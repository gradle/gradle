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

package org.gradle.integtests.language

import org.apache.commons.lang.StringUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.internal.SystemProperties
import org.gradle.test.fixtures.archive.JarTestFixture

abstract class AbstractJvmLanguageIntegrationTest extends AbstractIntegrationSpec {

    abstract TestJvmComponent getApp()

    def setup() {
        buildFile << """
        plugins {
            id 'jvm-component'
            id '${app.languageName}-lang'
        }
        ${mavenCentralRepository()}
    """
        expectDeprecationWarnings()
    }

    void expectDeprecationWarnings() {
        executer.expectDocumentedDeprecationWarning("The ${app.languageName}-lang plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-resources plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
    }

    @ToBeFixedForInstantExecution(bottomSpecs = ["JavaLanguageIntegrationTest", "JvmApiSpecIntegrationTest"])
    def "can build binary with sources in conventional location"() {
        when:
        app.writeSources(file("src/myLib"))
        app.writeResources(file("src/myLib/resources"))
        def expectedClasses = app.expectedClasses*.fullPath as String[]
        def expectedResources = app.resources*.fullPath as String[]
        def expectedOutputs = expectedClasses + expectedResources as String[]

        and:
        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec)
        }
    }

"""
        and:
        succeeds "createMyLibJar"

        then:
        executedAndNotSkipped ":processMyLibJarMyLibResources", ":compileMyLibJarMyLib${StringUtils.capitalize(app.languageName)}", ":createMyLibJar"

        and:
        file("build/classes/myLib/jar").assertHasDescendants(expectedClasses)
        file("build/resources/myLib/jar").assertHasDescendants(expectedResources)
        jarFile("build/jars/myLib/jar/myLib.jar").hasDescendants(expectedOutputs)
    }

    @ToBeFixedForInstantExecution(bottomSpecs = ["JavaLanguageIntegrationTest", "JvmApiSpecIntegrationTest"])
    def "generated binary includes compiled classes from all language source sets"() {
        setup:
        def extraSourceSetName = "extra${app.languageName}"

        when:
        def source1 = app.sources[0]
        def source2 = app.sources[1]

        source1.writeToDir(file("src/myLib/${app.languageName}"))
        source2.writeToDir(file("src/myLib/$extraSourceSetName"))

        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec) {
                sources {
                    $extraSourceSetName(${app.sourceSetTypeName})
                }
            }
        }
    }
"""
        and:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":compileMyLibJarMyLib${StringUtils.capitalize(app.languageName)}", ":compileMyLibJarMyLib${StringUtils.capitalize(extraSourceSetName)}", ":createMyLibJar", ":myLibJar"

        and:
        file("build/classes/myLib/jar").assertHasDescendants(source1.classFile.fullPath, source2.classFile.fullPath)

        and:
        def jar = jarFile("build/jars/myLib/jar/myLib.jar")
        jar.hasDescendants(source1.classFile.fullPath, source2.classFile.fullPath)
    }

    @ToBeFixedForInstantExecution(bottomSpecs = ["JavaLanguageIntegrationTest", "JvmApiSpecIntegrationTest"])
    def "can configure source locations for language and resource source sets"() {
        setup:
        def customSourceSetName = "my${app.languageName}"
        app.writeSources(file("src/myLib"), customSourceSetName)
        app.writeResources(file("src/myLib/myResources"))

        // Conventional locations are ignore with explicit configuration
        file("src/myLib/${app.languageName}/Ignored.${app.languageName}") << "IGNORE ME"
        file("src/myLib/resources/Ignored.txt") << "IGNORE ME"

        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec) {
                sources {
                    ${app.languageName} {
                        source.srcDir "src/myLib/$customSourceSetName"
                    }
                    resources {
                        source.srcDir "src/myLib/myResources"
                    }
                }
            }
        }
    }
"""
        when:
        succeeds "assemble"

        then:
        file("build/classes/myLib/jar").assertHasDescendants(app.expectedClasses*.fullPath as String[])
        file("build/resources/myLib/jar").assertHasDescendants(app.resources*.fullPath as String[])
        jarFile("build/jars/myLib/jar/myLib.jar").hasDescendants(app.expectedOutputs*.fullPath as String[])
    }

    @ToBeFixedForInstantExecution(bottomSpecs = ["JavaLanguageIntegrationTest", "JvmApiSpecIntegrationTest"])
    def "can combine resources and sources in a single source directory"() {
        when:
        app.writeSources(file("src/myLib"))
        app.writeResources(file("src/myLib"))

        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec) {
                sources {
                    ${app.languageName}.source {
                        srcDir "src/myLib"
                        exclude "**/*.txt"
                    }
                    resources.source {
                        srcDir "src/myLib"
                        ${excludeStatementFor(app.sourceFileExtensions)}
                    }
                }
            }
        }
    }
"""
        and:
        succeeds "assemble"

        then:
        file("build/classes/myLib/jar").assertHasDescendants(app.expectedClasses*.fullPath as String[])
        file("build/resources/myLib/jar").assertHasDescendants(app.resources*.fullPath as String[])
        jarFile("build/jars/myLib/jar/myLib.jar").hasDescendants(app.expectedOutputs*.fullPath as String[])
    }

    def excludeStatementFor(List<String> fileExtensions) {
        fileExtensions.collect { "exclude '**/*.${it}'" }.join(SystemProperties.instance.lineSeparator)
    }

    @ToBeFixedForInstantExecution(bottomSpecs = ["JavaLanguageIntegrationTest", "JvmApiSpecIntegrationTest"])
    def "can configure output directories for classes and resources"() {
        when:
        app.writeSources(file("src/myLib"))
        app.writeResources(file("src/myLib/resources"))
        def expectedOutputs = app.expectedOutputs*.fullPath as String[]

        and:
        buildFile << '''
    model {
        components {
            myLib(JvmLibrarySpec) {
                binaries {
                    all {
                        classesDir = new File($("buildDir"), "custom-classes")
                        resourcesDir = new File($("buildDir"), "custom-resources")
                    }
                }
            }
        }
    }
'''
        and:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":processMyLibJarMyLibResources", ":compileMyLibJarMyLib${StringUtils.capitalize(app.languageName)}", ":createMyLibJar", ":myLibJar"

        and:
        file("build/custom-classes").assertHasDescendants(app.sources*.classFile.fullPath as String[])
        file("build/custom-resources").assertHasDescendants(app.resources*.fullPath as String[])

        and:
        jarFile("build/jars/myLib/jar/myLib.jar").hasDescendants(expectedOutputs)
    }

    protected JarTestFixture jarFile(String s) {
        new JarTestFixture(file(s))
    }

}
