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

package org.gradle.script.lang.kotlin

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.config.JVMConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.addKotlinSourceRoots
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.utils.PathUtil

import com.intellij.openapi.util.Disposer

import org.slf4j.Logger

import java.io.File

fun compileKotlinScript(scriptFile: File, scriptDef: KotlinScriptDefinition, log: Logger): Class<*> {
    val messageCollector = messageCollectorFor(log)
    val rootDisposable = Disposer.newDisposable()
    try {
        val configuration = compilerConfigFor(listOf(scriptFile), messageCollector)
        configuration.add(CommonConfigurationKeys.SCRIPT_DEFINITIONS_KEY, scriptDef)
        val paths = PathUtil.getKotlinPathsForCompiler()
        val environment = KotlinCoreEnvironment.createForProduction(
            rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)

        val scriptClass: Class<*>? = KotlinToJVMBytecodeCompiler.compileScript(configuration, paths, environment)

        return scriptClass
            ?: throw IllegalStateException("Internal error: unable to compile script, see log for details")
    } catch (ex: CompilationException) {
        messageCollector.report(
            CompilerMessageSeverity.EXCEPTION,
            OutputMessageUtil.renderException(ex),
            MessageUtil.psiElementToMessageLocation(ex.element))

        throw IllegalStateException("Internal error: ${OutputMessageUtil.renderException(ex)}")
    } finally {
        rootDisposable.dispose()
    }
}

private fun compilerConfigFor(sourceFiles: List<File>, messageCollector: MessageCollector) =
    CompilerConfiguration().apply {
        addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
        addJvmClasspathRoots(currentClassPath())
        addKotlinSourceRoots(sourceFiles.map { it.canonicalPath })
        put(JVMConfigurationKeys.MODULE_NAME, "buildscript")
        put<MessageCollector>(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
    }

private fun currentClassPath(): List<File> =
    System.getProperty("java.class.path")
        .split(File.pathSeparator)
        .map { File(it) }
        .filter { it.exists() }

private fun messageCollectorFor(log: Logger): MessageCollector =
    MessageCollector { severity, message, location ->
        fun msg() =
            if (location == CompilerMessageLocation.NO_LOCATION) "$message"
            else "$message ($location)"

        when (severity) {
            in CompilerMessageSeverity.ERRORS -> log.info("Error: " + msg())
            CompilerMessageSeverity.LOGGING -> log.info(msg())
            CompilerMessageSeverity.INFO -> log.info(msg())
            CompilerMessageSeverity.WARNING -> log.info("Warning: " + msg())
            CompilerMessageSeverity.ERROR -> log.info(msg())
            else -> {
            }
        }
    }
