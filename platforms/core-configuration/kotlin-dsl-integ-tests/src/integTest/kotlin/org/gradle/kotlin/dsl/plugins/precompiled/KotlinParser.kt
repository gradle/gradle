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

import org.jetbrains.kotlin.cli.jvm.compiler.IdeaStandaloneExecutionSetup
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironmentMode
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile


object KotlinParser {

    fun <T> map(code: String, f: KtFile.() -> T): T =
        withPsiManager { f(parse("code.kt", code)) }

    fun PsiManager.parse(name: String, code: String): KtFile =
        findFile(virtualFile(name, code)) as KtFile

    fun virtualFile(name: String, code: String) =
        LightVirtualFile(name, KotlinFileType.INSTANCE, code)

    fun <T> withPsiManager(f: PsiManager.() -> T): T {
        val disposable = Disposer.newDisposable()
        try {
            IdeaStandaloneExecutionSetup.doSetup()
            val applicationEnvironment = KotlinCoreApplicationEnvironment.create(disposable, KotlinCoreApplicationEnvironmentMode.UnitTest)
            applicationEnvironment.registerParserDefinition(KotlinParserDefinition())
            val projectEnvironment = KotlinCoreProjectEnvironment(disposable, applicationEnvironment)
            val manager = PsiManager.getInstance(projectEnvironment.project)
            return f(manager)
        } finally {
            disposable.dispose()
        }
    }
}
