/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.compile.daemon

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

class ParallelCompilerDaemonIntegrationTest extends AbstractIntegrationSpec {
    @Rule TestResources resources = new TestResources(temporaryFolder)

    def "daemon compiler can handle --parallel"() {
        generateProjects(10, 10, 10)

        args("--parallel")

        expect:
        succeeds("classes")
    }

    private generateProjects(int numProjects, int numJavaSources, int numGroovySources) {
        def settingsText = ""
        numProjects.times { count ->
            settingsText += "include 'project$count'\n"
        }
        resources.dir.file("settings.gradle") << settingsText

        numProjects.times { count ->
            def projectDir = resources.dir.createDir("project$count")
            generateSources(numJavaSources, resources.dir.file("JavaClass.java"), new File(projectDir, "src/main/java"))
            generateSources(numGroovySources, resources.dir.file("GroovyClass.groovy"), new File(projectDir, "src/main/groovy"))
        }
    }

    private generateSources(int num, File originalFile, File destinationDir) {
        def text = originalFile.text
        def className = originalFile.name.substring(0, originalFile.name.lastIndexOf("."))
        def extension = originalFile.name.substring(originalFile.name.lastIndexOf(".") + 1)

        num.times { count ->
            def newClassName = className + count
            def newText = text.replace(className, newClassName)
            def file = new File(destinationDir, "${newClassName}.${extension}")
            file.parentFile.mkdirs()
            file << newText
        }
    }
}
