/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.extensibility


import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.util.internal.GroovyDependencyUtil

@TargetCoverage({ GroovyCoverage.SINCE_4_0 })
class DeprecatedBooleanPropertyMatchesGroovy4IntegrationTest extends MultiVersionIntegrationSpec {
    def "warns when Groovy 4 will not support the property (boolType=#boolType,name=#name)"() {
        boolean shouldWarn = name == "isProperty" && boolType == "Boolean"
        String propertyDeclaration = "$boolType $name() { return Boolean.TRUE }"
        String propertyAssertion = "assert myext.property"
        buildFile << """
            plugins {
                id 'groovy'
                id 'application'
            }

            abstract class MyExtension {
                $propertyDeclaration
            }
            def myext = extensions.create("myext", MyExtension)
            task assertProperty {
                doLast {
                    $propertyAssertion
                }
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation '${GroovyDependencyUtil.groovyModuleDependency("groovy", version.toString())}'
            }

            application {
                mainClass = 'Main'
            }
        """

        file("src/main/groovy/Main.groovy") << """
            class Main {
                static void main(String[] args) {
                    def myext = new Main()
                    $propertyAssertion
                }

                $propertyDeclaration
            }
        """

        expect:
        if (shouldWarn) {
            executer.expectDocumentedDeprecationWarning("Declaring an 'is-' property with a Boolean type has been deprecated. Starting with Gradle 9.0, this property will be ignored by Gradle. " +
                "The combination of method name and return type is not consistent with Java Bean property rules and will become unsupported in future versions of Groovy. " +
                "Add a method named 'getProperty' with the same behavior and mark the old one with @Deprecated, or change the type of 'MyExtension.isProperty' (and the setter) to 'boolean'. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#groovy_boolean_properties")
        }
        succeeds("assertProperty")
        if (shouldWarn) {
            executer.withStackTraceChecksDisabled()
            fails("run")
        } else {
            succeeds("run")
        }

        where:
        boolType  | name
        "Boolean" | "getProperty"
        "Boolean" | "isProperty"
        "boolean" | "getProperty"
        "boolean" | "isProperty"
    }
}
