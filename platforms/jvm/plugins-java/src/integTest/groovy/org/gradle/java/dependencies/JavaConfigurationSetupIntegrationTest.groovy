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

package org.gradle.java.dependencies

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.component.ResolutionFailureHandler

class JavaConfigurationSetupIntegrationTest extends AbstractIntegrationSpec {

    static final VALID = "_VALID"
    static final FORBIDDEN = "_FORBIDDEN"
    static final DOES_NOT_EXIST = "_DOES_NOT_EXIST"

    static forbidden(String marker) {
        marker == FORBIDDEN
    }

    static doesNotExist(String marker) {
        marker == DOES_NOT_EXIST
    }

    static valid(String marker) {
        marker == VALID
    }

    static deprecated(String alternatives) {
        !(alternatives in [VALID, FORBIDDEN, DOES_NOT_EXIST])
    }

    def "the #configuration configuration is setup correctly for dependency declaration in the #plugin plugin"() {
        given:
        buildFile << """
            plugins { id '$plugin' }
            dependencies {
                $configuration 'some:module:1.0'
            }
        """
        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"

        when:
        if (deprecated(alternatives)) {
            executer.expectDeprecationWarning()
        }
        if (forbidden(alternatives) || doesNotExist(alternatives)) {
            fails 'help'
        } else {
            succeeds 'help'
        }

        then:
        !deprecated(alternatives)   || output.contains("The $configuration configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 8.0. Please use the $alternatives configuration instead.")
        !valid(alternatives)        || !output.contains("> Configure project :")
        !doesNotExist(alternatives) || errorOutput.contains("Could not find method $configuration() for arguments [some:module:1.0] on object of type org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler")
        !forbidden(alternatives)    || failure.hasErrorOutput("Dependencies can not be declared against the `$configuration` configuration.")

        where:
        plugin         | configuration                  | alternatives

        'java'         | 'compileOnly'                  | VALID
        'java'         | 'runtimeOnly'                  | VALID
        'java'         | 'implementation'               | VALID
        'java'         | 'apiElements'                  | FORBIDDEN
        'java'         | 'runtimeElements'              | FORBIDDEN
        'java'         | 'compileClasspath'             | FORBIDDEN
        'java'         | 'runtimeClasspath'             | FORBIDDEN
        'java'         | 'annotationProcessor'          | VALID
        'java'         | 'api'                          | DOES_NOT_EXIST
        'java'         | 'compileOnlyApi'               | DOES_NOT_EXIST

        'java-library' | 'compileOnly'                  | VALID
        'java-library' | 'runtimeOnly'                  | VALID
        'java-library' | 'implementation'               | VALID
        'java-library' | 'apiElements'                  | FORBIDDEN
        'java-library' | 'runtimeElements'              | FORBIDDEN
        'java-library' | 'compileClasspath'             | FORBIDDEN
        'java-library' | 'runtimeClasspath'             | FORBIDDEN
        'java-library' | 'annotationProcessor'          | VALID
        'java-library' | 'api'                          | VALID
        'java-library' | 'compileOnlyApi'               | VALID
    }

    def "the #configuration configuration is setup correctly for consumption in the #plugin plugin"() {
        when:
        createDirs("sub")
        settingsFile.text = "include 'sub'"
        buildFile.text = """
            project(':sub') { apply plugin: '$plugin' }
            configurations { root { attributes.attribute Attribute.of('artifactType', String), 'jar' } }
            dependencies {
                root project(path: ':sub', configuration: '$configuration')
            }
            task resolve {
                inputs.files(configurations.root)
                doLast {
                }
            }
        """
        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"

        if (deprecated(alternatives)) {
            executer.expectDeprecationWarning()
        }
        if (forbidden(alternatives) || doesNotExist(alternatives)) {
            fails 'resolve'
        } else {
            succeeds 'resolve'
        }

        then:
        !deprecated(alternatives)   || output.contains("The $configuration configuration has been deprecated for consumption. This will fail with an error in Gradle 8.0. Please use attributes to consume the ${alternatives} configuration instead.")
        !valid(alternatives)        || output.contains("> Task :resolve\n\n")
        !forbidden(alternatives)    || errorOutput.contains("Selected configuration '$configuration' on 'project :sub' but it can't be used as a project dependency because it isn't intended for consumption by other components.")
        !doesNotExist(alternatives) || errorOutput.contains("A dependency was declared on configuration '$configuration' which is not declared in the descriptor for project :sub.")

        where:
        plugin         | configuration                  | alternatives

        'java'         | 'compileOnly'                  | FORBIDDEN
        'java'         | 'runtimeOnly'                  | FORBIDDEN
        'java'         | 'implementation'               | FORBIDDEN
        'java'         | 'runtimeElements'              | VALID
        'java'         | 'apiElements'                  | VALID
        'java'         | 'compileClasspath'             | FORBIDDEN
        'java'         | 'runtimeClasspath'             | FORBIDDEN
        'java'         | 'annotationProcessor'          | FORBIDDEN
        'java'         | 'api'                          | DOES_NOT_EXIST
        'java'         | 'compileOnlyApi'               | DOES_NOT_EXIST

        'java-library' | 'compileOnly'                  | FORBIDDEN
        'java-library' | 'runtimeOnly'                  | FORBIDDEN
        'java-library' | 'implementation'               | FORBIDDEN
        'java-library' | 'runtimeElements'              | VALID
        'java-library' | 'apiElements'                  | VALID
        'java-library' | 'compileClasspath'             | FORBIDDEN
        'java-library' | 'runtimeClasspath'             | FORBIDDEN
        'java-library' | 'annotationProcessor'          | FORBIDDEN
        'java-library' | 'api'                          | FORBIDDEN
        'java-library' | 'compileOnlyApi'               | FORBIDDEN
    }

    def "the #configuration configuration is setup correctly for resolution in the #plugin plugin"() {
        given:
        buildFile << """
            plugins { id '$plugin' }
            task resolve {
                def conf = configurations.${configuration}
                doLast {
                    conf.files
                }
            }
        """
        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"

        when:
        executer.withStacktraceEnabled()
        if (deprecated(alternatives)) {
            executer.expectDeprecationWarning()
        }
        if (forbidden(alternatives) || doesNotExist(alternatives)) {
            fails 'resolve'
        } else {
            succeeds 'resolve'
        }

        then:
        !deprecated(alternatives)   || output.contains("The $configuration configuration has been deprecated for resolution. This will fail with an error in Gradle 8.0. Please resolve the ${alternatives} configuration instead.")
        !valid(alternatives)        || output.contains("> Task :resolve\n\n")
        !forbidden(alternatives)    || errorOutput.contains("Resolving dependency configuration '$configuration' is not allowed as it is defined as 'canBeResolved=false'.\nInstead, a resolvable ('canBeResolved=true') dependency configuration that extends '$configuration' should be resolved.")
        !doesNotExist(alternatives) || errorOutput.contains("Could not get unknown property '$configuration' for configuration container of type org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer.")

        where:
        plugin         | configuration                  | alternatives

        'java'         | 'compileOnly'                  | FORBIDDEN
        'java'         | 'runtimeOnly'                  | FORBIDDEN
        'java'         | 'implementation'               | FORBIDDEN
        'java'         | 'runtimeElements'              | FORBIDDEN
        'java'         | 'apiElements'                  | FORBIDDEN
        'java'         | 'compileClasspath'             | VALID
        'java'         | 'runtimeClasspath'             | VALID
        'java'         | 'annotationProcessor'          | VALID
        'java'         | 'api'                          | DOES_NOT_EXIST
        'java'         | 'compileOnlyApi'               | DOES_NOT_EXIST

        'java-library' | 'compileOnly'                  | FORBIDDEN
        'java-library' | 'runtimeOnly'                  | FORBIDDEN
        'java-library' | 'implementation'               | FORBIDDEN
        'java-library' | 'runtimeElements'              | FORBIDDEN
        'java-library' | 'apiElements'                  | FORBIDDEN
        'java-library' | 'compileClasspath'             | VALID
        'java-library' | 'runtimeClasspath'             | VALID
        'java-library' | 'annotationProcessor'          | VALID
        'java-library' | 'api'                          | FORBIDDEN
        'java-library' | 'compileOnlyApi'               | FORBIDDEN
    }

    def "compileOnyApi dependency is part of #configuration"() {
        given:
        mavenRepo.module("org", "a").publish()

        buildFile << """
            plugins {
               id('java-library')
            }
            repositories {
                maven { url '$mavenRepo.uri' }
            }
            dependencies {
               compileOnlyApi 'org:a:1.0'
            }
            configurations.create('path') {
               extendsFrom(configurations.$configuration)
            }
            tasks.register('resolvePath') {
              def path = configurations.path
              doLast { println(path.collect { it.name }) }
            }
        """

        when:
        succeeds 'resolvePath'

        then:
        outputContains('[a-1.0.jar]')

        where:
        configuration          | _
        "compileOnly"          | _
        "testCompileOnly"      | _
        "compileClasspath"     | _
        "testCompileClasspath" | _
    }
}
