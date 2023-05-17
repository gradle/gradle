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
        methodName                   | role         | methodCall                                                                                                                           || message
        'resolve()'                  | 'consumable' | 'resolve()'                                                                                                                          || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'resolve()'                  | 'bucket'     | 'resolve()'                                                                                                                          || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'files(Closure)'             | 'consumable' | 'files { }'                                                                                                                          || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'files(Closure)'             | 'bucket'     | 'files { }'                                                                                                                          || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'fileCollection(Closure)'    | 'consumable' | 'fileCollection { }'                                                                                                                 || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'fileCollection(Closure)'    | 'bucket'     | 'fileCollection { }'                                                                                                                 || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'fileCollection(Dependency)' | 'consumable' | 'fileCollection(new org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency("org.jsoup", "jsoup", "1.15.3"))' || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'fileCollection(Dependency)' | 'bucket'     | 'fileCollection(new org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency("org.jsoup", "jsoup", "1.15.3"))' || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getResolvedConfiguration()' | 'consumable' | 'getResolvedConfiguration()'                                                                                                         || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getResolvedConfiguration()' | 'bucket'     | 'getResolvedConfiguration()'                                                                                                         || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getBuildDependencies()'     | 'consumable' | 'getBuildDependencies()'                                                                                                             || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getBuildDependencies()'     | 'bucket'     | 'getBuildDependencies()'                                                                                                             || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
    }

    def "calling an invalid public API method #methodName for role #role produces a deprecation warning"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        expect:
        executer.expectDocumentedDeprecationWarning(buildDeprecationMessage(methodName, role, allowed, false))
        succeeds('help')

        where:
        methodName                                     | role         | methodCall                                                     || allowed
        'attributes(Action)'                           | 'bucket'     | "attributes { attribute(Attribute.of('foo', String), 'bar') }" || [ProperMethodUsage.CONSUMABLE, ProperMethodUsage.RESOLVABLE]
        'defaultDependencies(Action)'                  | 'consumable' | 'defaultDependencies { }'                                      || [ProperMethodUsage.DECLARABLE_AGAINST]
        'defaultDependencies(Action)'                  | 'resolvable' | 'defaultDependencies { }'                                      || [ProperMethodUsage.DECLARABLE_AGAINST]
        'shouldResolveConsistentlyWith(Configuration)' | 'consumable' | 'shouldResolveConsistentlyWith(null)'                          || [ProperMethodUsage.RESOLVABLE]
        'shouldResolveConsistentlyWith(Configuration)' | 'bucket'     | 'shouldResolveConsistentlyWith(null)'                          || [ProperMethodUsage.RESOLVABLE]
        'disableConsistentResolution()'                | 'consumable' | 'disableConsistentResolution()'                                || [ProperMethodUsage.RESOLVABLE]
        'disableConsistentResolution()'                | 'bucket'     | 'disableConsistentResolution()'                                || [ProperMethodUsage.RESOLVABLE]
        'copy()'                                       | 'consumable' | 'copy()'                                                       || [ProperMethodUsage.RESOLVABLE]
        'copy()'                                       | 'bucket'     | 'copy()'                                                       || [ProperMethodUsage.RESOLVABLE]
        'copyRecursive()'                              | 'consumable' | 'copyRecursive()'                                              || [ProperMethodUsage.RESOLVABLE]
        'copyRecursive()'                              | 'bucket'     | 'copyRecursive()'                                              || [ProperMethodUsage.RESOLVABLE]
        'copy(Spec)'                                   | 'consumable' | 'copy { } as Spec'                                             || [ProperMethodUsage.RESOLVABLE]
        'copy(Spec)'                                   | 'bucket'     | 'copy { } as Spec'                                             || [ProperMethodUsage.RESOLVABLE]
        'copyRecursive(Spec)'                          | 'consumable' | 'copyRecursive { } as Spec'                                    || [ProperMethodUsage.RESOLVABLE]
        'copyRecursive(Spec)'                          | 'bucket'     | 'copyRecursive { } as Spec'                                    || [ProperMethodUsage.RESOLVABLE]

    }

    def "calling an invalid internal API method #methodName for role #role produces a deprecation warning"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        expect:
        executer.expectDocumentedDeprecationWarning(buildDeprecationMessage(methodName, role, allowed, true))
        succeeds('help')

        where:
        methodName                         | role         | methodCall                         || allowed
        'setExcludeRules(Set)'             | 'consumable' | "setExcludeRules([] as Set)"       || [ProperMethodUsage.DECLARABLE_AGAINST, ProperMethodUsage.RESOLVABLE]
        'getConsistentResolutionSource()'  | 'consumable' | "getConsistentResolutionSource()"  || [ProperMethodUsage.RESOLVABLE]
        'getConsistentResolutionSource()'  | 'bucket'     | "getConsistentResolutionSource()"  || [ProperMethodUsage.RESOLVABLE]
        'getDependenciesResolverFactory()' | 'consumable' | "getDependenciesResolverFactory()" || [ProperMethodUsage.RESOLVABLE]
        'getDependenciesResolverFactory()' | 'bucket'     | "getDependenciesResolverFactory()" || [ProperMethodUsage.RESOLVABLE]
        'getResolvedState()'               | 'consumable' | "getResolvedState()"               || [ProperMethodUsage.RESOLVABLE]
        'getResolvedState()'               | 'bucket'     | "getResolvedState()"               || [ProperMethodUsage.RESOLVABLE]
        'getSyntheticDependencies()'       | 'consumable' | "getSyntheticDependencies()"       || [ProperMethodUsage.RESOLVABLE]
        'getSyntheticDependencies()'       | 'bucket'     | "getSyntheticDependencies()"       || [ProperMethodUsage.RESOLVABLE]
        'resetResolutionState()'           | 'consumable' | "resetResolutionState()"           || [ProperMethodUsage.RESOLVABLE]
        'resetResolutionState()'           | 'bucket'     | "resetResolutionState()"           || [ProperMethodUsage.RESOLVABLE]
        'toRootComponentMetaData()'        | 'consumable' | "toRootComponentMetaData()"        || [ProperMethodUsage.RESOLVABLE]
        'toRootComponentMetaData()'        | 'bucket'     | "toRootComponentMetaData()"        || [ProperMethodUsage.RESOLVABLE]
    }

    def "forcing resolve of a non-resolvable configuration via calling invalid internal API method #methodName for role #role warns and then throws an exception"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        expect:
        executer.expectDocumentedDeprecationWarning(buildDeprecationMessage(methodName, role, allowed, true))
        fails('help')

        where:
        methodName       | role         | methodCall                  || allowed
        'contains(File)' | 'consumable' | "contains(new File('foo'))" || [ProperMethodUsage.RESOLVABLE]
    }

    def "calling deprecated usage produces a deprecation warning"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            ConfigurationRole customRole = new org.gradle.api.internal.artifacts.configurations.DefaultConfigurationRole("custom", true, false, false, true, false, false)
            configurations.createWithRole('custom', customRole)

            configurations.custom.attributes {
                attribute(Attribute.of('foo', String), 'bar')
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("""Calling configuration method 'attributes(Action)' is deprecated for configuration 'custom', which has permitted usage(s):
\tConsumable - this configuration can be selected by another project as a dependency (but this behavior is marked deprecated)
This method is only meant to be called on configurations which allow the (non-deprecated) usage(s): 'Consumable, Resolvable'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: ${documentationRegistry.getDocumentationFor("upgrading_version_8", "deprecated_configuration_usage")}""")
        succeeds('help')
    }

    def "calling deprecated usage does not produce a deprecation warning if other allowed usage permits it"() {
        given:
        buildFile << """
            configurations {
                createWithRole('foo', org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_BUCKET) {
                    attributes {
                        attribute(Attribute.of('foo', String), 'bar')
                    }
                }
            }
        """

        expect:
        succeeds('help')
    }

    def "configuration explicitly deprecated for resolution will warn if resolved, but not fail"() {
        buildFile << """
            configurations {
                createWithRole('foo', org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.RESOLVABLE_BUCKET_TO_BUCKET)
            }

            ${mavenCentralRepository()}

            dependencies {
                foo 'org.apache.commons:commons-lang3:3.9'
            }

            configurations.foo.files
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The foo configuration has been deprecated for resolution. This will fail with an error in Gradle 9.0. Please resolve another configuration instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
        succeeds("help")
    }

    private String buildDeprecationMessage(String methodName, String role, List<ProperMethodUsage> allowed, boolean allowDeprecated) {
        return """Calling configuration method '$methodName' is deprecated for configuration 'custom', which has permitted usage(s):
${buildAllowedUsages(role)}
This method is only meant to be called on configurations which allow the ${allowDeprecated ? "" : "(non-deprecated) "}usage(s): '${buildProperNames(allowed)}'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configuration_usage"""
    }

    private String buildProperNames(List<ProperMethodUsage> usages) {
        usages.collect { ProperMethodUsage.buildProperName(it) }.join(", ")
    }

    private String buildAllowedUsages(String role) {
        switch (role) {
            case 'bucket':
                return "\tDeclarable - this configuration can have dependencies added to it"
            case 'consumable':
                return "\tConsumable - this configuration can be selected by another project as a dependency"
            case 'resolvable':
                return "\tResolvable - this configuration can be resolved by this project to a set of files"
            default:
                throw new IllegalArgumentException("Unknown role: $role")
        }
    }
}
