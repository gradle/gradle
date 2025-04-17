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

class InvalidConfigurationUsageIntegrationTest extends AbstractIntegrationSpec {
    def "calling an invalid public API method #methodName for role #role fails"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        when:
        fails('help')

        then:
        assertCallNotAllowed(methodName, "custom")

        where:
        methodName                                     | role              | methodCall
        'attributes(Action)'                           | 'dependencyScope' | "attributes { attribute(Attribute.of('foo', String), 'bar') }"
        'defaultDependencies(Action)'                  | 'consumable'      | 'defaultDependencies { }'
        'defaultDependencies(Action)'                  | 'resolvable'      | 'defaultDependencies { }'
        'shouldResolveConsistentlyWith(Configuration)' | 'consumable'      | 'shouldResolveConsistentlyWith(null)'
        'shouldResolveConsistentlyWith(Configuration)' | 'dependencyScope' | 'shouldResolveConsistentlyWith(null)'
        'disableConsistentResolution()'                | 'consumable'      | 'disableConsistentResolution()'
        'disableConsistentResolution()'                | 'dependencyScope' | 'disableConsistentResolution()'
        'copy()'                                       | 'consumable'      | 'copy()'
        'copy()'                                       | 'dependencyScope' | 'copy()'
        'copyRecursive()'                              | 'consumable'      | 'copyRecursive()'
        'copyRecursive()'                              | 'dependencyScope' | 'copyRecursive()'
        'copy(Spec)'                                   | 'consumable'      | 'copy { } as Spec'
        'copy(Spec)'                                   | 'dependencyScope' | 'copy { } as Spec'
        'copyRecursive(Spec)'                          | 'consumable'      | 'copyRecursive { } as Spec'
        'copyRecursive(Spec)'                          | 'dependencyScope' | 'copyRecursive { } as Spec'
        'getResolvedConfiguration()'                   | 'consumable'      | 'getResolvedConfiguration()'
        'getResolvedConfiguration()'                   | 'dependencyScope' | 'getResolvedConfiguration()'
        'resolve()'                                    | 'consumable'      | 'resolve()'
        'resolve()'                                    | 'dependencyScope' | 'resolve()'
    }

    def "causing resolve of a non-resolvable configuration via public API method #methodName with role #role fails"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        when:
        fails('help')

        then:
        assertResolutionNotAllowed("custom")

        where:
        methodName               | role              | methodCall
        'getBuildDependencies()' | 'consumable'      | 'getBuildDependencies()'
        'getBuildDependencies()' | 'dependencyScope' | 'getBuildDependencies()'
        'getFiles()'             | 'consumable'      | 'files'
        'getFiles()'             | 'dependencyScope' | 'files'
    }

    def "calling an invalid internal API method #methodName for role #role fails"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        expect:
        fails('help')
        assertCallNotAllowed(methodName, "custom")

        where:
        methodName                        | role              | methodCall
        'getConsistentResolutionSource()' | 'consumable'      | "getConsistentResolutionSource()"
        'getConsistentResolutionSource()' | 'dependencyScope' | "getConsistentResolutionSource()"
        'callAndResetResolutionState()'   | 'consumable'      | "callAndResetResolutionState { 'foo' }"
        'callAndResetResolutionState()'   | 'dependencyScope' | "callAndResetResolutionState { 'foo' }"
    }

    def "causing resolve of a non-resolvable configuration via internal API method #methodName for role #role fails"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        expect:
        fails('help')
        assertCallNotAllowed(methodName, "custom")

        where:
        methodName       | role         | methodCall
        'contains(File)' | 'consumable' | "contains(new File('foo'))"
    }

    def "calling deprecated usage does not produce a deprecation warning if other allowed usage permits it"() {
        given:
        buildFile << """
            configurations {
                migratingLocked('foo', org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE)
                foo.attributes {
                    attribute(Attribute.of('foo', String), 'bar')
                }
            }
        """

        expect:
        succeeds('help')
    }

    private void assertCallNotAllowed(String methodName, String confName) {
        failureDescriptionContains("A problem occurred evaluating root project '${buildFile.parentFile.name}'.")

        // Exact usage printing isn't important to test here, it's tested elsewhere; just ensure we're printing the right message
        failureCauseContains("Calling configuration method '$methodName' is not allowed for configuration '$confName', which has permitted usage(s):")
        failureCauseContains("This method is only meant to be called on configurations which allow")
    }

    private void assertResolutionNotAllowed(String confName) {
        failureDescriptionContains("A problem occurred evaluating root project '${buildFile.parentFile.name}'.")
        failureCauseContains("""Resolving dependency configuration '$confName' is not allowed as it is defined as 'canBeResolved=false'.
Instead, a resolvable ('canBeResolved=true') dependency configuration that extends '$confName' should be resolved.""")
    }
}
