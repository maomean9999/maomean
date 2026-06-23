package com.example.api

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String,
    val providerId: String = "google.com",
    val joinDate: Long = System.currentTimeMillis()
)

data class SyncProjectItem(
    val id: String,
    val videoName: String,
    val subtitleCount: Int,
    val originalLanguage: String = "Khmer (ភាសាខ្មែរ)",
    val targetLanguage: String,
    val syncedAt: Long,
    val subtitleContent: String
)

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    private var isInitialized = false
    private var isMockMode = false

    private var authInstance: FirebaseAuth? = null
    private var firestoreInstance: FirebaseFirestore? = null

    // For Mock state fallback if google-services.json is absent/incorrect
    private var mockCurrentUser: UserProfile? = null
    private val mockProjects = mutableListOf<SyncProjectItem>()

    fun init(context: Context) {
        if (isInitialized) return
        try {
            // Attempt to initialize real FirebaseApp
            FirebaseApp.initializeApp(context)
            authInstance = FirebaseAuth.getInstance()
            firestoreInstance = FirebaseFirestore.getInstance()
            isInitialized = true
            isMockMode = false
            Log.d(TAG, "Firebase initialized successfully in LIVE mode")
            
            // Query initial check
            if (authInstance?.currentUser != null) {
                Log.d(TAG, "Current Firebase user found: ${authInstance?.currentUser?.email}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed - falling back to sandbox mode: ${e.localizedMessage}")
            isMockMode = true
            isInitialized = true
            
            // Seed mock translation history projects for sandbox simulation
            mockProjects.add(
                SyncProjectItem(
                    id = "cloud_proj_1",
                    videoName = "Khmer Romantic Movie Clip.mp4",
                    subtitleCount = 7,
                    targetLanguage = "English (US)",
                    syncedAt = System.currentTimeMillis() - 86400000, // 1 day ago
                    subtitleContent = "Compiled subtitle file data saved successfully."
                )
            )
            mockProjects.add(
                SyncProjectItem(
                    id = "cloud_proj_2",
                    videoName = "Angkor Wat Documentary.mp4",
                    subtitleCount = 4,
                    targetLanguage = "Hindi (India)",
                    syncedAt = System.currentTimeMillis() - 43200000, // 12 hours ago
                    subtitleContent = "Sample temple doc data compiled correctly."
                )
            )
        }
    }

    fun isUsingSandbox(): Boolean = isMockMode

    fun getLiveAuth(): FirebaseAuth? = authInstance
    fun getLiveFirestore(): FirebaseFirestore? = firestoreInstance

    // Auth actions: Simulated google sign-in/out
    fun getCurrentUser(): UserProfile? {
        if (isMockMode) {
            return mockCurrentUser
        }
        val liveUser = authInstance?.currentUser
        return if (liveUser != null) {
            UserProfile(
                uid = liveUser.uid,
                displayName = liveUser.displayName ?: liveUser.email?.substringBefore("@") ?: "Firebase Partner",
                email = liveUser.email ?: "partner@firebase.google.com",
                photoUrl = liveUser.photoUrl?.toString() ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&h=100&fit=crop",
                providerId = "google.com"
            )
        } else {
            null
        }
    }

    suspend fun signInWithGoogleSimulated(email: String, name: String, photo: String): UserProfile {
        if (isMockMode) {
            mockCurrentUser = UserProfile(
                uid = "mock_google_id_${System.currentTimeMillis()}",
                displayName = name,
                email = email,
                photoUrl = photo
            )
            return mockCurrentUser!!
        }

        // Live Mode (try signing in/creating email as fallback if google token isn't present)
        try {
            val result = authInstance?.signInWithEmailAndPassword(email, "firebaseSafePassword123")?.await()
            val user = result?.user
            if (user != null) {
                return UserProfile(user.uid, user.displayName ?: name, user.email ?: email, photo)
            }
        } catch (e: Exception) {
            // Try creating the account
            try {
                val result = authInstance?.createUserWithEmailAndPassword(email, "firebaseSafePassword123")?.await()
                val user = result?.user
                if (user != null) {
                    return UserProfile(user.uid, name, user.email ?: email, photo)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error in live simulation sign-in: ${ex.localizedMessage}")
            }
        }

        // Default local user state if live call has network issue
        mockCurrentUser = UserProfile(
            uid = "fallback_id_${System.currentTimeMillis()}",
            displayName = name,
            email = email,
            photoUrl = photo
        )
        return mockCurrentUser!!
    }

    fun signOut() {
        if (isMockMode) {
            mockCurrentUser = null
        } else {
            authInstance?.signOut()
        }
    }

    // Firestore CRUD Operations
    suspend fun saveDubbingProject(
        videoName: String,
        subtitleCount: Int,
        targetLanguage: String,
        subtitleContent: String
    ): SyncProjectItem {
        val newItem = SyncProjectItem(
            id = "proj_${System.currentTimeMillis()}",
            videoName = videoName,
            subtitleCount = subtitleCount,
            targetLanguage = targetLanguage,
            syncedAt = System.currentTimeMillis(),
            subtitleContent = subtitleContent
        )

        if (isMockMode) {
            mockProjects.add(0, newItem)
            return newItem
        }

        // Real Firestore Persistence
        val liveUser = authInstance?.currentUser
        if (liveUser != null) {
            try {
                val data = mapOf(
                    "id" to newItem.id,
                    "videoName" to newItem.videoName,
                    "subtitleCount" to newItem.subtitleCount,
                    "originalLanguage" to newItem.originalLanguage,
                    "targetLanguage" to newItem.targetLanguage,
                    "syncedAt" to newItem.syncedAt,
                    "subtitleContent" to newItem.subtitleContent
                )
                firestoreInstance?.collection("users")
                    ?.document(liveUser.uid)
                    ?.collection("projects")
                    ?.document(newItem.id)
                    ?.set(data)
                    ?.await()
                Log.d(TAG, "Project saved to Firestore for user: ${liveUser.uid}")
            } catch (e: Exception) {
                Log.e(TAG, "Firestore save failed, adding to local cache: ${e.localizedMessage}")
            }
        }

        // Always also add locally for safe reactive fallback UI
        mockProjects.add(0, newItem)
        return newItem
    }

    suspend fun getDubbingProjects(): List<SyncProjectItem> {
        if (isMockMode) {
            return mockProjects.sortedByDescending { it.syncedAt }
        }

        val liveUser = authInstance?.currentUser
        if (liveUser != null) {
            try {
                val snapshot = firestoreInstance?.collection("users")
                    ?.document(liveUser.uid)
                    ?.collection("projects")
                    ?.orderBy("syncedAt", Query.Direction.DESCENDING)
                    ?.get()
                    ?.await()

                if (snapshot != null && !snapshot.isEmpty) {
                    val dbList = snapshot.documents.mapNotNull { doc ->
                        try {
                            SyncProjectItem(
                                id = doc.getString("id") ?: doc.id,
                                videoName = doc.getString("videoName") ?: "Unnamed Video",
                                subtitleCount = doc.getLong("subtitleCount")?.toInt() ?: 0,
                                originalLanguage = doc.getString("originalLanguage") ?: "Khmer",
                                targetLanguage = doc.getString("targetLanguage") ?: "English",
                                syncedAt = doc.getLong("syncedAt") ?: System.currentTimeMillis(),
                                subtitleContent = doc.getString("subtitleContent") ?: ""
                            )
                        } catch (ex: Exception) {
                            null
                        }
                    }
                    if (dbList.isNotEmpty()) {
                        // Merge or clear local mock and use exact elements
                        mockProjects.clear()
                        mockProjects.addAll(dbList)
                        return dbList
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore projects fetch failed: ${e.localizedMessage}")
            }
        }

        return mockProjects.sortedByDescending { it.syncedAt }
    }

    suspend fun deleteDubbingProject(projectId: String): Boolean {
        if (isMockMode) {
            mockProjects.removeAll { it.id == projectId }
            return true
        }

        val liveUser = authInstance?.currentUser
        if (liveUser != null) {
            try {
                firestoreInstance?.collection("users")
                    ?.document(liveUser.uid)
                    ?.collection("projects")
                    ?.document(projectId)
                    ?.delete()
                    ?.await()
                Log.d(TAG, "Project deleted from Firestore: $projectId")
            } catch (e: Exception) {
                Log.e(TAG, "Firestore project delete failed: ${e.localizedMessage}")
                return false
            }
        }

        mockProjects.removeAll { it.id == projectId }
        return true
    }
}
