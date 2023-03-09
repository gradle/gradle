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

import org.gradle.cache.FileLockManager
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.EncryptionAlgorithm
import org.gradle.util.internal.EncryptionAlgorithm.EncryptionException
import org.gradle.util.internal.SupportedEncryptionAlgorithm
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey


/**
 * Provides access to configuration parameters to control encryption.
 */
internal
interface EncryptionConfiguration {
    val isEncrypting: Boolean
    val encryptionKeyHashCode: HashCode
    val encryptionAlgorithm: EncryptionAlgorithm
}


/**
 * A service for encrypting/decrypting streams.
 */
internal
interface EncryptionService : EncryptionConfiguration {
    fun outputStream(stateType: StateType, output: () -> OutputStream): OutputStream
    fun inputStream(stateType: StateType, input: () -> InputStream): InputStream
}


@ServiceScope(Scopes.BuildTree::class)
internal
class DefaultEncryptionService(
    startParameter: ConfigurationCacheStartParameter,
    val fileLockManager: FileLockManager
) : EncryptionService {

    private
    val encryptingRequested: Boolean = startParameter.encryptionRequested

    private
    val secretKey: SecretKey? by lazy {
        val keySource = this.keySource
        if (keySource == null)
            null
        else {
            try {
                keySource.getKey(fileLockManager).also {
                    val keyLength = it.encoded.size
                    if (keyLength < 16) {
                        throw InvalidKeyException("Encryption key length is $keyLength bytes, but must be at least 16 bytes long")
                    }
                    // acquire a cipher just to validate the key
                    encryptionAlgorithm.newSession(it).encryptingCipher {}
                }
            } catch (e: EncryptionException) {
                if (e.message != null) {
                    throw e
                }
                throw EncryptionException("Error loading encryption key from ${keySource.sourceDescription}", e.cause)
            } catch (e: Exception) {
                throw EncryptionException("Error loading encryption key from ${keySource.sourceDescription}", e)
            }
        }
    }

    override
    val encryptionAlgorithm: EncryptionAlgorithm by lazy {
        SupportedEncryptionAlgorithm.byTransformation(startParameter.encryptionAlgorithm)
    }

    private
    val keySource: SecretKeySource? by lazy {
        if (encryptingRequested)
            sourceKeyFromKeyStore(startParameter)
        else
            null
    }

    override val isEncrypting: Boolean
        by lazy { encryptingRequested && secretKey != null }

    override val encryptionKeyHashCode: HashCode by lazy {
        secretKey?.let {
            Hashing.sha512().newHasher().apply {
                putBytes(it.encoded)
                putString(encryptionAlgorithm.transformation)
            }.hash()
        } ?: Hashing.newHasher().hash()
    }

    private
    fun shouldEncryptStreams(stateType: StateType) =
        isEncrypting && isStateEncryptable(stateType)

    private
    fun isStateEncryptable(stateType: StateType) = isEncrypting && stateType.encryptable

    override fun outputStream(stateType: StateType, output: () -> OutputStream): OutputStream =
        if (shouldEncryptStreams(stateType))
            encryptingOutputStream(output.invoke())
        else
            output.invoke()

    override fun inputStream(stateType: StateType, input: () -> InputStream): InputStream =
        if (shouldEncryptStreams(stateType))
            decryptingInputStream(input.invoke())
        else
            input.invoke()

    private
    fun decryptingInputStream(inputStream: InputStream): InputStream {
        val cipher = encryptionAlgorithm.newSession(secretKey).decryptingCipher(inputStream::read)
        return CipherInputStream(inputStream, cipher)
    }

    private
    fun encryptingOutputStream(outputStream: OutputStream): OutputStream {
        val cipher = encryptionAlgorithm.newSession(secretKey).encryptingCipher(outputStream::write)
        return CipherOutputStream(outputStream, cipher)
    }

    private
    fun sourceKeyFromKeyStore(startParameter: ConfigurationCacheStartParameter): SecretKeySource =
        KeyStoreKeySource.fromKeyStore(
            encryptionAlgorithm = encryptionAlgorithm.algorithm,
            keyStorePath = startParameter.keystorePath?.let { File(it) }
                ?: File(startParameter.gradleUserHomeDir, "gradle.keystore"),
            keyAlias = startParameter.keyAlias,
            keyStorePassword = startParameter.keystorePassword,
            keyPassword = startParameter.keyPassword
        )
}


interface SecretKeySource {
    fun getKey(fileLockManager: FileLockManager): SecretKey
    val sourceDescription: String
}


class KeyStoreKeySource(
    private val encryptionAlgorithm: String,
    private val keyStorePath: File,
    private val keyAlias: String,
    private val keyStorePassword: String?,
    keyPassword: String
) : SecretKeySource {

    private
    val keyProtection =
        KeyStore.PasswordProtection(keyPassword.toCharArray())

    override val sourceDescription: String
        get() = "Java keystore at $keyStorePath"

    override fun getKey(fileLockManager: FileLockManager): SecretKey {
        // JKS does not support non-PrivateKeys
        val ks = KeyStore.getInstance(KEYSTORE_TYPE)
        val key: SecretKey
        val alias = keyAlias
        fileLockManager.lock(keyStorePath, crossVersionExclusiveAccess(), "Gradle keystore").use {
            if (keyStorePath.isFile) {
                logger.info("Loading keystore from $keyStorePath")
                keyStorePath.inputStream().use { fis ->
                    ks.load(fis, keyStorePassword?.toCharArray())
                }
                val entry = ks.getEntry(alias, keyProtection) as KeyStore.SecretKeyEntry?
                if (entry != null) {
                    key = entry.secretKey
                    logger.debug("Retrieved key")
                } else {
                    logger.debug("No key found")
                    key = generateKey(ks, alias)
                    logger.warn("Key added to existing keystore at $keyStorePath")
                }
            } else {
                logger.debug("No keystore found")
                ks.load(null, null)
                key = generateKey(ks, alias)
                logger.debug("Key added to a new keystore at $keyStorePath")
            }
        }
        return key
    }

    private
    fun crossVersionExclusiveAccess(): LockOptionsBuilder =
        LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive).useCrossVersionImplementation()

    private
    fun generateKey(ks: KeyStore, alias: String): SecretKey {
        val newKey = KeyGenerator.getInstance(encryptionAlgorithm).generateKey()!!
        logger.info("Generated key")
        val entry = KeyStore.SecretKeyEntry(newKey)
        ks.setEntry(alias, entry, keyProtection)
        keyStorePath.outputStream().use { fos ->
            ks.store(fos, keyStorePassword?.toCharArray())
        }
        return newKey
    }

    companion object {
        const val KEYSTORE_TYPE = "pkcs12"
        fun fromKeyStore(
            encryptionAlgorithm: String,
            keyStorePath: File,
            keyAlias: String,
            keyStorePassword: String?,
            keyPassword: String
        ): SecretKeySource =
            KeyStoreKeySource(encryptionAlgorithm, keyStorePath, keyAlias, keyStorePassword, keyPassword)
    }
}
