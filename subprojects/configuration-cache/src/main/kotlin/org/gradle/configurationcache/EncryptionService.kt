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
import org.gradle.util.internal.EncryptionAlgorithm
import org.gradle.util.internal.EncryptionAlgorithm.EncryptionException
import org.gradle.util.internal.SupportedEncryptionAlgorithm
import java.io.InputStream
import java.io.OutputStream
import java.security.InvalidKeyException
import java.util.Base64
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
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
    Streams(1, true);
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
class DefaultEncryptionService(startParameter: ConfigurationCacheStartParameter) : EncryptionService {

    private
    val encryptionStrategy: EncryptionStrategy = if (startParameter.encryptionRequested) EncryptionStrategy.Streams else EncryptionStrategy.None

    private
    val encryptingRequested: Boolean = encryptionStrategy.encrypted

    private
    val secretKey: SecretKey? by lazy {
        if (keySource == null) {
            null
        } else
            try {
                keySource!!.getKey().also {
                    val keyLength = it.encoded.size
                    if (keyLength < 16) {
                        throw InvalidKeyException("Encryption key length is $keyLength bytes, but must be at least 16 bytes long")
                    }
                    // acquire a cipher just to validate the key
                    encryptionAlgorithm.newSession(it).encryptingCipher {}
                }
            } catch (e: EncryptionException) {
                logger.warn("Encryption was requested but could not be enabled: ${e.message}")
                null
            } catch (e: Exception) {
                logger.warn("Encryption was requested but could not be enabled: $e")
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
    fun sourceKeyFromEnvironment(): SecretKeySource =
        EnvironmentVarKeySource(encryptionAlgorithm.algorithm, EnvironmentVarKeySource.GRADLE_ENCRYPTION_KEY_ENV_KEY)
}


interface SecretKeySource {
    fun getKey(): SecretKey
}


class EnvironmentVarKeySource(algorithm: String, private val envVarName: String) : SecretKeySource {
    private
    val secretKey: SecretKey by lazy {
        Base64.getDecoder().decode(getKeyAsBase64()).let { keyAsBytes ->
            SecretKeySpec(keyAsBytes, algorithm)
        }
    }

    private
    fun getKeyAsBase64(): String = System.getenv(envVarName) ?: ""

    override fun getKey(): SecretKey = secretKey

    companion object {
        const val GRADLE_ENCRYPTION_KEY_ENV_KEY = "GRADLE_ENCRYPTION_KEY"
    }
}
