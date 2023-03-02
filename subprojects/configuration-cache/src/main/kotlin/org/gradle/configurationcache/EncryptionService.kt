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
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.DecryptingDecoder
import org.gradle.internal.serialize.kryo.EncryptingEncoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.EncryptionAlgorithm
import org.gradle.util.internal.SupportedEncryptionAlgorithm
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.KeyStore.PasswordProtection
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


enum class EncryptionStrategy(val id: Int, val encrypted: Boolean) {
    None(0, false),
    Streams(1, true),
    Encoding(2, true);

    companion object {
        fun forId(encryptionStrategyId: Int): EncryptionStrategy =
            values().find { it.id == encryptionStrategyId } ?: None
    }
}


/**
 * A service for encrypting/decrypting streams.
 */
internal
interface EncryptionService : EncryptionConfiguration {
    fun outputStream(stateType: StateType, output: () -> OutputStream): OutputStream
    fun inputStream(stateType: StateType, input: () -> InputStream): InputStream
    fun encoder(stateType: StateType, output: () -> OutputStream): Encoder
    fun decoder(stateType: StateType, input: () -> InputStream): Decoder
}


@ServiceScope(Scopes.BuildTree::class)
internal
class SimpleEncryptionService(startParameter: ConfigurationCacheStartParameter) : EncryptionService {

    private
    val encryptionStrategy: EncryptionStrategy = EncryptionStrategy.forId(startParameter.encryptionStrategy)

    private
    val encryptingRequested: Boolean = encryptionStrategy.encrypted

    private
    val secretKey: SecretKey? by lazy {
        if (keySource == null) {
            null
        } else
            try {
                keySource!!.getKey().also {
                    // acquire a cipher just to validate the key
                    encryptionAlgorithm.newSession(it).encryptingCipher({})
                }
            } catch (e: Exception) {
                logger.warn("Encryption was requested but could not be enabled", e)
                null
            }
    }

    private
    val shouldEncrypt: Boolean by lazy { encryptingRequested && secretKey != null }

    override
    val encryptionAlgorithm: EncryptionAlgorithm by lazy {
        SupportedEncryptionAlgorithm.byTransformation(startParameter.encryptionAlgorithm)
    }

    private
    val keySource: SecretKeySource? by lazy {
        if (encryptingRequested)
            sourceKeyFromEnvironment()
                ?: sourceKeyFromKeyStore(startParameter)
        else
            null
    }

    override val isEncrypting: Boolean
        get() = shouldEncrypt

    override val encryptionKeyHashCode: HashCode by lazy {
        secretKey?.let {
            Hashing.sha512().newHasher().apply {
                putBytes(it.encoded)
                putString(encryptionAlgorithm.transformation)
                putInt(encryptionStrategy.id)
            }.hash()
        } ?: Hashing.newHasher().hash()
    }

    override fun encoder(stateType: StateType, output: () -> OutputStream): Encoder =
        if (shouldEncryptEncoding(stateType))
            EncryptingEncoder(output.invoke(), encryptionAlgorithm.newSession(secretKey))
        else
            KryoBackedEncoder(output.invoke())

    override fun decoder(stateType: StateType, input: () -> InputStream): Decoder =
        if (shouldEncryptEncoding(stateType))
            DecryptingDecoder(input.invoke(), encryptionAlgorithm.newSession(secretKey))
        else
            KryoBackedDecoder(input.invoke())

    private
    fun shouldEncryptEncoding(stateType: StateType) =
        encryptionStrategy == EncryptionStrategy.Encoding && isStateEncryptable(stateType)

    private
    fun shouldEncryptStreams(stateType: StateType) =
        encryptionStrategy == EncryptionStrategy.Streams && isStateEncryptable(stateType)

    private
    fun isStateEncryptable(stateType: StateType) = shouldEncrypt && stateType.encryptable

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

    private
    fun sourceKeyFromEnvironment(): SecretKeySource? =
        EnvironmentKeySource.fromEnvironment(encryptionAlgorithm.algorithm)
}


interface SecretKeySource {
    fun getKey(): SecretKey
}


class EnvironmentKeySource(algorithm: String, keyAsBase64: String) : SecretKeySource {
    val secretKey: SecretKey = Base64.getDecoder().decode(keyAsBase64).let { keyAsBytes ->
        SecretKeySpec(keyAsBytes, algorithm)
    }
    companion object {
        const val GRADLE_ENCRYPTION_KEY_ENV_VAR = "GRADLE_CC_ENCRYPTION_KEY"
        fun fromEnvironment(algorithm: String, envVarName: String = GRADLE_ENCRYPTION_KEY_ENV_VAR): SecretKeySource? =
            System.getenv(envVarName)?.let {
                EnvironmentKeySource(algorithm, it)
            }
    }

    override fun getKey(): SecretKey = secretKey
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
        PasswordProtection(keyPassword.toCharArray())

    override fun getKey(): SecretKey = computeSecretKey()

    private
    fun computeSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        val key: SecretKey
        val alias = keyAlias
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
        return key
    }

    private
    fun generateKey(ks: KeyStore, alias: String): SecretKey {
        val newKey = KeyGenerator.getInstance(encryptionAlgorithm).generateKey()!!
        logger.info("Generated key")
        val entry = KeyStore.SecretKeyEntry(newKey)
        ks.setEntry(alias, entry, keyProtection)
        keyStorePath.parentFile.mkdirs()
        keyStorePath.outputStream().use { fos -> ks.store(fos, keyStorePassword?.toCharArray()) }
        return newKey
    }

    companion object {
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
