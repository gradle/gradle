/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.java.compile.incremental


import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.integtests.fixtures.CompiledLanguage

abstract class AbstractCrossTaskIncrementalCompilationSupport extends AbstractJavaGroovyIncrementalCompilationSupport {
    CompilationOutputsFixture impl

    def setup() {
        impl = new CompilationOutputsFixture(file("impl/build/classes"))
        settingsFile """
            include "api", "impl"
            dependencyResolutionManagement {
                ${mavenCentralRepository()}
            }
            gradle.lifecycle.beforeProject {
                if (path != ':') {
                    apply plugin: '${language.name}'
                    apply plugin: 'java-library'
                    configurations.compileClasspath.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.${useJar ? 'JAR' : 'CLASSES'}))
                }
            }
        """
        createDirs("api", "impl")
        addDependency("impl", "api")

        if (language == CompiledLanguage.GROOVY) {
            configureGroovyIncrementalCompilationForSubprojects()
        }
    }

    protected void addDependency(String from, String to) {
        buildFile("$from/build.gradle", """
            dependencies { api project(':$to') }
        """)
    }

    protected abstract boolean isUseJar()

    protected void clearImplProjectDependencies() {
        buildFile("impl/build.gradle", """
            configurations.api.dependencies.clear() //so that api jar is no longer on classpath
        """)
    }

    File source(Map projectToClassBodies) {
        File out = null
        projectToClassBodies.each { project, bodies ->
            if (bodies instanceof String || bodies instanceof GString) {
                out = sourceForProject(project as String, bodies as String)
            } else {
                out = sourceForProject(project as String, bodies as String[])
            }
        }
        out
    }
}
