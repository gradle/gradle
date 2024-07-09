/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.internal.artifacts.configurations

import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.GradleVersion

/**
 * Integration tests for {@code extendsFrom} method in {@link DefaultConfiguration}.
 */
class DefaultConfigurationExtendsFromIntegrationTest extends AbstractIntegrationSpec {
    def "extending a configuration in another project is deprecated"() {
        given:
        settingsFile """
            include ":project1", ":project2"
        """

        groovyFile(file('project1/build.gradle'), """
            configurations {
                resolvable('conf1')
            }
        """)

        groovyFile(file('project2/build.gradle'), """
            configurations {
                resolvable('conf2') {
                    extendsFrom project(':project1').configurations.conf1
                }
            }
        """)

        expect:
        executer.expectDeprecationWarning("Configuration 'conf2' in project ':project2' extends configuration 'conf1' in project ':project1'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Configurations can only extend from configurations in the same project. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#extending_configurations_in_same_project")
        succeeds ':project2:resolvableConfigurations', '--all'
        outputContains("""
--------------------------------------------------
Configuration conf2
--------------------------------------------------

Extended Configurations
    - conf1
""")
    }

    def "extending a configuration in same project is fine"() {
        given:
        buildFile """
            configurations {
                resolvable('conf1')
            }

            configurations {
                resolvable('conf2') {
                    extendsFrom configurations.conf1
                }
            }
        """

        expect:
        succeeds 'resolvableConfigurations', '--all'
        outputContains("""
--------------------------------------------------
Configuration conf2
--------------------------------------------------

Extended Configurations
    - conf1
""")
    }
}
