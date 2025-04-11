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
        methodName                   | role              | methodCall                   || message
        'resolve()'                  | 'consumable'      | 'resolve()'                  || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'resolve()'                  | 'dependencyScope' | 'resolve()'                  || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getFiles()'                 | 'consumable'      | 'files'                      || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getFiles()'                 | 'dependencyScope' | 'files'                      || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getResolvedConfiguration()' | 'consumable'      | 'getResolvedConfiguration()' || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getResolvedConfiguration()' | 'dependencyScope' | 'getResolvedConfiguration()' || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getBuildDependencies()'     | 'consumable'      | 'getBuildDependencies()'     || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getBuildDependencies()'     | 'dependencyScope' | 'getBuildDependencies()'     || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
    }

    def "calling an invalid public API method (#methodCall) for role #role fails"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        when:
        fails('help')

        then:
        failure.assertHasCause(buildFailureMessage(what, why))

        where:
        role              | methodCall                                                     || what                                  | why
        'dependencyScope' | "attributes { attribute(Attribute.of('foo', String), 'bar') }" || "set attributes on"                   | "a consumable or resolvable configuration"
        'consumable'      | 'defaultDependencies { }'                                      || "set default dependencies on"         | "a configuration that allows declaring dependencies"
        'resolvable'      | 'defaultDependencies { }'                                      || "set default dependencies on"         | "a configuration that allows declaring dependencies"
        'consumable'      | 'shouldResolveConsistentlyWith(null)'                          || "set consistent resolution on"        | "a resolvable configuration"
        'dependencyScope' | 'shouldResolveConsistentlyWith(null)'                          || "set consistent resolution on"        | "a resolvable configuration"
        'consumable'      | 'disableConsistentResolution()'                                || "disable consistent resolution on"    | "a resolvable configuration"
        'dependencyScope' | 'disableConsistentResolution()'                                || "disable consistent resolution on"    | "a resolvable configuration"
        'consumable'      | 'copy()'                                                       || "copy"                                | "a resolvable configuration"
        'dependencyScope' | 'copy()'                                                       || "copy"                                | "a resolvable configuration"
        'consumable'      | 'copyRecursive()'                                              || "copy"                                | "a resolvable configuration"
        'dependencyScope' | 'copyRecursive()'                                              || "copy"                                | "a resolvable configuration"
        'consumable'      | 'copy { } as Spec'                                             || "copy"                                | "a resolvable configuration"
        'dependencyScope' | 'copy { } as Spec'                                             || "copy"                                | "a resolvable configuration"
        'consumable'      | 'copyRecursive { } as Spec'                                    || "copy"                                | "a resolvable configuration"
        'dependencyScope' | 'copyRecursive { } as Spec'                                    || "copy"                                | "a resolvable configuration"
    }

    def "calling an invalid internal API (#methodCall) for role #role fails"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        when:
        fails('help')

        then:
        failure.assertHasCause(buildFailureMessage(what, why))

        where:
        role              | methodCall                              || what                                     | why
        'consumable'      | "getConsistentResolutionSource()"       || "get consistent resolution source on"    | "a resolvable configuration"
        'dependencyScope' | "getConsistentResolutionSource()"       || "get consistent resolution source on"    | "a resolvable configuration"
        'consumable'      | "callAndResetResolutionState { 'foo' }" || "reset consistent resolution state on"   | "a resolvable configuration"
        'dependencyScope' | "callAndResetResolutionState { 'foo' }" || "reset consistent resolution state on"   | "a resolvable configuration"
    }

    def "forcing resolve of a non-resolvable configuration via calling invalid internal API (contains) for role #role fails"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            configurations.$role('custom')
            configurations.custom.contains(new File('foo'))
        """

        when:
        fails('help')

        then:
        failure.assertHasCause("Not allowed to check if configuration ':custom' contains a file as it is not a resolvable configuration.")

        where:
        role << ['consumable', 'dependencyScope']
    }

    def "calling unpermitted usage produces a failure"() {
        given:
        buildFile << """
            configurations.consumable('custom')

            configurations.custom.getConsistentResolutionSource()
        """

        when:
        fails('help')

        then:
        failure.assertHasCause("Not allowed to get consistent resolution source on configuration ':custom' as it is not a resolvable configuration.")
    }

    def "calling deprecated usage does not produce a deprecation warning if other allowed usage permits it"() {
        given:
        buildFile << """
            configurations {
                migratingUnlocked('foo', org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE)
                foo.attributes {
                    attribute(Attribute.of('foo', String), 'bar')
                }
            }
        """

        expect:
        succeeds('help')
    }

    def "configuration explicitly deprecated for resolution will warn if resolved, but not fail"() {
        buildFile << """
            configurations {
                migratingUnlocked('foo', org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.LEGACY_TO_CONSUMABLE)
            }

            ${mavenCentralRepository()}

            dependencies {
                foo 'org.apache.commons:commons-lang3:3.9'
            }

            configurations.foo.files
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The foo configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 9.0. Please use another configuration instead. For more information, please refer to https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
        succeeds("help")
    }

    private String buildFailureMessage(String what, String why) {
        return String.format("Not allowed to %s configuration ':custom' as it is not %s.", what, why)
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
            case 'dependencyScope':
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
