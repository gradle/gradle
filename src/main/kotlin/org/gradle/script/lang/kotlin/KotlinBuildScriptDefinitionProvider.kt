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

import org.gradle.api.Project

import org.jetbrains.kotlin.script.KotlinConfigurableScriptDefinition
import org.jetbrains.kotlin.script.loadScriptConfigs

import java.io.File
import java.net.URLClassLoader

object KotlinBuildScriptDefinitionProvider {

    val standardBuildScriptDefinition by lazy {
        KotlinConfigurableScriptDefinition(
            loadScriptConfigs(standardBuildScriptConfig.byteInputStream()).first(),
            emptyMap())
    }

    private val standardBuildScriptConfig by lazy {
        """<?xml version="1.0" encoding="UTF-8"?>
        <KotlinScriptDefinitions>
          <script>
            <name>Kotlin build script</name>
            <files>.*\.kts</files>
            <classpath>${collectRequiredClasspath().joinToString("") {
                "\n      <path>$it</path>"
            }}
            </classpath>
            <parameters>
              <scriptParam>
                <name>_project_hidden_</name>
                <type>${Project::class.qualifiedName}</type>
              </scriptParam>
            </parameters>
            <supertypes>
              <type>${KotlinBuildScript::class.qualifiedName}</type>
            </supertypes>
            <superclassParameters>
              <scriptParamToSuperclassParam>
                <scriptParamName>_project_hidden_</scriptParamName>
                <superclassParamType>${Project::class.qualifiedName}</superclassParamType>
              </scriptParamToSuperclassParam>
            </superclassParameters>
          </script>
        </KotlinScriptDefinitions>
        """
    }

    fun collectRequiredClasspath() =
        (KotlinBuildScriptDefinitionProvider::class.java.classLoader as? URLClassLoader)
            ?.urLs
            ?.map { File(it.toURI()).canonicalPath }
            ?.filter {
                it.contains("kotlin-runtime")
                    || it.contains("kotlin-stdlib")
                    || it.contains("kotlin-reflect")
                    || it.contains("gradle-")
            }
            ?: emptyList()
}
