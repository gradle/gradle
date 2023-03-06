/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ConfigurationUsageChangingFixture

class DeprecatedConfigurationUsageIntegrationTest extends AbstractIntegrationSpec implements ConfigurationUsageChangingFixture {
    def "calling deprecated usage produces a deprecation warning"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole
            
            ConfigurationRole customRole = ConfigurationRole.forUsage('custom', true, false, false, true, false, false)
            configurations.createWithRole('custom', customRole)
            
            configurations.custom.attributes {
                attribute(Attribute.of('foo', String), 'bar')
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("""Calling configuration method 'attributes' is deprecated for configuration 'custom', which has permitted usage(s):
\tConsumable - this configuration can be selected by another project as a dependency (but this behavior is marked deprecated)
This method is only meant to be called on configurations which allow the (non-deprecated) usage(s): 'Consumable, Resolvable'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Configurations should only be used as permitted. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configuration_usage""")
        succeeds('help')
    }

    def "calling deprecated usage does not produce a deprecation warning if other allowed usage permits it"() {
        given:
        buildFile << """
            configurations {
                custom {
                    deprecateForConsumption()
                    
                    attributes {
                        attribute(Attribute.of('foo', String), 'bar')
                    }
                }
            }
        """

        expect:
        succeeds('help')
    }

    def "calling deprecated usage produces a deprecation warning"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole
            
            ConfigurationRole customRole = ConfigurationRole.forUsage('custom', false, true, true, false, true, false)
            configurations.createWithRole('custom', customRole)
            
            configurations.custom.contains(new File("test"))
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The custom configuration has been deprecated for resolution. This will fail with an error in Gradle 9.0. Please resolve another configuration instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
        executer.expectDocumentedDeprecationWarning("""Calling configuration method 'contains' is deprecated for configuration 'custom', which has permitted usage(s):
\tResolvable - this configuration can be resolved by this project to a set of files (but this behavior is marked deprecated)
\tDeclarable Against - this configuration can have dependencies added to it
This method is only meant to be called on configurations which allow the (non-deprecated) usage(s): 'Resolvable'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Configurations should only be used as permitted. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configuration_usage""")
        succeeds('help')
    }
}
