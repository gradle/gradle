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

package org.gradle.kotlin.dsl.plugins.precompiled

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment

import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile

import org.jetbrains.kotlin.config.CompilerConfiguration

import org.jetbrains.kotlin.idea.KotlinFileType

import org.jetbrains.kotlin.psi.KtFile


object KotlinParser {

    fun <T> map(code: String, f: KtFile.() -> T): T =
        withProject { f(parse("code.kt", code)) }

    fun Project.parse(name: String, code: String): KtFile =
        psiManager.findFile(virtualFile(name, code)) as KtFile

    fun virtualFile(name: String, code: String) =
        LightVirtualFile(name, KotlinFileType.INSTANCE, code)

    val Project.psiManager
        get() = PsiManager.getInstance(this)

    fun <T> withProject(f: Project.() -> T): T {
        val parentDisposable = Disposer.newDisposable()
        try {
            val project =
                KotlinCoreEnvironment.createForProduction(
                    parentDisposable,
                    CompilerConfiguration().apply {
                        put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, TestMessageCollector)
                    },
                    EnvironmentConfigFiles.JVM_CONFIG_FILES
                ).project

            return f(project)
        } finally {
            parentDisposable.dispose()
        }
    }

    private
    object TestMessageCollector : MessageCollector {
        override fun clear() = Unit
        override fun hasErrors(): Boolean = false
        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
            println("$severity: $message")
        }
    }
}
