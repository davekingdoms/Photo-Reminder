package com.example.photoreminder.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


object DataStoreManager {

    private val TOKEN_KEY = stringPreferencesKey("auth_token")

    // 1) Estensione per creare DataStore
    //    "val Context.dataStore by preferencesDataStore(name = "my_datastore")"
    //    Non possiamo inserirla direttamente qui (in un object), va fuori da un object/classe.
    //    Quindi la definiamo sotto.

    /**
     * Salva il token in DataStore in modo asincrono.
     */
    suspend fun saveToken(context: Context, token: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
        }
    }

    /**
     * Restituisce il token, oppure null se non presente.
     */
    suspend fun getToken(context: Context): String? {
        // dataStore.data è un Flow<Preferences>
        // con "first()" otteniamo il primo (e unico) valore
        // (in un'app reale, potresti preferire un Flow continuo)
        val prefs = context.dataStore.data.first()
        return prefs[TOKEN_KEY]
    }

    /**
     * Rimuove il token (logout).
     */
    suspend fun clearToken(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
        }
    }
}

val Context.dataStore by preferencesDataStore(name = "my_datastore")
