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


/**
 * Provides access to configuration parameters to control encryption.
 */
internal
interface EncryptionConfiguration {
    val isEncrypting: Boolean
    val encryptionKeyHashCode: HashCode
    val initVectorLength: Int
    val encryptionAlgorithm: EncryptionAlgorithm
}


/**
 * A service for encrypting/decrypting streams.
 */
internal
interface EncryptionService : EncryptionConfiguration {
    fun outputStream(stateType: StateType, output: () -> OutputStream): OutputStream
    fun outputStream(stateFile: ConfigurationCacheStateFile): OutputStream =
        outputStream(stateFile.stateType, stateFile::outputStream)
    fun outputStream(stateFile: ConfigurationCacheStateStore.StateFile): OutputStream =
        outputStream(stateFile.stateType, stateFile.file::outputStream)
    fun inputStream(stateType: StateType, input: () -> InputStream): InputStream
    fun inputStream(stateFile: ConfigurationCacheStateFile): InputStream =
        inputStream(stateFile.stateType, stateFile::inputStream)
    fun inputStream(stateFile: ConfigurationCacheStateStore.StateFile): InputStream =
        inputStream(stateFile.stateType, stateFile.file::inputStream)
    fun encoder(stateType: StateType, output: () -> OutputStream): Encoder
    fun decoder(stateType: StateType, input: () -> InputStream): Decoder
}


@ServiceScope(Scopes.BuildTree::class)
internal
class SimpleEncryptionService(startParameter: ConfigurationCacheStartParameter) : EncryptionService {
    private
    val keyStorePath: File = startParameter.keystorePath
        ?.let { File(it) } ?: File(startParameter.gradleUserHomeDir, "gradle.keystore")

    private
    val keyStorePassword: String? = startParameter.keystorePassword

    private
    val keyPassword: String = startParameter.keyPassword

    private
    val keyAlias: String = startParameter.keyAlias

    private
    val keyAlgorithm: String = startParameter.keyAlgorithm

    private
    val keyProtection =
        PasswordProtection(keyPassword.toCharArray())

    private
    val encryptingRequested: Boolean = startParameter.encryptedCache

    private
    val secretKey: SecretKey? by lazy { if (encryptingRequested) computeSecretKey() else null }

    private
    val shouldEncrypt: Boolean by lazy { encryptingRequested && secretKey != null }

    override
    val encryptionAlgorithm: EncryptionAlgorithm by lazy {
        SupportedEncryptionAlgorithm.byTransformation(startParameter.encryptionAlgorithm)!!
    }

    override
    val initVectorLength: Int = startParameter.initVectorLength

    init {
        if (encryptingRequested) {
            logger.info("""Configuration cache encryption requested
                - algorithm: ${encryptionAlgorithm.transformation}
                - keystore at: $keyStorePath
                - key algorithm: $keyAlgorithm
                - key alias: $keyAlias""".trimMargin()
            )
            if (!isEncrypting) {
                logger.warn("Configuration cache encryption requested but not enabled")
            }
        }
    }

    private
    fun computeSecretKey(): SecretKey? {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        val key: SecretKey
        val alias = keyAlias
        if (keyStorePath.isFile) {
            logger.info("Loading keystore from $keyStorePath")
            keyStorePath.inputStream().use { fis ->
                try {
                    ks.load(fis, keyStorePassword?.toCharArray())
                } catch (e: Exception) {
                    // could not load, store password is wrong
                    logger.warn("Keystore exists, but cannot be loaded, encryption will not be enabled", e)
                    return@computeSecretKey null
                }
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
        val newKey = KeyGenerator.getInstance(keyAlgorithm).generateKey()!!
        logger.info("Generated key")
        val entry = KeyStore.SecretKeyEntry(newKey)
        ks.setEntry(alias, entry, keyProtection)
        keyStorePath.outputStream().use { fos -> ks.store(fos, keyStorePassword?.toCharArray()) }
        return newKey
    }

    override val isEncrypting: Boolean
        get() = shouldEncrypt

    override val encryptionKeyHashCode: HashCode by lazy {
        secretKey?.let {
            Hashing.sha512().newHasher().apply {
                putBytes(it.encoded)
                putString(it.format)
                putString(it.algorithm)
                putInt(initVectorLength)
            }.hash()
        } ?: Hashing.newHasher().hash()
    }

    override fun encoder(stateType: StateType, output: () -> OutputStream): Encoder =
        if (isEncryptable(stateType))
            EncryptingEncoder(output.invoke(), encryptionAlgorithm.newSession(secretKey))
        else
            KryoBackedEncoder(output.invoke())

    override fun decoder(stateType: StateType, input: () -> InputStream): Decoder =
        if (isEncryptable(stateType))
            DecryptingDecoder(input.invoke(), encryptionAlgorithm.newSession(secretKey))
        else
            KryoBackedDecoder(input.invoke())

    private
    fun isEncryptable(stateType: StateType) = shouldEncrypt && stateType.encryptable

    override fun outputStream(stateType: StateType, output: () -> OutputStream): OutputStream =
//        if (isEncryptable(stateType))
//            encryptingOutputStream(output.invoke())
//        else
        output.invoke()

    override fun inputStream(stateType: StateType, input: () -> InputStream): InputStream =
//        if (isEncryptable(stateType))
//            decryptingInputStream(input.invoke())
//        else
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
}


private
fun SecretKey.asString(): String =
    String(Base64.getEncoder().encode(encoded)) + " $algorithm/$format"


