/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.build.plugins

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.gradle.testkit.runner.BuildResult

import java.io.File


abstract class AbstractBuildPluginTest : AbstractIntegrationTest() {

    protected
    fun run(vararg arguments: String): BuildResult =
        gradleRunnerForArguments(*arguments).apply {
            withPluginClasspath(testKitPluginClasspath)
        }.build()
}


private
val testKitPluginClasspath: List<File> by lazy {
    AbstractBuildPluginTest::class.java.classLoader.getResourceAsStream("plugin-classpath.txt")
        .bufferedReader().readLines().map { File(it) }
}
