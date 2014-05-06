/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.runtime.jvm.plugins
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JvmComponentPluginIntegrationTest extends AbstractIntegrationSpec {
    def "does not create library or binaries when not configured"() {
        when:
        buildFile << """
        apply plugin: 'jvm-component'
        task check << {
            assert jvmLibraries.empty
            assert binaries.empty
        }
"""
        then:
        succeeds "check"

        and:
        !file("build").exists()
    }

    def "creates jvm library and binary model objects and lifecycle task"() {
        when:
        buildFile << """
    apply plugin: 'jvm-component'

    jvmLibraries {
        myLib
    }

    task check << {
        assert jvmLibraries.size() == 1
        def myLib = jvmLibraries.myLib
        assert myLib.name == 'myLib'
        assert myLib == jvmLibraries['myLib']
        assert myLib instanceof JvmLibrary

        assert binaries.size() == 1
        def myLibJar = (binaries as List)[0]
        assert myLibJar instanceof JvmLibraryBinary
        assert myLibJar.name == 'myLibJar'
        assert myLibJar.displayName == "jar 'myLib:jar'"

        def binaryTask = tasks['myLibJar']
        assert binaryTask.group == 'build'
        assert binaryTask.description == "Assembles jar 'myLib:jar'."
        assert myLibJar.lifecycleTask == binaryTask

        def jarTask = tasks['createMyLibJar']
        assert jarTask instanceof Zip
        assert jarTask.group == null
        assert jarTask.description == "Creates the binary file for jar 'myLib:jar'."
    }
"""
        then:
        succeeds "check"

        and:
        !file("build").exists()
    }

    def "skips creating binary when binary has no sources"() {
        given:
        buildFile << """
    apply plugin: 'jvm-component'

    jvmLibraries {
        myJvmLib
    }
"""
        when:
        succeeds "myJvmLibJar"

        then:
        executed ":createMyJvmLibJar", ":myJvmLibJar"

        and:
        !file("build").exists()
    }

    def "can specify additional builder tasks for binary"() {
        given:
        buildFile << """
    apply plugin: 'jvm-component'

    jvmLibraries {
        myJvmLib
    }
    binaries.all { binary ->
        def logTask = project.tasks.create(binary.namingScheme.getTaskName("log")) {
            println "Constructing binary: \${binary.displayName}"
        }
        binary.builtBy(logTask)
    }
"""
        when:
        succeeds "myJvmLibJar"

        then:
        executed ":createMyJvmLibJar", ":logMyJvmLibJar", ":myJvmLibJar"

        and:
        output.contains("Constructing binary: jar 'myJvmLib:jar'")
    }

    def "can define multiple jvm libraries in single project"() {
        when:
        buildFile << """
    apply plugin: 'jvm-component'

    jvmLibraries {
        myLibOne
        myLibTwo
    }

    task check << {
        assert jvmLibraries.size() == 2
        assert jvmLibraries.myLibOne instanceof JvmLibrary
        assert jvmLibraries.myLibTwo instanceof JvmLibrary

        assert binaries.size() == 2
        assert binaries.myLibOneJar.library == jvmLibraries.myLibOne
        assert binaries.myLibTwoJar.library == jvmLibraries.myLibTwo
    }
"""
        then:
        succeeds "check"
    }

    def "can build multiple jvm libraries in single project"() {
        given:
        buildFile << """
    apply plugin: 'jvm-component'

    jvmLibraries {
        myLibOne
        myLibTwo
    }
"""
        when:
        succeeds "myLibOneJar"

        then:
        executed ":createMyLibOneJar", ":myLibOneJar"
        notExecuted ":myLibTwoJar"

        when:
        succeeds "assemble"

        then:
        executed ":createMyLibOneJar", ":myLibOneJar", ":createMyLibTwoJar", ":myLibTwoJar"
    }
}