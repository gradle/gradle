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

package gradlebuild.basics.kotlindsl

import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmAnalysisFlags
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.load.java.JavaTypeEnhancementState
import org.jetbrains.kotlin.load.java.Jsr305Settings
import org.jetbrains.kotlin.load.java.ReportLevel


fun KotlinCompile.configureKotlinCompilerForGradleBuild() {
    compilerOptions {
        allWarningsAsErrors = true
        apiVersion = KotlinVersion.KOTLIN_1_8
        languageVersion = KotlinVersion.KOTLIN_1_8
        jvmTarget = JvmTarget.JVM_1_8
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-java-parameters",
            "-Xsam-conversions=class",
            "-Xcontext-receivers",
            "-Xskip-metadata-version-check",
        )
    }
}


fun CompilerConfiguration.configureKotlinCompilerForGradleBuild() {

    put(
        CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
        LanguageVersionSettingsImpl(
            languageVersion = LanguageVersion.KOTLIN_1_8,
            apiVersion = ApiVersion.KOTLIN_1_8,
            analysisFlags = mapOf(
                JvmAnalysisFlags.javaTypeEnhancementState to JavaTypeEnhancementState(
                    Jsr305Settings(ReportLevel.STRICT, ReportLevel.STRICT)
                ) { ReportLevel.STRICT },
                AnalysisFlags.skipMetadataVersionCheck to true,
            )
        )
    )

    put(JVMConfigurationKeys.SAM_CONVERSIONS, JvmClosureGenerationScheme.CLASS)
    put(JVMConfigurationKeys.PARAMETERS_METADATA, true)
    put(JVMConfigurationKeys.JVM_TARGET, org.jetbrains.kotlin.config.JvmTarget.JVM_1_8)
}
