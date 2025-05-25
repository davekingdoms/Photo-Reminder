package com.example.photoreminder.data.local

enum class SyncStatus {
    LOCAL_ONLY,   // create offline
    DIRTY,        // modified offline
    PENDING_DELETE, // delete offline
    SYNCED        // synced with server
}
