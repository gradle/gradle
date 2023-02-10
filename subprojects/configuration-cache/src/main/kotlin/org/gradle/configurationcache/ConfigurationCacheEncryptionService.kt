/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.configurationcache

import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.KeyStore.PasswordProtection
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec


internal
interface ConfigurationCacheEncryptionService {
    fun outputStream(stateFile: ConfigurationCacheStateFile): OutputStream
    fun inputStream(stateFile: ConfigurationCacheStateFile): InputStream
    val isEncrypting: Boolean
    val encryptionKeyHashCode: HashCode
}


@ServiceScope(Scopes.BuildTree::class)
internal
class SimpleEncryptionService(startParameter: ConfigurationCacheStartParameter) : ConfigurationCacheEncryptionService {
    private
    val keyStorePath: File = startParameter.keystorePath
        .let { File(it) }

    private
    val keyStorePassword: String? = startParameter.keystorePassword

    private
    val keyPassword: String = startParameter.keyPassword

    private
    val keyProtection =
        keyPassword
            .let { PasswordProtection(it.toCharArray()) }

    private
    val encryptingRequested: Boolean = startParameter.encryptedCache

    private
    val secretKey: SecretKey? by lazy { if (encryptingRequested) computeSecretKey() else null }

    private
    val shouldEncrypt: Boolean by lazy { encryptingRequested && secretKey != null }

    private
    fun computeSecretKey(): SecretKey? {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        val key: SecretKey
        val alias = "gradle-secret"
        logger.info("Configuration cache encryption is enabled? $encryptingRequested")
        if (keyStorePath.isFile) {
            logger.info("Loading keystore from $keyStorePath")
            keyStorePath.inputStream().use { fis ->
                try {
                    ks.load(fis, keyStorePassword?.toCharArray())
                } catch (e: Exception) {
                    // could not load, store password is wrong
                    logger.error("Keystore exists, but cannot be loaded, encryption not enabled", e)
                    return@computeSecretKey null
                }
            }
            val entry = ks.getEntry(alias, keyProtection) as KeyStore.SecretKeyEntry?
            if (entry != null) {
                key = entry.secretKey
                logger.info("Retrieved key: ${key.asString()}")
            } else {
                logger.info("No key found")
                key = generateKey(ks, alias)
                logger.info("Key created")
            }
        } else {
            logger.info("No keystore found")
            ks.load(null, null)
            key = generateKey(ks, alias)
            logger.info("Keystore created")
        }
        return key
    }

    private
    fun generateKey(ks: KeyStore, alias: String): SecretKey {
        val newKey = KeyGenerator.getInstance("AES").generateKey()!!
        logger.info("Generated key: ${newKey.asString()}")
        val entry = KeyStore.SecretKeyEntry(newKey)
        ks.setEntry(alias, entry, keyProtection)
        keyStorePath.outputStream().use { fos -> ks.store(fos, keyStorePassword?.toCharArray()) }
        return newKey
    }

    override val isEncrypting: Boolean
        get() = shouldEncrypt

    override val encryptionKeyHashCode: HashCode = secretKey?.let {
        Hashing.sha512().newHasher().apply {
            putBytes(it.encoded)
            putString(it.format)
            putString(it.algorithm)
        }.hash()
    } ?: Hashing.newHasher().hash()

    override fun outputStream(stateFile: ConfigurationCacheStateFile): OutputStream =
        if (shouldEncrypt && stateFile.stateType.encryptable)
            encryptingOutputStream(stateFile.outputStream())
        else
            stateFile.outputStream()

    override fun inputStream(stateFile: ConfigurationCacheStateFile): InputStream =
        if (shouldEncrypt && stateFile.stateType.encryptable)
            decryptingInputStream(stateFile.inputStream())
        else
            stateFile.inputStream()

    private
    fun decryptingInputStream(inputStream: InputStream): InputStream {
        val cipher = cipher()
        val fileIv = ByteArray(16)
        inputStream.read(fileIv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(fileIv))
        return CipherInputStream(inputStream, cipher)
    }

    private
    fun encryptingOutputStream(outputStream: OutputStream): OutputStream {
        val cipher = cipher()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        outputStream.write(iv)
        return CipherOutputStream(outputStream, cipher)
    }

    fun cipher() = Cipher.getInstance("AES/CBC/PKCS5Padding")
}


private
fun SecretKey.asString(): String =
    String(Base64.getEncoder().encode(encoded)) + " $algorithm/$format"


