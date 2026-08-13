/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.kotlin.dsl.fixtures

import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.support.BtaClasspathSnapshotter
import org.gradle.kotlin.dsl.support.cleanupKotlinCompilers
import org.gradle.kotlin.dsl.support.toKotlinJvmTarget
import org.jetbrains.kotlin.CoreEnvironmentDeprecation
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer.dispose
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer.newDisposable
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File

fun compileToDirectory(
    outputDirectory: File,
    moduleName: String,
    sourceFiles: Iterable<File>,
    classPath: Iterable<File>,
    jvmTarget: JavaVersion? = null,
): Boolean {

    withDisposable {
        val arguments = K2JVMCompilerArguments().apply {
            this.destination = outputDirectory.absolutePath
            this.moduleName = moduleName
            this.freeArgs = sourceFiles.map { it.absolutePath }
            this.classpath = (classPath + kotlinStdlibJar).joinToString(separator = File.pathSeparator) { it.absolutePath }
            this.noStdlib = true
            jvmTarget?.toKotlinJvmTarget()?.description?.let { this.jvmTarget = it }
        }
        return K2JVMCompiler().exec(messageCollector, Services.EMPTY, arguments) == ExitCode.OK
    }
}

/**
 * Cleans up the shared Kotlin compiler instances and the classpath-snapshotting session,
 * as [org.gradle.kotlin.dsl.provider.KotlinCompilerContextDisposer] does at the end of a build.
 * Call from an `@After` in tests that compile against jars inside the test directory: the environment
 * caches open jar handles, which on Windows prevent deleting the directory.
 */
fun disposeKotlinCompilerContext() {
    cleanupKotlinCompilers()
    BtaClasspathSnapshotter.closeSession()
}


private
inline fun <T> withDisposable(action: Disposable.() -> T): T {
    val rootDisposable = newDisposable()
    try {
        return action(rootDisposable)
    } finally {
        dispose(rootDisposable)
    }
}

private
val messageCollector: MessageCollector
    get() = PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, false)


private
val kotlinStdlibJar: File
    get() = PathUtil.getResourcePathForClass(Unit::class.java)