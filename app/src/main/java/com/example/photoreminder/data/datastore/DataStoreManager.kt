package com.example.photoreminder.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

object DataStoreManager {

    /* ──────── CHIAVI ──────── */
    private val TOKEN_KEY     = stringPreferencesKey("auth_token")
    private val USERNAME_KEY  = stringPreferencesKey("username")   // << was: user_id

    /* ──────── TOKEN ──────── */
    suspend fun saveToken(context: Context, token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }
    suspend fun getToken(context: Context): String? =
        context.dataStore.data.first()[TOKEN_KEY]

    suspend fun clearToken(context: Context) {
        context.dataStore.edit { it.remove(TOKEN_KEY) }
    }

    /* ──────── USERNAME ──────── */
    suspend fun saveUsername(context: Context, username: String) {
        context.dataStore.edit { it[USERNAME_KEY] = username }
    }
    suspend fun getUsername(context: Context): String? =
        context.dataStore.data.first()[USERNAME_KEY]

    suspend fun clearUsername(context: Context) {
        context.dataStore.edit { it.remove(USERNAME_KEY) }
    }
}

/* Extension DataStore */
val Context.dataStore by preferencesDataStore(name = "my_datastore")
