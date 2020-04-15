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
import org.gradle.test.fixtures.archive.JarTestFixture

import java.util.regex.Pattern

abstract class AbstractJvmPluginLanguageIntegrationTest extends AbstractIntegrationSpec {

    abstract String getSourceSetTypeName();

    String getLanguageName() {
        def matcher = Pattern.compile("(\\w+)LanguagePluginIntegrationTest").matcher(getClass().simpleName)
        if (matcher.matches()) {
            return matcher.group(1).toLowerCase()
        }
        throw new UnsupportedOperationException("Cannot determine language name from class name '${getClass().simpleName}.")
    }

    def setup() {
        buildFile << """
        plugins {
            id 'jvm-component'
            id '${languageName}-lang'
        }"""
        executer.expectDocumentedDeprecationWarning("The ${languageName}-lang plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-resources plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
    }

    @ToBeFixedForInstantExecution
    def "creates default source sets"() {
        when:
        buildFile << """

    model {
        components {
            myLib(JvmLibrarySpec)
        }
        tasks {
            validate(Task) {
                def components = \$.components
                def sources = \$.sources
                def binaries = \$.binaries
                doLast {
                    def myLib = components.myLib
                    assert myLib instanceof JvmLibrarySpec

                    assert myLib.sources.size() == 2
                    assert myLib.sources.${languageName} instanceof ${sourceSetTypeName}
                    assert myLib.sources.resources instanceof JvmResourceSet

                    assert sources as Set == myLib.sources as Set

                    binaries.withType(JarBinarySpec).each { jvmBinary ->
                        assert jvmBinary.inputs.toList() == myLib.sources.values().toList()
                    }
                }
            }
        }
    }
"""
        then:
        succeeds "validate"

        and:
        !file("build").exists()
    }

    @ToBeFixedForInstantExecution
    def "can configure additional language source sets for library"() {
        when:
        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec) {
                sources {
                    extra${languageName}(${sourceSetTypeName})
                    extraResources(JvmResourceSet)
                }
            }
        }
        tasks {
            validate(Task) {
                def components = \$.components
                def sources = \$.sources
                def binaries = \$.binaries
                doLast {
                    def myLib = components.myLib
                    assert myLib instanceof JvmLibrarySpec

                    assert myLib.sources.size() == 4
                    assert myLib.sources.${languageName} instanceof ${sourceSetTypeName}
                    assert myLib.sources.extra${languageName} instanceof ${sourceSetTypeName}
                    assert myLib.sources.resources instanceof JvmResourceSet
                    assert myLib.sources.extraResources instanceof JvmResourceSet

                    assert sources as Set == myLib.sources as Set

                    binaries.withType(JarBinarySpec).each { jvmBinary ->
                        assert jvmBinary.inputs.toList() == myLib.sources.values().toList()
                    }
                }
            }
        }
    }
"""
        then:
        succeeds "validate"

        and:
        !file("build").exists()
    }

    def "creates empty jar when library has empty sources"() {
        given:
        file("src/myLib/${languageName}").mkdirs()
        file('src/myLib/resources').mkdirs()

        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec)
        }
    }
"""
        when:
        succeeds "myLibJar"

        then:
        executed ":createMyLibJar", ":myLibJar"

        and:
        def jar = new JarTestFixture(file("build/jars/myLib/jar/myLib.jar"))
        jar.hasDescendants()
    }

    @ToBeFixedForInstantExecution(because = ":components")
    def "source sets and locations are visible in the components report"() {
        when:
        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec){
                sources {
                    extra${languageName}(${sourceSetTypeName})
                    extraResources(JvmResourceSet)
                }
            }
        }
    }
"""
        then:
        succeeds "components"

        and:
        output.contains """
    JVM resources 'myLib:extraResources'
        srcDir: src${File.separator}myLib${File.separator}extraResources"""

        output.contains """
    ${StringUtils.capitalize(languageName)} source 'myLib:extra${languageName}'
        srcDir: src${File.separator}myLib${File.separator}extra${languageName}"""

        output.contains """
    JVM resources 'myLib:resources'
        srcDir: src${File.separator}myLib${File.separator}resources"""

        output.contains """
    ${StringUtils.capitalize(languageName)} source 'myLib:${languageName}'
        srcDir: src${File.separator}myLib${File.separator}${languageName}"""
    }

}
