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

package org.gradle.language.java.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture

class JavaLanguagePluginIntegrationTest extends AbstractIntegrationSpec {

    def "creates default java source sets"() {
        when:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    jvm {
        libraries {
            myLib
        }
    }

    task check << {
        def myLib = jvm.libraries.myLib
        assert myLib instanceof JvmLibrarySpec

        assert myLib.sources.size() == 2
        assert myLib.sources.java instanceof JavaSourceSet
        assert myLib.sources.resources instanceof JvmResourceSet
        assert myLib.sources as Set == [sources.myLib.java, sources.myLib.resources] as Set

        assert sources.myLib == myLib.sources

        binaries.withType(JarBinarySpec) { jvmBinary ->
            assert jvmBinary.source == myLib.source
        }
    }
"""
        then:
        succeeds "check"

        and:
        !file("build").exists()
    }

    def "can configure additional language source sets for java library"() {
        when:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    jvm {
        libraries {
            myLib {
                sources {
                    extraJava(JavaSourceSet)
                    extraResources(JvmResourceSet)
                }
            }
        }
    }

    task check << {
        def myLib = jvm.libraries.myLib
        assert myLib instanceof JvmLibrarySpec

        assert myLib.sources == sources.myLib
        assert myLib.sources.size() == 4
        assert myLib.sources.java instanceof JavaSourceSet
        assert myLib.sources.extraJava instanceof JavaSourceSet
        assert myLib.sources.resources instanceof JvmResourceSet
        assert myLib.sources.extraResources instanceof JvmResourceSet

        binaries.withType(JarBinarySpec) { jvmBinary ->
            assert jvmBinary.source == myLib.source
        }
    }
"""
        then:
        succeeds "check"

        and:
        !file("build").exists()
    }

    def "creates empty jar when library has empty sources"() {
        given:
        file('src/myLib/java').mkdirs()
        file('src/myLib/resources').mkdirs()

        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    jvm {
        libraries {
            myLib
        }
    }
"""
        when:
        succeeds "myLibJar"

        then:
        executed ":createMyLibJar", ":myLibJar"

        and:
        def jar = new JarTestFixture(file("build/jars/myLibJar/myLib.jar"))
        jar.hasDescendants()
    }
}