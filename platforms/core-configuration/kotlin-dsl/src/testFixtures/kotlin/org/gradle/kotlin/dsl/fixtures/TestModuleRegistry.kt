/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.internal.classpath.Module
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.classpath.UnknownModuleException
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import java.io.File

class TestModuleRegistry : ModuleRegistry {
    override fun findModule(name: String): Module? {
        return findPathToJar(name)?.let { TestModule(it) }
    }

    override fun getModule(name: String): org.gradle.api.internal.classpath.Module {
        return findModule(name) ?: throw UnknownModuleException("Cannot find module '$name'.")
    }

    private fun findPathToJar(jarName: String): String? {
        return System.getProperty("java.class.path").split(File.pathSeparator).firstOrNull { it.contains(jarName) }
    }
}

private
class TestModule(val pathToJar: String) : org.gradle.api.internal.classpath.Module {
    override fun getName(): String = TODO("Not yet implemented")

    override fun getImplementationClasspath(): ClassPath = DefaultClassPath.of(File(pathToJar))

    override fun getDependencyNames(): List<String> = TODO("Not yet implemented")

    override fun getAlias(): Module.ModuleAlias = TODO("Not yet implemented")
}