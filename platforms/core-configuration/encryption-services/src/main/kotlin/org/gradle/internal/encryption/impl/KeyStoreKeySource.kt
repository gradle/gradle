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

import org.gradle.cache.CacheBuilder
import org.gradle.cache.PersistentCache
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.extensions.stdlib.useToRun
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey


internal
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
