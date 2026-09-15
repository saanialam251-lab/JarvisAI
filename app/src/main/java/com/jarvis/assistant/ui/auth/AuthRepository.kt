package com.jarvis.assistant.ui.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All three login options from your request:
 *   1) Google sign-in   2) Email + password   3) Phone number (OTP)
 * Firebase is OPTIONAL at build time: if google-services.json is absent
 * the app still compiles/runs and auth reports "not configured".
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    sealed interface AuthResult {
        data class Ok(val uid: String) : AuthResult
        data class CodeSent(val verificationId: String) : AuthResult
        data class Error(val message: String) : AuthResult
    }

    private val auth: FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    private fun notConfigured() = AuthResult.Error(
        "Firebase not configured. Place google-services.json in app/ (see README). " +
        "You can also skip login — the app works without it.")

    val currentUser get() = auth?.currentUser

    fun googleSignInIntent(): Intent? {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("YOUR_WEB_CLIENT_ID") // from Firebase console → replace, or it auto-fills via google-services
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    suspend fun firebaseAuthWithGoogle(idToken: String?): AuthResult {
        if (auth == null) return notConfigured()
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
            AuthResult.Ok(auth.currentUser?.uid ?: "ok")
        } catch (e: Exception) { AuthResult.Error(e.message ?: "Google sign-in failed") }
    }

    suspend fun signInEmail(email: String, password: String): AuthResult {
        if (auth == null) return notConfigured()
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            AuthResult.Ok(auth.currentUser?.uid ?: "ok")
        } catch (e: Exception) { AuthResult.Error(e.message ?: "Sign-in failed") }
    }

    suspend fun createEmail(email: String, password: String): AuthResult {
        if (auth == null) return notConfigured()
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            AuthResult.Ok(auth.currentUser?.uid ?: "ok")
        } catch (e: Exception) { AuthResult.Error(e.message ?: "Sign-up failed") }
    }

    fun sendPhoneOtp(phoneNumber: String): Flow<AuthResult> = callbackFlow {
        if (auth == null) { trySend(notConfigured()); close(); return@callbackFlow }
        val cb = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(cred: PhoneAuthCredential) {
                // auto-retrieval on some devices
                auth.signInWithCredential(cred).addOnCompleteListener {
                    trySend(if (it.isSuccessful) AuthResult.Ok(auth.currentUser?.uid ?: "ok")
                            else AuthResult.Error(it.exception?.message ?: "OTP failed"))
                }
            }
            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                trySend(AuthResult.Error(e.message ?: "Verification failed"))
            }
            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                trySend(AuthResult.CodeSent(verificationId))
            }
        }
        val opts = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setCallbacks(cb)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(opts)
        awaitClose { }
    }

    suspend fun confirmOtp(verificationId: String, code: String): AuthResult {
        if (auth == null) return notConfigured()
        return try {
            val cred = PhoneAuthProvider.getCredential(verificationId, code)
            auth.signInWithCredential(cred).await()
            AuthResult.Ok(auth.currentUser?.uid ?: "ok")
        } catch (e: Exception) { AuthResult.Error(e.message ?: "Wrong code") }
    }

    fun signOut() { auth?.signOut() }
}
