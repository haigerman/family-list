package io.github.haigerman.familylist.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.haigerman.familylist.cloud.OpenCloudClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Config(
    /** Der eigene Name – er steht an Einträgen und ist der Tag, über den Tasks zugeordnet werden. */
    val name: String = "",
    /** Farbe des eigenen Tags als 0xRRGGBB. */
    val color: Int = TAG_COLORS[0],
    /** Kennung dieses Geräts in der Familienliste. */
    val memberId: String = "",
    val serverUrl: String = "",
    val user: String = "",
    val token: String = "",
    val spaceId: String = "",
    val spaceName: String = "",
    /** WebDAV-Adresse des Space, in dem die Listen liegen. */
    val spaceUrl: String = "",
) {
    /** Zugangsdaten vollständig? */
    val hasLogin: Boolean
        get() = OpenCloudClient.parseServerUrl(serverUrl) != null && user.isNotBlank() && token.isNotBlank()

    /** Alles da, um mit dem Space abzugleichen? */
    val complete: Boolean
        get() = hasLogin && spaceUrl.isNotBlank()
}

/** Einstellungen der App. Der App-Token wird mit einem Schlüssel aus dem Android-Keystore verschlüsselt abgelegt. */
object Settings {
    private lateinit var prefs: SharedPreferences
    private val _config = MutableStateFlow(Config())
    val config: StateFlow<Config> = _config

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val memberId = prefs.getString("memberId", null) ?: newId().also { prefs.edit().putString("memberId", it).apply() }
        _config.value = Config(
            name = prefs.getString("name", "") ?: "",
            color = prefs.getInt("color", TAG_COLORS[0]),
            memberId = memberId,
            serverUrl = prefs.getString("serverUrl", "") ?: "",
            user = prefs.getString("user", "") ?: "",
            token = prefs.getString("token", null)?.let(Secrets::decrypt) ?: "",
            spaceId = prefs.getString("spaceId", "") ?: "",
            spaceName = prefs.getString("spaceName", "") ?: "",
            spaceUrl = prefs.getString("spaceUrl", "") ?: "",
        )
    }

    fun update(config: Config) {
        val old = _config.value
        if (config == old) return
        _config.value = config
        prefs.edit().apply {
            putString("name", config.name)
            putInt("color", config.color)
            putString("serverUrl", config.serverUrl)
            putString("user", config.user)
            putString("spaceId", config.spaceId)
            putString("spaceName", config.spaceName)
            putString("spaceUrl", config.spaceUrl)
            if (config.token != old.token) {
                if (config.token.isEmpty()) remove("token") else putString("token", Secrets.encrypt(config.token))
            }
        }.apply()
    }
}

private object Secrets {
    private const val ALIAS = "family_list_token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + data, Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }

    /** null, wenn sich der Token nicht mehr entschlüsseln lässt (z. B. nach einer Neuinstallation). */
    fun decrypt(stored: String): String? = try {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        val ivLength = raw[0].toInt()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 1, ivLength))
        String(cipher.doFinal(raw, 1 + ivLength, raw.size - 1 - ivLength), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}
