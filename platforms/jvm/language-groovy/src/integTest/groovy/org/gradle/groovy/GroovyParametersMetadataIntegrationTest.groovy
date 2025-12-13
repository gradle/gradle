/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.groovy

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.testing.fixture.GroovyCoverage
import org.junit.Assume
import spock.lang.Issue

import static org.gradle.util.internal.GroovyDependencyUtil.groovyModuleDependency

@TargetCoverage({ GroovyCoverage.SUPPORTS_PARAMETERS })
class GroovyParametersMetadataIntegrationTest extends MultiVersionIntegrationSpec implements JavaToolchainFixture {
    Jvm supportedJvm

    def setup() {
        supportedJvm = GroovyCoverage.ALL_VERSIONS_JVMS[version]
        Assume.assumeNotNull(supportedJvm)

        executer.beforeExecute {
            withInstallations(supportedJvm)
        }

        buildFile << """
            plugins {
                id("groovy")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation "${groovyModuleDependency("groovy", versionNumber)}"
            }

            ${javaPluginToolchainVersion(supportedJvm)}
        """
    }

    @Issue('gradle/gradle#2487')
    def "classes compiled with parameters option must contain metadata about method parameters on jdk8 and above"() {
        given:
        // We do this so that we can load and inspect the compiled class after compilation
        def targetCompatibility = supportedJvm.javaVersion.majorVersion > JavaVersion.current().majorVersion ? "targetCompatibility = ${JavaVersion.current().majorVersion}" : ""

        buildFile << """
            apply plugin: "groovy"
            ${mavenCentralRepository()}
            compileGroovy {
                groovyOptions.parameters = true
                ${targetCompatibility}
            }
        """.stripIndent()

        and: 'prepare class with method'
        file("src/main/groovy/compile/test/Person.groovy") << """
            package parameters.test
            class Person {
                void setFullName(String fullName) {
                    // no-op
                }
            }
        """.stripIndent()

        expect:
        succeeds 'compileGroovy'
        def compiled = groovyClassFile("parameters/test/Person.class")
        compiled.exists()

        and:
        def compiledDir = compiled.parentFile.parentFile.parentFile.toURI().toURL()
        def compiledClassLoader = new URLClassLoader([compiledDir] as URL[])
        def loadedClass = compiledClassLoader.loadClass('parameters.test.Person')
        def methodParameters = loadedClass.methods
            .find { 'setFullName' == it.name }
            .parameters
        methodParameters.size() == 1
        methodParameters[0].name == 'fullName'
    }
}
