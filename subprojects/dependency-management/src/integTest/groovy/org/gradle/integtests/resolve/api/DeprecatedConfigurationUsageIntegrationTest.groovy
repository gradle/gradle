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

import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration.ProperMethodUsage
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ConfigurationUsageChangingFixture

class DeprecatedConfigurationUsageIntegrationTest extends AbstractIntegrationSpec implements ConfigurationUsageChangingFixture {
    def "calling an invalid public API method #methodName for role #role fails"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole
            
            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        when:
        executer.noDeprecationChecks() // These will be checked elsewhere, this test is about ensuring failures
        fails('help')

        then:
        failureCauseContains(message)

        where:
        methodName                                      | role            | methodCall                                  || message
        'resolve()'                                     | 'consumable'    | 'resolve()'                                 || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'resolve()'                                     | 'bucket'        | 'resolve()'                                 || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'files(Closure)'                                | 'consumable'    | 'files { }'                                 || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'files(Closure)'                                | 'bucket'        | 'files { }'                                 || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'fileCollection(Closure)'                       | 'consumable'    | 'fileCollection { }'                        || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'fileCollection(Closure)'                       | 'bucket'        | 'fileCollection { }'                        || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'fileCollection(Dependency)'                    | 'consumable'    | 'fileCollection(new org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency("org.jsoup", "jsoup", "1.15.3"))'  || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'fileCollection(Dependency)'                    | 'bucket'        | 'fileCollection(new org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency("org.jsoup", "jsoup", "1.15.3"))'  || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getResolvedConfiguration()'                    | 'consumable'    | 'getResolvedConfiguration()'               || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getResolvedConfiguration()'                    | 'bucket'        | 'getResolvedConfiguration()'               || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getBuildDependencies()'                        | 'consumable'    | 'getBuildDependencies()'                   || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getBuildDependencies()'                        | 'bucket'        | 'getBuildDependencies()'                   || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
    }

    def "calling an invalid public API method #methodName for role #role produces a deprecation warning"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole
            
            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        expect:
        executer.expectDocumentedDeprecationWarning(buildDeprecationMessage(methodName, role, allowed))
        succeeds('help')

        where:
        methodName                                      | role          | methodCall                                                            || allowed
        'attributes(Action)'                            | 'bucket'      | "attributes { attribute(Attribute.of('foo', String), 'bar') }"        || [ProperMethodUsage.CONSUMABLE, ProperMethodUsage.RESOLVABLE]
        'defaultDependencies(Action)'                   | 'consumable'  | 'defaultDependencies { }'                                             || [ProperMethodUsage.DECLARABLE_AGAINST]
        'defaultDependencies(Action)'                   | 'resolvable'  | 'defaultDependencies { }'                                             || [ProperMethodUsage.DECLARABLE_AGAINST]
        'shouldResolveConsistentlyWith(Configuration)'  | 'consumable'  | 'shouldResolveConsistentlyWith(null)'                                 || [ProperMethodUsage.RESOLVABLE]
        'shouldResolveConsistentlyWith(Configuration)'  | 'bucket'      | 'shouldResolveConsistentlyWith(null)'                                 || [ProperMethodUsage.RESOLVABLE]
        'disableConsistentResolution()'                 | 'consumable'  | 'disableConsistentResolution()'                                       || [ProperMethodUsage.RESOLVABLE]
        'disableConsistentResolution()'                 | 'bucket'      | 'disableConsistentResolution()'                                       || [ProperMethodUsage.RESOLVABLE]
        'getDependencyConstraints()'                    | 'consumable'  | 'getDependencyConstraints()'                                          || [ProperMethodUsage.DECLARABLE_AGAINST, ProperMethodUsage.RESOLVABLE]
    }

    def "calling an invalid internal API method #methodName for role #role throws an exception"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole
            
            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        when:
        fails('help')

        then:
        failureCauseContains(buildFailureMessage(methodName, role, allowed))

        where:
        methodName                          | role          | methodCall                                    || allowed
        'contains(File)'                    | 'consumable'  | "contains(new File('foo'))"                   || [ProperMethodUsage.RESOLVABLE]
        'setExcludeRules(Set)'              | 'consumable'  | "setExcludeRules(null)"                       || [ProperMethodUsage.DECLARABLE_AGAINST, ProperMethodUsage.RESOLVABLE]
        'getConsistentResolutionSource()'   | 'consumable'  | "getConsistentResolutionSource()"             || [ProperMethodUsage.RESOLVABLE]
        'getConsistentResolutionSource()'   | 'bucket'      | "getConsistentResolutionSource()"             || [ProperMethodUsage.RESOLVABLE]
        'getDependenciesResolver()'         | 'consumable'  | "getDependenciesResolver()"                   || [ProperMethodUsage.RESOLVABLE]
        'getDependenciesResolver()'         | 'bucket'      | "getDependenciesResolver()"                   || [ProperMethodUsage.RESOLVABLE]
        'getResolvedState()'                | 'consumable'  | "getResolvedState()"                          || [ProperMethodUsage.RESOLVABLE]
        'getResolvedState()'                | 'bucket'      | "getResolvedState()"                          || [ProperMethodUsage.RESOLVABLE]
        'getSyntheticDependencies()'        | 'consumable'  | "getSyntheticDependencies()"                  || [ProperMethodUsage.RESOLVABLE]
        'getSyntheticDependencies()'        | 'bucket'      | "getSyntheticDependencies()"                  || [ProperMethodUsage.RESOLVABLE]
        'resetResolutionState()'            | 'consumable'  | "resetResolutionState()"                      || [ProperMethodUsage.RESOLVABLE]
        'resetResolutionState()'            | 'bucket'      | "resetResolutionState()"                      || [ProperMethodUsage.RESOLVABLE]
        'toRootComponentMetaData()'         | 'consumable'  | "toRootComponentMetaData()"                   || [ProperMethodUsage.RESOLVABLE]
        'toRootComponentMetaData()'         | 'bucket'      | "toRootComponentMetaData()"                   || [ProperMethodUsage.RESOLVABLE]
    }

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

    private String buildDeprecationMessage(String methodName, String role, List<ProperMethodUsage> allowed) {
        return """Calling configuration method '$methodName' is deprecated for configuration 'custom', which has permitted usage(s):
${buildAllowedUsages(role)}
This method is only meant to be called on configurations which allow the (non-deprecated) usage(s): '${ buildProperNames(allowed) }'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configuration_usage"""
    }

    private String buildFailureMessage(String methodName, String role, List<ProperMethodUsage> allowed) {
        return """Calling configuration method '$methodName' is not allowed for configuration 'custom', which has permitted usage(s):
${buildAllowedUsages(role)}
This method is only meant to be called on configurations which allow the (non-deprecated) usage(s): '${ buildProperNames(allowed) }'."""
    }

    private String buildProperNames(List<ProperMethodUsage> usages) {
        usages.collect { ProperMethodUsage.buildProperName(it) }.join(", ")
    }

    private String buildAllowedUsages(String role) {
        switch (role) {
            case 'bucket':
                return "\tDeclarable Against - this configuration can have dependencies added to it"
            case 'consumable':
                return "\tConsumable - this configuration can be selected by another project as a dependency"
            case 'resolvable':
                return "\tResolvable - this configuration can be resolved by this project to a set of files"
            default:
                throw new IllegalArgumentException("Unknown role: $role")
        }
    }
}
