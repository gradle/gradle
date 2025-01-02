/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.encryption.impl

import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.encryption.EncryptionService
import org.gradle.internal.extensions.core.getInternalFlag
import org.gradle.internal.extensions.core.getInternalString
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.util.internal.EncryptionAlgorithm
import org.gradle.util.internal.SupportedEncryptionAlgorithm
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.InvalidKeyException
import javax.crypto.SecretKey


internal
class DefaultEncryptionService(
    options: InternalOptions,
    private val cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
) : EncryptionService {

    private
    val encryptionRequestedOption: Boolean = options.getInternalFlag("org.gradle.configuration-cache.internal.encryption", true)

    private
    val keystoreDirOption: String? = options.getInternalString("org.gradle.configuration-cache.internal.key-store-dir", null)

    private
    val encryptionAlgorithmOption: String = options.getInternalString("org.gradle.configuration-cache.internal.encryption-alg", SupportedEncryptionAlgorithm.getDefault().transformation)

    private
    val secretKey: SecretKey? by lazy {
        produceSecretKey(EncryptionKind.select(encryptionRequestedOption))
    }

    private
    fun produceSecretKey(encryptionKind: EncryptionKind) =
        secretKeySource(encryptionKind).let { keySource ->
            try {
                secretKeyFrom(keySource)?.also { key ->
                    assertKeyLength(key)
                }
            } catch (e: Exception) {
                throw GradleException("Error loading encryption key from ${keySource.sourceDescription}", e)
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
        SupportedEncryptionAlgorithm.getAll().find { it.transformation == encryptionAlgorithmOption }
            ?: throw InvalidUserDataException(
                "Unsupported encryption algorithm: ${encryptionAlgorithmOption}. " +
                    "Supported algorithms are: ${SupportedEncryptionAlgorithm.getAll().joinToString { it.transformation }}"
            )
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

    override fun outputStream(output: OutputStream): OutputStream =
        if (isEncrypting)
            encryptionAlgorithm.encryptedStream(output, secretKey)
        else
            output

    override fun inputStream(input: InputStream): InputStream =
        if (isEncrypting)
            encryptionAlgorithm.decryptedStream(input, secretKey)
        else
            input

    private
    fun secretKeySource(kind: EncryptionKind): SecretKeySource =
        when (kind) {
            EncryptionKind.KEYSTORE ->
                KeyStoreKeySource(
                    encryptionAlgorithm = encryptionAlgorithm.algorithm,
                    customKeyStoreDir = keystoreDirOption?.let { File(it) },
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
