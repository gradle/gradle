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

import org.gradle.cache.CacheBuilder
import org.gradle.cache.PersistentCache
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.extensions.stdlib.useToRun
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.EncryptionAlgorithm
import org.gradle.util.internal.EncryptionAlgorithm.EncryptionException
import org.gradle.util.internal.SupportedEncryptionAlgorithm
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.InvalidKeyException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec


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


@ServiceScope(Scope.BuildTree::class)
internal
class DefaultEncryptionService(
    private val startParameter: ConfigurationCacheStartParameter,
    private val cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
) : EncryptionService {

    private
    val secretKey: SecretKey? by lazy {
        produceSecretKey(EncryptionKind.select(startParameter.encryptionRequested))
    }

    private
    fun produceSecretKey(encryptionKind: EncryptionKind) =
        secretKeySource(encryptionKind).let { keySource ->
            try {
                secretKeyFrom(keySource)?.also { key ->
                    assertKeyLength(key)
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

    private
    fun secretKeyFrom(keySource: SecretKeySource) =
        keySource.getKey()


    private
    fun assertKeyLength(key: SecretKey) {
        val keyLength = key.encoded.size
        if (keyLength < 16) {
            throw InvalidKeyException("Encryption key length is $keyLength bytes, but must be at least 16 bytes long")
        }
    }

    override val encryptionAlgorithm: EncryptionAlgorithm by lazy {
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
            safeWrap(output, this::encryptingOutputStream)
        else
            output.invoke()

    override fun inputStream(stateType: StateType, input: () -> InputStream): InputStream =
        if (shouldEncryptStreams(stateType))
            safeWrap(input, this::decryptingInputStream)
        else
            input.invoke()

    /**
     * Wraps an inner closeable into an outer closeable, while ensuring that
     * if the wrapper function fails, the inner closeable is closed before
     * the exception is thrown.
     *
     * @param innerSupplier the supplier that produces the inner closeable that is to be safely wrapped
     * @param unsafeWrapper a wrapping function that is potentially unsafe
     * @return the result of the wrapper function
     */
    private
    fun <C : Closeable, T : Closeable> safeWrap(innerSupplier: () -> C, unsafeWrapper: (C) -> T): T {
        // fine if we fail here
        val innerCloseable = innerSupplier()
        val outerCloseable = try {
            // but if we fail here, we need to ensure we close
            // the inner closeable, or else it will leak
            unsafeWrapper(innerCloseable)
        } catch (e: Throwable) {
            try {
                innerCloseable.close()
            } catch (closingException: Throwable) {
                e.addSuppressed(closingException)
            }
            throw e
        }
        return outerCloseable
    }

    private
    fun decryptingInputStream(inputStream: InputStream): InputStream {
        val cipher = newEncryptionSession().decryptingCipher(inputStream::read)
        return CipherInputStream(inputStream, cipher)
    }

    private
    fun encryptingOutputStream(outputStream: OutputStream): OutputStream {
        val cipher = newEncryptionSession().encryptingCipher(outputStream::write)
        return CipherOutputStream(outputStream, cipher)
    }

    private
    fun newEncryptionSession(): EncryptionAlgorithm.Session =
        encryptionAlgorithm.newSession(secretKey)

    private
    fun secretKeySource(kind: EncryptionKind): SecretKeySource =
        when (kind) {
            EncryptionKind.KEYSTORE ->
                KeyStoreKeySource(
                    encryptionAlgorithm = encryptionAlgorithm.algorithm,
                    customKeyStoreDir = startParameter.keystoreDir?.let { File(it) },
                    keyAlias = "gradle-secret",
                    cacheBuilderFactory = cacheBuilderFactory
                )

            EncryptionKind.ENV_VAR ->
                EnvironmentVarKeySource(
                    encryptionAlgorithm = encryptionAlgorithm.algorithm
                )

            EncryptionKind.NONE ->
                NoEncryptionKeySource()
        }
}


interface SecretKeySource {
    fun getKey(): SecretKey?
    val sourceDescription: String
}


private
class KeyStoreKeySource(
    val encryptionAlgorithm: String,
    val customKeyStoreDir: File?,
    val keyAlias: String,
    val cacheBuilderFactory: GlobalScopedCacheBuilderFactory
) : SecretKeySource {

    private
    val keyProtection = KeyStore.PasswordProtection(CharArray(0))

    private
    val keyStore by lazy {
        KeyStore.getInstance(KEYSTORE_TYPE)
    }

    override val sourceDescription: String
        get() = customKeyStoreDir?.let { "custom Java keystore at $it" }
            ?: "default Gradle configuration cache keystore"

    private
    fun createKeyStoreAndGenerateKey(keyStoreFile: File): SecretKey {
        logger.debug("No keystore found")
        keyStore.load(null, KEYSTORE_PASSWORD)
        return generateKey(keyStoreFile, keyAlias).also {
            logger.debug("Key added to a new keystore at {}", keyStoreFile)
        }
    }

    private
    fun loadSecretKeyFromExistingKeystore(keyStoreFile: File): SecretKey {
        logger.debug("Loading keystore from {}", keyStoreFile)
        keyStoreFile.inputStream().use { fis ->
            keyStore.load(fis, KEYSTORE_PASSWORD)
        }
        val entry = keyStore.getEntry(keyAlias, keyProtection) as KeyStore.SecretKeyEntry?
        if (entry != null) {
            return entry.secretKey.also {
                logger.debug("Retrieved key")
            }
        }
        logger.debug("No key found")
        return generateKey(keyStoreFile, keyAlias).also {
            logger.warn("Key added to existing keystore at {}", keyStoreFile)
        }
    }

    private
    fun generateKey(keyStoreFile: File, alias: String): SecretKey {
        val newKey = KeyGenerator.getInstance(encryptionAlgorithm).generateKey()
        require(newKey != null) {
            "Failed to generate encryption key using $encryptionAlgorithm."
        }
        logger.debug("Generated key")
        val entry = KeyStore.SecretKeyEntry(newKey)
        keyStore.setEntry(alias, entry, keyProtection)
        keyStoreFile.outputStream().use { fos ->
            keyStore.store(fos, KEYSTORE_PASSWORD)
        }
        return newKey
    }

    private
    fun cacheBuilderFor(): CacheBuilder =
        cacheBuilderFactory
            .run { customKeyStoreDir?.let { createCacheBuilderFactory(it) } ?: this }
            .createCacheBuilder("cc-keystore")
            .withDisplayName("Gradle Configuration Cache keystore")

    override fun getKey(): SecretKey {
        return cacheBuilderFor()
            .withInitializer {
                createKeyStoreAndGenerateKey(it.keyStoreFile())
            }.open().useToRun {
                try {
                    loadSecretKeyFromExistingKeystore(keyStoreFile())
                } catch (loadException: Exception) {
                    // try to recover from a tampered-with keystore by generating a new key
                    // TODO:configuration-cache do we really need this?
                    try {
                        createKeyStoreAndGenerateKey(keyStoreFile())
                    } catch (e: Exception) {
                        e.addSuppressed(loadException)
                        throw e
                    }
                }
            }
    }

    private
    fun PersistentCache.keyStoreFile(): File =
        File(baseDir, "gradle.keystore")

    companion object {
        // JKS does not support non-PrivateKeys
        const val KEYSTORE_TYPE = "pkcs12"
        val KEYSTORE_PASSWORD = charArrayOf('c', 'c')
    }
}


private
class EnvironmentVarKeySource(encryptionAlgorithm: String) : SecretKeySource {
    private
    val secretKey: SecretKey by lazy {
        Base64.getDecoder().decode(getKeyAsBase64()).let { keyAsBytes ->
            SecretKeySpec(keyAsBytes, encryptionAlgorithm)
        }
    }

    private
    fun getKeyAsBase64(): String = System.getenv(GRADLE_ENCRYPTION_KEY_ENV_KEY) ?: ""

    override fun getKey(): SecretKey = secretKey

    override val sourceDescription: String
        get() = "$GRADLE_ENCRYPTION_KEY_ENV_KEY environment variable"

    companion object {
        const val GRADLE_ENCRYPTION_KEY_ENV_KEY = "GRADLE_ENCRYPTION_KEY"
    }
}


private
class NoEncryptionKeySource : SecretKeySource {
    override fun getKey(): SecretKey? {
        logger.warn("Encryption of the configuration cache is disabled.")
        return null
    }

    override val sourceDescription: String
        get() = "no encryption"
}


internal
enum class EncryptionKind(val encrypted: Boolean) {
    NONE(false),
    ENV_VAR(true),
    KEYSTORE(true);

    companion object {
        fun select(requested: Boolean): EncryptionKind {
            val keyInEnvVar = System.getenv(EnvironmentVarKeySource.GRADLE_ENCRYPTION_KEY_ENV_KEY)
            return when {
                !requested -> NONE
                !keyInEnvVar.isNullOrBlank() -> ENV_VAR
                else -> KEYSTORE
            }
        }
    }
}
