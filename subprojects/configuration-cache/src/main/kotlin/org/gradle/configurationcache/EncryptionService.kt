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
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
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
    cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
) : EncryptionService {

    private
    val secretKey: SecretKey? by lazy {
        when {
            // encryption is on by default
            startParameter.encryptionRequested -> {
                secretKeySourceFrom(startParameter).let { keySource ->
                    try {
                        secretKeyFrom(keySource, cacheBuilderFactory).also { key ->
                            val keyLength = key.encoded.size
                            if (keyLength < 16) {
                                throw InvalidKeyException("Encryption key length is $keyLength bytes, but must be at least 16 bytes long")
                            }
                            // acquire a cipher just to validate the key
                            encryptionAlgorithm.newSession(key).encryptingCipher {}
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

            else -> {
                null
            }
        }
    }

    private
    fun secretKeyFrom(keySource: KeyStoreKeySource, cacheBuilderFactory: GlobalScopedCacheBuilderFactory) =
        // JKS does not support non-PrivateKeys
        cacheBuilderFactory
            .run { keySource.customKeyStoreDir?.let { createCacheBuilderFactory(it) } ?: this }
            .createCacheBuilder("keystore")
            .withDisplayName("Gradle keystore")
            .withLockOptions(exclusiveLockMode())
            .open().use { cache ->
                val keyStoreFile = File(cache.baseDir, "gradle.keystore")
                keySource.loadOrCreateKey(keyStoreFile)
            }

    private
    fun exclusiveLockMode(): LockOptionsBuilder =
        LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive)

    override
    val encryptionAlgorithm: EncryptionAlgorithm by lazy {
        SupportedEncryptionAlgorithm.byTransformation(startParameter.encryptionAlgorithm)
    }

    override val isEncrypting: Boolean
        get() = secretKey != null

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
        isEncrypting && stateType.encryptable

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
    fun secretKeySourceFrom(startParameter: ConfigurationCacheStartParameter) =
        KeyStoreKeySource(
            encryptionAlgorithm = encryptionAlgorithm.algorithm,
            customKeyStoreDir = startParameter.keystoreDir?.let { File(it) },
            keyAlias = "gradle-secret"
        )
}


class KeyStoreKeySource(
    val encryptionAlgorithm: String,
    val customKeyStoreDir: File?,
    val keyAlias: String,
) {

    private
    val keyProtection = KeyStore.PasswordProtection(CharArray(0))

    val sourceDescription: String
        get() = customKeyStoreDir?.let { "custom Java keystore at $it" }
            ?: "default Gradle keystore"

    fun loadOrCreateKey(keyStoreFile: File) =
        secretKey(keyStoreFile, KeyStore.getInstance(KEYSTORE_TYPE))

    private
    fun secretKey(keyStoreFile: File, ks: KeyStore) = when {
        keyStoreFile.isFile -> loadSecretKeyFromExistingKeystore(keyStoreFile, ks)
        else -> createKeyStoreAndGenerateKey(keyStoreFile, ks)
    }

    private
    fun loadSecretKeyFromExistingKeystore(keyStoreFile: File, ks: KeyStore): SecretKey {
        logger.info("Loading keystore from {}", keyStoreFile)
        keyStoreFile.inputStream().use { fis ->
            ks.load(fis, null)
        }
        val entry = ks.getEntry(keyAlias, keyProtection) as KeyStore.SecretKeyEntry?
        if (entry != null) {
            return entry.secretKey.also {
                logger.debug("Retrieved key")
            }
        }
        logger.debug("No key found")
        return generateKey(keyStoreFile, ks, keyAlias).also {
            logger.warn("Key added to existing keystore at {}", keyStoreFile)
        }
    }

    private
    fun createKeyStoreAndGenerateKey(keyStoreFile: File, ks: KeyStore): SecretKey {
        logger.debug("No keystore found")
        ks.load(null, null)
        return generateKey(keyStoreFile, ks, keyAlias).also {
            logger.debug("Key added to a new keystore at {}", keyStoreFile)
        }
    }

    private
    fun generateKey(keyStoreFile: File, ks: KeyStore, alias: String): SecretKey {
        val newKey = KeyGenerator.getInstance(encryptionAlgorithm).generateKey()!!
        logger.info("Generated key")
        val entry = KeyStore.SecretKeyEntry(newKey)
        ks.setEntry(alias, entry, keyProtection)
        keyStoreFile.outputStream().use { fos ->
            ks.store(fos, null)
        }
        return newKey
    }

    companion object {
        const val KEYSTORE_TYPE = "pkcs12"
    }
}
