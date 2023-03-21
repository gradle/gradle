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
            
            ConfigurationRole customRole = new ConfigurationRole() {
                @Override
                String getName() {
                    return "custom"
                }
    
                @Override
                boolean isConsumable() {
                    return true
                }
    
                @Override
                boolean isResolvable() {
                    return false
                }
    
                @Override
                boolean isDeclarableAgainst() {
                    return false
                }
    
                @Override
                boolean isConsumptionDeprecated() {
                    return true
                }
    
                @Override
                boolean isResolutionDeprecated() {
                    return false
                }
    
                @Override
                boolean isDeclarationAgainstDeprecated() {
                    return false
                }
            }
            configurations.createWithRole('custom', customRole)
            
            configurations.custom.attributes {
                attribute(Attribute.of('foo', String), 'bar')
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("""Calling configuration method 'attributes(Action)' is deprecated for configuration 'custom', which has permitted usage(s):
\tConsumable - this configuration can be selected by another project as a dependency (but this behavior is marked deprecated)
This method is only meant to be called on configurations which allow the (non-deprecated) usage(s): 'Consumable, Resolvable'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configuration_usage""")
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
}
