/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin.support

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageUtil
import org.jetbrains.kotlin.cli.common.messages.OutputMessageUtil

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler.compileBunchOfSources
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler.compileScript
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots

import org.jetbrains.kotlin.codegen.CompilationException

import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer.dispose
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer.newDisposable

import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JVMConfigurationKeys.OUTPUT_DIRECTORY
import org.jetbrains.kotlin.config.JVMConfigurationKeys.OUTPUT_JAR
import org.jetbrains.kotlin.config.addKotlinSourceRoots

import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.utils.PathUtil

import org.slf4j.Logger

import java.io.File

import java.lang.IllegalStateException

internal
fun compileKotlinScript(scriptFile: File,
                        scriptDef: KotlinScriptDefinition,
                        classLoader: ClassLoader,
                        log: Logger): Class<*> {
    withRootDisposable { rootDisposable ->
        withMessageCollectorFor(log) { messageCollector ->
            val configuration = compilerConfigurationFor(messageCollector, scriptFile).apply {
                setModuleName("buildscript")
                addScriptDefinition(scriptDef)
            }
            val environment = kotlinCoreEnvironmentFor(configuration, rootDisposable)
            return compileScript(environment, classLoader)
                ?: throw IllegalStateException("Internal error: unable to compile script, see log for details")
        }
    }
}

internal
fun compileToJar(outputJar: File,
                 sourceFile: File,
                 logger: Logger,
                 classPath: List<File> = emptyList()): Boolean =
    compileTo(OUTPUT_JAR, outputJar, sourceFile, logger, classPath)

internal
fun compileToDirectory(outputDirectory: File,
                       sourceFile: File,
                       logger: Logger,
                       classPath: List<File> = emptyList()): Boolean =
    compileTo(OUTPUT_DIRECTORY, outputDirectory, sourceFile, logger, classPath)

private
fun compileTo(outputConfigurationKey: CompilerConfigurationKey<File>,
              output: File,
              sourceFile: File,
              logger: Logger,
              classPath: List<File>): Boolean {
    withRootDisposable { disposable ->
        withMessageCollectorFor(logger) { messageCollector ->
            val configuration = compilerConfigurationFor(messageCollector, sourceFile).apply {
                put(outputConfigurationKey, output)
                setModuleName(sourceFile.nameWithoutExtension)
                addJvmClasspathRoots(classPath)
            }
            val environment = kotlinCoreEnvironmentFor(configuration, disposable)
            return compileBunchOfSources(environment)
        }
    }
}

private
inline fun <T> withRootDisposable(action: (Disposable) -> T): T {
    val rootDisposable = newDisposable()
    try {
        return action(rootDisposable)
    } finally {
        dispose(rootDisposable)
    }
}

private
inline fun <T> withMessageCollectorFor(log: Logger, action: (MessageCollector) -> T): T {
    val messageCollector = messageCollectorFor(log)
    try {
        return action(messageCollector)
    } catch (ex: CompilationException) {
        messageCollector.report(
            CompilerMessageSeverity.EXCEPTION,
            OutputMessageUtil.renderException(ex),
            MessageUtil.psiElementToMessageLocation(ex.element))

        throw IllegalStateException("Internal error: ${OutputMessageUtil.renderException(ex)}")
    }
}

private
fun compilerConfigurationFor(messageCollector: MessageCollector, sourceFile: File): CompilerConfiguration =
    CompilerConfiguration().apply {
        addKotlinSourceRoots(listOf(sourceFile.canonicalPath))
        addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
        put<MessageCollector>(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
    }

private
fun CompilerConfiguration.setModuleName(name: String) {
    put(CommonConfigurationKeys.MODULE_NAME, name)
}

private
fun CompilerConfiguration.addScriptDefinition(scriptDef: KotlinScriptDefinition) {
    add(JVMConfigurationKeys.SCRIPT_DEFINITIONS, scriptDef)
}

private
fun kotlinCoreEnvironmentFor(configuration: CompilerConfiguration, rootDisposable: Disposable) =
    KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)

private
fun messageCollectorFor(log: Logger): MessageCollector =
    object : MessageCollector {
        override fun hasErrors(): Boolean = false

        override fun clear() {}

        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation) {
            fun msg() =
                if (location == CompilerMessageLocation.NO_LOCATION) "$message"
                else "$message ($location)"

            when (severity) {
                in CompilerMessageSeverity.ERRORS -> log.error("Error: " + msg())
                CompilerMessageSeverity.ERROR -> log.error(msg())
                CompilerMessageSeverity.WARNING -> log.info("Warning: " + msg())
                CompilerMessageSeverity.LOGGING -> log.info(msg())
                CompilerMessageSeverity.INFO -> log.info(msg())
                else -> {
                }
            }
        }
    }
