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
import org.gradle.language.fixtures.TestJavaLibrary
import org.gradle.test.fixtures.archive.JarTestFixture

class JavaLanguagePluginIntegrationTest extends AbstractIntegrationSpec {
    def app = new TestJavaLibrary()

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
        assert sources.size() == 1
        assert sources.myLib instanceof FunctionalSourceSet
        assert sources.myLib.size() == 2
        assert sources.myLib.java instanceof JavaSourceSet
        assert sources.myLib.resources instanceof ResourceSet

        def myLib = jvm.libraries.myLib
        assert myLib instanceof JvmLibrary
        assert myLib.source as Set == [sources.myLib.java, sources.myLib.resources] as Set

        binaries.withType(JvmLibraryBinary) { jvmBinary ->
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

    sources {
        myLib {
            extraJava(JavaSourceSet)
            extraResources(ResourceSet)
        }
    }

    jvm {
        libraries {
            myLib
        }
    }

    task check << {
        assert sources.size() == 1
        assert sources.myLib instanceof FunctionalSourceSet
        assert sources.myLib.size() == 4
        assert sources.myLib.java instanceof JavaSourceSet
        assert sources.myLib.extraJava instanceof JavaSourceSet
        assert sources.myLib.resources instanceof ResourceSet
        assert sources.myLib.extraResources instanceof ResourceSet

        def myLib = jvm.libraries.myLib
        assert myLib instanceof JvmLibrary
        assert myLib.source as Set == [sources.myLib.java, sources.myLib.extraJava, sources.myLib.resources, sources.myLib.extraResources] as Set

        binaries.withType(JvmLibraryBinary) { jvmBinary ->
            assert jvmBinary.source == myLib.source
        }
    }
"""
        then:
        succeeds "check"

        and:
        !file("build").exists()
    }

    def "can configure additional functional source set for java library"() {
        when:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    sources {
        myExtraSources
    }

    jvm {
        libraries {
            myLib {
                source sources.myExtraSources
            }
        }
    }

    task check << {
        assert sources.size() == 2

        [sources.myLib, sources.myExtraSources].each {
            assert it instanceof FunctionalSourceSet
            assert it.size() == 2
            assert it.java instanceof JavaSourceSet
            assert it.resources instanceof ResourceSet
        }

        def myLib = jvm.libraries.myLib
        assert myLib instanceof JvmLibrary
        assert myLib.source as Set == [sources.myLib.java, sources.myExtraSources.java, sources.myLib.resources, sources.myExtraSources.resources] as Set

        binaries.withType(JvmLibraryBinary) { jvmBinary ->
            assert jvmBinary.source == myLib.source
        }
    }
"""
        then:
        succeeds "check"

        and:
        !file("build").exists()
    }

    def "generated binary includes resources from all resource sets"() {
        when:
        def resource1 = app.resources[0]
        def resource2 = app.resources[1]

        resource1.writeToDir(file("src/myLib/resources"))
        resource2.writeToDir(file("src/myLib/extraResources"))

        // TODO:DAZ Need to configure the default source locations (move out of Native)
        // Will currently have different behaviour if native-component plugin is applied!
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    sources {
        myLib {
            resources {
                source.srcDir "src/myLib/resources"
            }
            extraResources(ResourceSet) {
                source.srcDir "src/myLib/extraResources"
            }
        }
    }

    jvm {
        libraries {
            myLib
        }
    }
"""
        and:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":processMyLibJarMyLibResources", ":processMyLibJarMyLibExtraResources", ":createMyLibJar", ":myLibJar"

        and:
        file("build/classes/myLibJar").assertHasDescendants(resource1.fullPath, resource2.fullPath)

        and:
        def jar = jarFile("build/jars/myLibJar/myLib.jar")
        jar.hasDescendants(resource1.fullPath, resource2.fullPath)
        jar.assertFileContent(resource1.fullPath, resource1.content)
        jar.assertFileContent(resource2.fullPath, resource2.content)
    }

    def "generated binary includes compiled classes from all java source sets"() {
        when:
        def source1 = app.sources[0]
        def source2 = app.sources[1]

        source1.writeToDir(file("src/myLib/java"))
        source2.writeToDir(file("src/myLib/extraJava"))

        // TODO:DAZ Need to configure the default source locations (move out of Native)
        // Will currently have different behaviour if native-component plugin is applied!
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    sources {
        myLib {
            java {
                source.srcDir "src/myLib/java"
            }
            extraJava(JavaSourceSet) {
                source.srcDir "src/myLib/extraJava"
            }
        }
    }

    jvm {
        libraries {
            myLib
        }
    }
"""
        and:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":compileMyLibJarMyLibJava", ":compileMyLibJarMyLibExtraJava", ":createMyLibJar", ":myLibJar"

        and:
        file("build/classes/myLibJar").assertHasDescendants(source1.classFile.fullPath, source2.classFile.fullPath)

        and:
        def jar = jarFile("build/jars/myLibJar/myLib.jar")
        jar.hasDescendants(source1.classFile.fullPath, source2.classFile.fullPath)
    }

    private JarTestFixture jarFile(String s) {
        new JarTestFixture(file(s))
    }
}