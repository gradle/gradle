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

package org.gradle.language.jvm.plugins
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JvmComponentPluginIntegrationTest extends AbstractIntegrationSpec {
    def "does not create library or binaries when not configured"() {
        when:
        buildFile << """
        apply plugin: 'jvm-component'
        task check << {
            assert libraries.empty
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

    libraries {
        myLib(JvmLibrary)
    }

    task check << {
        assert libraries.size() == 1
        def myLib = libraries.myLib
        assert myLib.name == 'myLib'
        assert myLib == libraries['myLib']
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
    }
"""
        then:
        succeeds "check"

        and:
        !file("build").exists()
    }
}