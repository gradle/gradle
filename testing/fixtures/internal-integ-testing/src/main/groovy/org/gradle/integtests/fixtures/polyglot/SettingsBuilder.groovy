/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.polyglot

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.TestFile

@CompileStatic
class SettingsBuilder implements PolyglotFileGenerator {
    String rootProjectName = 'test-project'
    final List<String> includes = []
    final List<String> includeBuilds = []

    SettingsBuilder include(String... other) {
        includes.addAll(other)
        this
    }

    SettingsBuilder includeBuild(String... other) {
        includeBuilds.addAll(other)
        this
    }

    void generate(GradleDsl dsl, TestFile projectDir) {
        switch (dsl) {
            case GradleDsl.GROOVY:
                new GroovyWriter().writeTo(projectDir.file("settings.gradle"))
                break
            case GradleDsl.KOTLIN:
                new KotlinWriter().writeTo(projectDir.file("settings.gradle.kts"))
                break
        }
    }

    private class GroovyWriter {
        void writeTo(TestFile settingsFile) {
            settingsFile << """
                rootProject.name = '$rootProjectName'
            """
            includes.each { pName ->
                settingsFile << """
                include '$pName'
            """
            }
            includeBuilds.each { pName ->
                settingsFile << """
                includeBuild '$pName'
            """
            }
        }
    }

    private class KotlinWriter {
        void writeTo(TestFile settingsFile) {
            settingsFile << """
                rootProject.name = "$rootProjectName"
            """
            includes.each { pName ->
                settingsFile << """
                include("$pName")
            """
            }
            includeBuilds.each { pName ->
                settingsFile << """
                includeBuild("$pName")
            """
            }
        }
    }
}
