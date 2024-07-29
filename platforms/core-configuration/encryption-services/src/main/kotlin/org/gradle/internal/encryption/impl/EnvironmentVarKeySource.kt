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

import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec


internal
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
