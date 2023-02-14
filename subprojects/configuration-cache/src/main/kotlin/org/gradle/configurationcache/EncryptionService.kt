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
import java.security.spec.AlgorithmParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec


/**
 * Provides access to configuration parameters to control encryption.
 */
internal
interface EncryptionConfiguration {
    val isEncrypting: Boolean
    val encryptionKeyHashCode: HashCode
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

    private
    val encryptionAlgorithm: String = startParameter.encryptionAlgorithm

    private
    val initVectorLength: Int = startParameter.initVectorLength

    init {
        if (encryptingRequested) {
            logger.info("""Configuration cache encryption requested
                - algorithm: $encryptionAlgorithm
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

    override fun outputStream(stateType: StateType, output: () -> OutputStream): OutputStream =
        if (shouldEncrypt && stateType.encryptable) {
            encryptingOutputStream(output.invoke())
        } else
            output.invoke()

    override fun inputStream(stateType: StateType, input: () -> InputStream): InputStream =
        if (shouldEncrypt && stateType.encryptable) {
            decryptingInputStream(input.invoke())
        } else
            input.invoke()

    private
    fun decryptingInputStream(inputStream: InputStream): InputStream {
        val cipher = cipher()
        val fileIv = ByteArray(initVectorLength)
        inputStream.read(fileIv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, getAlgorithmParameter(fileIv))
        return CipherInputStream(inputStream, cipher)
    }

    private
    fun getAlgorithmParameter(initializationVector: ByteArray): AlgorithmParameterSpec =
        when {
            encryptionAlgorithm.startsWith("AES/GCM") ->
                GCMParameterSpec(128, initializationVector)
            else ->
                IvParameterSpec(initializationVector)
        }

    private
    fun encryptingOutputStream(outputStream: OutputStream): OutputStream {
        val cipher = cipher()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        outputStream.write(cipher.iv)
        return CipherOutputStream(outputStream, cipher)
    }

    private
    fun cipher() = Cipher.getInstance(encryptionAlgorithm)
}


private
fun SecretKey.asString(): String =
    String(Base64.getEncoder().encode(encoded)) + " $algorithm/$format"


