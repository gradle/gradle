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
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import java.io.File

class TestModuleRegistry : ModuleRegistry { // TODO: this works, but might not be the best thing to do...
    override fun findModule(name: String): org.gradle.api.internal.classpath.Module {
        return TestModule(name)
    }

    override fun getModule(name: String): org.gradle.api.internal.classpath.Module {
        TODO("Not yet implemented")
    }
}

private
class TestModule(val jarName: String) : org.gradle.api.internal.classpath.Module {
    override fun getName(): String {
        TODO("Not yet implemented")
    }

    override fun getImplementationClasspath(): ClassPath {
        val pathToJar = System.getProperty("java.class.path").split(File.pathSeparator).firstOrNull { it.contains(jarName) }
        if (pathToJar != null) {
            return DefaultClassPath.of(File(pathToJar))
        }
        throw RuntimeException("$jarName.jar not found on the classpath!")
    }

    override fun getDependencyNames(): List<String> {
        TODO("Not yet implemented")
    }

    override fun getAlias(): Module.ModuleAlias {
        TODO("Not yet implemented")
    }
}