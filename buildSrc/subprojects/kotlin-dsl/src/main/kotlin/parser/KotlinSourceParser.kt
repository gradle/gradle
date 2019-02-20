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

package parser

import org.gradle.api.Action

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
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.PathUtil

import java.io.File


class KotlinSourceParser {

    private
    val messageCollector: MessageCollector
        get() = PrintingMessageCollector(System.out, MessageRenderer.PLAIN_RELATIVE_PATHS, false)

    fun forEachParsedKotlinFile(sourceRoots: List<File>, block: Action<KtFile>) {
    }

    fun <T : PsiElement> forEachParsedElementOfType(sourceRoots: List<File>, type: Class<T>, block: Action<T>): Unit =
        withParsedKotlinSource(sourceRoots.toList()) { ktFiles ->
            ktFiles.forEach { ktFile ->
                ktFile.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        super.visitElement(element)
                        if (type.isAssignableFrom(element.javaClass)) {
                            block.execute(type.cast(element))
                        }
                    }
                })
            }
        }

    fun <T : Any> mapParsedKotlinFiles(vararg sourceRoots: File, block: (KtFile) -> T): List<T> =
        withParsedKotlinSource(sourceRoots.toList()) { ktFiles ->
            ktFiles.map(block)
        }

    private
    fun <T : Any> withParsedKotlinSource(sourceRoots: List<File>, block: (List<KtFile>) -> T) = withRootDisposable {

        // TODO collect errors and react
        val configuration = CompilerConfiguration().apply {

            put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
            put(JVMConfigurationKeys.RETAIN_OUTPUT_IN_MEMORY, false)
            put(CommonConfigurationKeys.MODULE_NAME, "parser")

            // TODO infer from source set configuration?
            put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_1_8)
            put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, LanguageVersionSettingsImpl(
                languageVersion = LanguageVersion.KOTLIN_1_3,
                apiVersion = ApiVersion.KOTLIN_1_3,
                specificFeatures = mapOf(
                    LanguageFeature.NewInference to LanguageFeature.State.ENABLED,
                    LanguageFeature.SamConversionForKotlinFunctions to LanguageFeature.State.ENABLED
                ),
                analysisFlags = mapOf(
                    AnalysisFlags.skipMetadataVersionCheck to true
                )
            ))

            // TODO add source set compile classpath?
            addJvmClasspathRoots(PathUtil.getJdkClassesRoots(Jvm.current().javaHome))
            addKotlinSourceRoots(sourceRoots.map { it.canonicalPath })
        }
        val environment = KotlinCoreEnvironment.createForProduction(this, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        environment.getSourceFiles().let(block)
    }
}


internal
inline fun <T> withRootDisposable(action: Disposable.() -> T): T {
    val rootDisposable = Disposer.newDisposable()
    try {
        return action(rootDisposable)
    } finally {
        Disposer.dispose(rootDisposable)
    }
}
