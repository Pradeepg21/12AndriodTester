package com.satya.a12andriodtester.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Live query results; errors (e.g. permission denied after sign-out) end in an empty list. */
fun Query.live(): Flow<QuerySnapshot?> = callbackFlow {
    val reg = addSnapshotListener { snap, err ->
        trySend(if (err != null) null else snap)
    }
    awaitClose { reg.remove() }
}

fun DocumentReference.live(): Flow<DocumentSnapshot?> = callbackFlow {
    val reg = addSnapshotListener { snap, err ->
        trySend(if (err != null) null else snap)
    }
    awaitClose { reg.remove() }
}

/** Must match REGION in functions/src/index.ts. */
const val FUNCTIONS_REGION = "asia-south1"
