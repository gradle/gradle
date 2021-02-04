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

package gradlebuild.basics.util

import org.gradle.internal.jvm.Jvm

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.AnalysisFlag
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.JvmAnalysisFlags
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.PathUtil


import java.io.File

import kotlin.reflect.KProperty1
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.memberProperties


class KotlinSourceParser {

    data class ParsedKotlinFiles(

        val ktFiles: List<KtFile>,

        private
        val disposable: Disposable

    ) : AutoCloseable {

        override fun close() {
            Disposer.dispose(disposable)
        }
    }

    private
    val messageCollector: MessageCollector
        get() = PrintingMessageCollector(System.out, MessageRenderer.PLAIN_RELATIVE_PATHS, false)

    fun <T : Any> mapParsedKotlinFiles(vararg sourceRoots: File, block: (KtFile) -> T): List<T> =
        withParsedKotlinSource(sourceRoots.toList()) { ktFiles ->
            ktFiles.map(block)
        }

    fun parseSourceRoots(sourceRoots: List<File>, compilationClasspath: List<File>): ParsedKotlinFiles =
        Disposer.newDisposable().let { disposable ->
            ParsedKotlinFiles(disposable.parseKotlinFiles(sourceRoots, compilationClasspath), disposable)
        }

    private
    fun <T : Any?> withParsedKotlinSource(sourceRoots: List<File>, block: (List<KtFile>) -> T) =
        Disposer.newDisposable().use {
            parseKotlinFiles(sourceRoots, emptyList()).let(block)
        }

    private
    fun Disposable.parseKotlinFiles(sourceRoots: List<File>, compilationClasspath: List<File>): List<KtFile> {
        configureKotlinCompilerIoForWindowsSupport()
        val configuration = CompilerConfiguration().apply {

            put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
            put(JVMConfigurationKeys.RETAIN_OUTPUT_IN_MEMORY, false)
            put(JVMConfigurationKeys.DISABLE_OPTIMIZATION, true)
            put(CommonConfigurationKeys.MODULE_NAME, "parser")

            configureKotlinCompilerForGradleBuild()

            addJvmClasspathRoots(PathUtil.getJdkClassesRoots(Jvm.current().javaHome))
            addJvmClasspathRoots(compilationClasspath)
            addKotlinSourceRoots(sourceRoots.map { it.canonicalPath })
        }
        val environment = KotlinCoreEnvironment.createForProduction(this, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        return environment.getSourceFiles()
    }

    private
    fun configureKotlinCompilerIoForWindowsSupport() =
        org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback()

    private
    fun CompilerConfiguration.configureKotlinCompilerForGradleBuild() {

        put(
            CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
            LanguageVersionSettingsImpl(
                languageVersion = LanguageVersion.KOTLIN_1_4,
                apiVersion = ApiVersion.KOTLIN_1_4,
                analysisFlags = mapOf(strictJsr305AnalysisFlag)
            )
        )

        put(JVMConfigurationKeys.PARAMETERS_METADATA, true)
        put(JVMConfigurationKeys.SKIP_RUNTIME_VERSION_CHECK, true)
        put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_1_8)
    }

    /**
     * Temporary compatibility with Kotlin 1.4.20 / 1.4.30.
     */
    private
    val strictJsr305AnalysisFlag: Pair<AnalysisFlag<*>, Any?>
        get() {
            val kotlinOneDotThirty = KotlinCompilerVersion.VERSION.startsWith("1.4.3")
            val (flagName, valueTypeName) = when {
                kotlinOneDotThirty -> "javaTypeEnhancementState" to "org.jetbrains.kotlin.utils.JavaTypeEnhancementState"
                else -> "jsr305" to "org.jetbrains.kotlin.utils.Jsr305State"
            }
            val flags = JvmAnalysisFlags::class.objectInstance!!
            val flag = JvmAnalysisFlags::class.memberProperties.single { it.name == flagName }.get(flags) as AnalysisFlag<*>
            val valueType = Class.forName(valueTypeName).kotlin
            @Suppress("UNCHECKED_CAST") val valueProperty = valueType.companionObject!!.memberProperties.single { it.name == "STRICT" } as KProperty1<Any, *>
            val value = valueProperty.getValue(valueType.companionObjectInstance!!, valueProperty)
            return flag to value
        }
}


private
inline fun <T : Any?> Disposable.use(action: Disposable.() -> T) =
    try {
        action(this)
    } finally {
        Disposer.dispose(this)
    }
