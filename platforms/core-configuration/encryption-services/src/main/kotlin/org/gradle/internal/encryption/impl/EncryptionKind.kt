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
