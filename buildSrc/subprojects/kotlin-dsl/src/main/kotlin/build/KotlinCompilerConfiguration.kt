/*
 * Copyright 2019 the original author or authors.
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

package build

import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JvmAnalysisFlags
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.utils.Jsr305State


internal
fun KotlinCompile.configureKotlinCompilerForGradleBuild() {

    kotlinOptions {
        apiVersion = "1.3"
        languageVersion = "1.3"
        freeCompilerArgs += listOf(
            "-Xjsr305=strict",
            "-java-parameters",
            "-Xskip-runtime-version-check",
            "-progressive"
        )
        jvmTarget = "1.8"
    }
}


internal
fun CompilerConfiguration.configureKotlinCompilerForGradleBuild() {

    put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, LanguageVersionSettingsImpl(
        languageVersion = LanguageVersion.KOTLIN_1_3,
        apiVersion = ApiVersion.KOTLIN_1_3,
        analysisFlags = mapOf(JvmAnalysisFlags.jsr305 to Jsr305State.STRICT)
    ))

    put(JVMConfigurationKeys.PARAMETERS_METADATA, true)
    put(JVMConfigurationKeys.SKIP_RUNTIME_VERSION_CHECK, true)
    put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_1_8)
}
