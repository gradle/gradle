/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugins.strictcompile

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile

import org.gradle.kotlin.dsl.*


/**
 * Strict compilation plugin.
 */
@Suppress("unused")
open class StrictCompilePlugin : Plugin<Project> {

    private
    val strictCompilerArgs = listOf("-Werror", "-Xlint:all", "-Xlint:-options", "-Xlint:-serial")

    private
    val ignoreDeprecationsArg = "-Xlint:-deprecation"

    override fun apply(project: Project): Unit = project.run {

        val strictCompile = extensions.create("strictCompile", StrictCompileExtension::class.java)

        afterEvaluate {
            val compilerArgs =
                if (strictCompile.ignoreDeprecations) strictCompilerArgs + ignoreDeprecationsArg
                else strictCompilerArgs
            tasks.withType<JavaCompile> {
                options.compilerArgs.addAll(compilerArgs)
            }
            tasks.withType<GroovyCompile> {
                options.compilerArgs.addAll(compilerArgs)
            }
        }
    }
}
