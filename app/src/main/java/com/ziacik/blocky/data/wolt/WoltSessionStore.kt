package com.ziacik.blocky.data.wolt

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class WoltSessionStore(context: Context) {
	private val preferences = context.applicationContext
		.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

	fun saveCookies(cookies: String) {
		preferences.edit().putString(KEY_COOKIES, encrypt(cookies)).apply()
	}

	fun cookies(): String? = preferences.getString(KEY_COOKIES, null)
		?.let { encrypted -> runCatching { decrypt(encrypted) }.getOrNull() }

	fun clearCookies() {
		preferences.edit()
			.remove(KEY_COOKIES)
			.remove(KEY_LAST_SYNC)
			.apply()
	}

	fun isConnected(): Boolean = cookies()?.contains("__wtoken=") == true

	fun lastSuccessfulSyncMillis(): Long? =
		preferences.takeIf { it.contains(KEY_LAST_SYNC) }?.getLong(KEY_LAST_SYNC, 0L)

	fun markSuccessfulSync(timestampMillis: Long) {
		preferences.edit().putLong(KEY_LAST_SYNC, timestampMillis).apply()
	}

	private fun encrypt(value: String): String {
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.ENCRYPT_MODE, secretKey())
		val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
		val encrypted = Base64.encodeToString(
			cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
			Base64.NO_WRAP,
		)
		return "$iv:$encrypted"
	}

	private fun decrypt(value: String): String {
		val separator = value.indexOf(':')
		require(separator > 0) { "Invalid encrypted Wolt session." }
		val iv = Base64.decode(value.substring(0, separator), Base64.NO_WRAP)
		val encrypted = Base64.decode(value.substring(separator + 1), Base64.NO_WRAP)
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
		return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
	}

	private fun secretKey(): SecretKey {
		val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
		(keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

		val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
		generator.init(
			KeyGenParameterSpec.Builder(
				KEY_ALIAS,
				KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
			)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.build()
		)
		return generator.generateKey()
	}

	private companion object {
		const val PREFERENCES = "wolt_session"
		const val KEY_COOKIES = "cookies"
		const val KEY_LAST_SYNC = "last_sync"
		const val KEY_ALIAS = "blocky_wolt_session"
		const val TRANSFORMATION = "AES/GCM/NoPadding"
	}
}
