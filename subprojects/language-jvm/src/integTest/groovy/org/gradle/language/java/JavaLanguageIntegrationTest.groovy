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

package org.gradle.language.java
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.language.fixtures.BadJavaLibrary
import org.gradle.language.fixtures.TestJavaLibrary
import org.gradle.test.fixtures.archive.JarTestFixture

class JavaLanguageIntegrationTest extends AbstractIntegrationSpec {
    def app = new TestJavaLibrary()

    def "can build binary with sources in conventional location"() {
        when:
        app.sources*.writeToDir(file("src/myLib/java"))
        app.resources*.writeToDir(file("src/myLib/resources"))
        def expectedOutputs = app.expectedOutputs*.fullPath as String[]

        and:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    jvm {
        libraries {
            myLib
        }
    }
"""
        and:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":processMyLibJarMyLibResources", ":compileMyLibJarMyLibJava", ":createMyLibJar", ":myLibJar"

        and:
        file("build/classes/myLibJar").assertHasDescendants(expectedOutputs)
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(expectedOutputs)
    }

    def "generated binary includes resources from all resource sets"() {
        when:
        def resource1 = app.resources[0]
        def resource2 = app.resources[1]

        resource1.writeToDir(file("src/myLib/resources"))
        resource2.writeToDir(file("src/myLib/extraResources"))

        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    sources {
        myLib {
            extraResources(ResourceSet)
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

        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    sources {
        myLib {
            extraJava(JavaSourceSet)
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

    def "can configure source locations for java and resource source sets"() {
        when:
        app.sources*.writeToDir(file("src/myLib/myJava"))
        app.resources*.writeToDir(file("src/myLib/myResources"))

        // Conventional locations are ignore with explicit configuration
        file("src/myLib/java/Ignored.java") << "IGNORE ME"
        file("src/myLib/resources/Ignored.txt") << "IGNORE ME"

        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    sources {
        myLib {
            java {
                source.srcDir "src/myLib/myJava"
            }
            resources {
                source.srcDir "src/myLib/myResources"
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
        file("build/classes/myLibJar").assertHasDescendants(app.expectedOutputs*.fullPath as String[])
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(app.expectedOutputs*.fullPath as String[])
    }

    def "can combine resources and sources in a single source directory"() {
        when:
        app.sources*.writeToDir(file("src/myLib"))
        app.resources*.writeToDir(file("src/myLib"))
        String[] expectedOutputs = [app.sources[0].classFile.fullPath, app.sources[1].classFile.fullPath, app.resources[0].fullPath, app.resources[1].fullPath]

        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    sources {
        myLib {
            java {
                source.srcDir "src/myLib"
            }
            resources.source {
                srcDir "src/myLib"
                exclude "**/*.java"
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
        file("build/classes/myLibJar").assertHasDescendants(expectedOutputs)
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(expectedOutputs)
    }

    def "can configure output directories for classes and resources"() {
        when:
        app.sources*.writeToDir(file("src/myLib/java"))
        app.resources*.writeToDir(file("src/myLib/resources"))
        def expectedOutputs = app.expectedOutputs*.fullPath as String[]

        and:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    jvm {
        libraries {
            myLib
        }
    }
    binaries.withType(JarBinarySpec) {
        classesDir = file("\${project.buildDir}/custom-classes")
        resourcesDir = file("\${project.buildDir}/custom-resources")
    }
"""
        and:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":processMyLibJarMyLibResources", ":compileMyLibJarMyLibJava", ":createMyLibJar", ":myLibJar"

        and:
        file("build/custom-classes").assertHasDescendants(app.sources*.classFile.fullPath as String[])
        file("build/custom-resources").assertHasDescendants(app.resources*.fullPath as String[])

        and:
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(expectedOutputs)
    }

    def "creates empty jar when library has no sources"() {
        given:
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

    def "reports failure to compile bad java sources"() {
        when:
        def badApp = new BadJavaLibrary()
        badApp.sources*.writeToDir(file("src/myLib/java"))

        and:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    jvm {
        libraries {
            myLib
        }
    }
"""
        then:
        fails "assemble"

        and:
        badApp.compilerErrors.each {
            assert errorOutput.contains(it)
        }
    }

    private JarTestFixture jarFile(String s) {
        new JarTestFixture(file(s))
    }
}