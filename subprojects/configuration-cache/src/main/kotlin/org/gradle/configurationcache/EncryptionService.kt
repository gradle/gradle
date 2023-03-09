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
    val encryptingRequested: Boolean = startParameter.encryptionRequested

    private
    val secretKey: SecretKey? by lazy {
        val keySource = this.keySource
        if (keySource == null)
            null
        else {
            try {
                keySource.getKey(cacheBuilderFactory).also {
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
            keyStorePath = startParameter.keystoreDir?.let { File(it) },
        )
}


interface SecretKeySource {
    fun getKey(cacheBuilderFactory: GlobalScopedCacheBuilderFactory): SecretKey
    val sourceDescription: String
}


class KeyStoreKeySource(
    private val encryptionAlgorithm: String,
    private val customKeyStoreDir: File?,
    private val keyAlias: String,
) : SecretKeySource {

    private
    val keyProtection = KeyStore.PasswordProtection(CharArray(0))

    override val sourceDescription: String
        get() = customKeyStoreDir?.let { "custom Java keystore at $it" }
            ?: "default Gradle keystore"

    override fun getKey(cacheBuilderFactory: GlobalScopedCacheBuilderFactory): SecretKey {
        // JKS does not support non-PrivateKeys
        val ks = KeyStore.getInstance(KEYSTORE_TYPE)
        return cacheBuilderFactory
            .run { customKeyStoreDir?.let { createCacheBuilderFactory(it) } ?: this }
            .createCacheBuilder("keystore")
            .withDisplayName("Gradle keystore")
            .withLockOptions(exclusiveLockMode())
            .open().use {
                secretKey(File(it.baseDir, "gradle.keystore"), ks)
            }
    }

    private
    fun secretKey(keyStorePath: File, ks: KeyStore) = when {
        keyStorePath.isFile -> loadSecretKeyFromExistingKeystore(keyStorePath, ks)
        else -> createKeyStoreAndGenerateKey(keyStorePath, ks)
    }

    private
    fun loadSecretKeyFromExistingKeystore(keyStorePath: File, ks: KeyStore): SecretKey {
        logger.info("Loading keystore from {}", keyStorePath)
        keyStorePath.inputStream().use { fis ->
            ks.load(fis, CharArray(0))
        }
        val entry = ks.getEntry(keyAlias, keyProtection) as KeyStore.SecretKeyEntry?
        if (entry != null) {
            return entry.secretKey.also {
                logger.debug("Retrieved key")
            }
        }
        logger.debug("No key found")
        return generateKey(keyStorePath, ks, keyAlias).also {
            logger.warn("Key added to existing keystore at {}", keyStorePath)
        }
    }

    private
    fun createKeyStoreAndGenerateKey(keyStorePath: File, ks: KeyStore): SecretKey {
        logger.debug("No keystore found")
        ks.load(null, CharArray(0))
        return generateKey(keyStorePath, ks, keyAlias).also {
            logger.debug("Key added to a new keystore at {}", keyStorePath)
        }
    }

    private
    fun exclusiveLockMode(): LockOptionsBuilder =
        LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive)

    private
    fun generateKey(keyStorePath: File, ks: KeyStore, alias: String): SecretKey {
        val newKey = KeyGenerator.getInstance(encryptionAlgorithm).generateKey()!!
        logger.info("Generated key")
        val entry = KeyStore.SecretKeyEntry(newKey)
        ks.setEntry(alias, entry, keyProtection)
        keyStorePath.outputStream().use { fos ->
            ks.store(fos, CharArray(0))
        }
        return newKey
    }

    companion object {
        const val KEYSTORE_TYPE = "pkcs12"

        fun fromKeyStore(
            encryptionAlgorithm: String,
            keyStorePath: File?,
        ): SecretKeySource = KeyStoreKeySource(
            encryptionAlgorithm,
            keyStorePath,
            "gradle-secret"
        )
    }
}
