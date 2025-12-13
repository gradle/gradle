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
    def "causing invalid resolution via #methodName for role #role fails"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        when:
        fails('help')

        then:
        failureCauseContains(message)

        where:
        methodName                          | role              | methodCall                   || message
        'contains(new File("dummy.txt"))'   | 'consumable'      | 'contains(File)'             || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'contains(new File("dummy.txt"))'   | 'dependencyScope' | 'contains(File)'             || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getFiles()'                        | 'consumable'      | 'files'                      || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getFiles()'                        | 'dependencyScope' | 'files'                      || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getBuildDependencies()'            | 'consumable'      | 'getBuildDependencies()'     || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
        'getBuildDependencies()'            | 'dependencyScope' | 'getBuildDependencies()'     || "Resolving dependency configuration 'custom' is not allowed as it is defined as 'canBeResolved=false'."
    }

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
        failureCauseContains(message)

        where:
        methodName                                      | role              | methodCall                                                        || message
        'attributes(Action)'                            | 'dependencyScope' | "attributes { attribute(Attribute.of('foo', String), 'bar') }"    || buildMethodNotAllowedMessage('attributes(Action)')
        'copy()'                                        | 'consumable'      | 'copy()'                                                          || buildMethodNotAllowedMessage('copy()')
        'copy()'                                        | 'dependencyScope' | 'copy()'                                                          || buildMethodNotAllowedMessage('copy()')
        'copyRecursive()'                               | 'consumable'      | 'copyRecursive()'                                                 || buildMethodNotAllowedMessage('copyRecursive()')
        'copyRecursive()'                               | 'dependencyScope' | 'copyRecursive()'                                                 || buildMethodNotAllowedMessage('copyRecursive()')
        'copy(Spec)'                                    | 'consumable'      | 'copy { } as Spec'                                                || buildMethodNotAllowedMessage('copy(Spec)')
        'copy(Spec)'                                    | 'dependencyScope' | 'copy { } as Spec'                                                || buildMethodNotAllowedMessage('copy(Spec)')
        'copyRecursive(Spec)'                           | 'consumable'      | 'copyRecursive { } as Spec'                                       || buildMethodNotAllowedMessage('copyRecursive(Spec)')
        'copyRecursive(Spec)'                           | 'dependencyScope' | 'copyRecursive { } as Spec'                                       || buildMethodNotAllowedMessage('copyRecursive(Spec)')
        'defaultDependencies(Action)'                   | 'consumable'      | 'defaultDependencies { }'                                         || buildMethodNotAllowedMessage('defaultDependencies(Action)')
        'defaultDependencies(Action)'                   | 'resolvable'      | 'defaultDependencies { }'                                         || buildMethodNotAllowedMessage('defaultDependencies(Action)')
        'disableConsistentResolution()'                 | 'consumable'      | 'disableConsistentResolution()'                                   || buildMethodNotAllowedMessage('disableConsistentResolution()')
        'disableConsistentResolution()'                 | 'dependencyScope' | 'disableConsistentResolution()'                                   || buildMethodNotAllowedMessage('disableConsistentResolution()')
        'getResolvedConfiguration()'                    | 'consumable'      | 'getResolvedConfiguration()'                                      || buildMethodNotAllowedMessage('getResolvedConfiguration()')
        'getResolvedConfiguration()'                    | 'dependencyScope' | 'getResolvedConfiguration()'                                      || buildMethodNotAllowedMessage('getResolvedConfiguration()')
        'resolve()'                                     | 'consumable'      | 'resolve()'                                                       || buildMethodNotAllowedMessage('resolve()')
        'resolve()'                                     | 'dependencyScope' | 'resolve()'                                                       || buildMethodNotAllowedMessage('resolve()')
        'shouldResolveConsistentlyWith(Configuration)'  | 'consumable'      | 'shouldResolveConsistentlyWith(null)'                             || buildMethodNotAllowedMessage('shouldResolveConsistentlyWith(Configuration)')
        'shouldResolveConsistentlyWith(Configuration)'  | 'dependencyScope' | 'shouldResolveConsistentlyWith(null)'                             || buildMethodNotAllowedMessage('shouldResolveConsistentlyWith(Configuration)')
    }

    def "calling a valid but deprecated public API method #methodName for role #role warns"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration

            configurations.$role
            configurations.custom.$methodCall
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Calling $methodName on configuration ':custom' has been deprecated. This will fail with an error in Gradle 10. This configuration does not allow this method to be called. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
        succeeds('help')

        where:
        methodName                                      | role                                                                                                          | methodCall
        'copy()'                                        | 'migratingLocked("custom", ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_DEPENDENCY_SCOPE)'   | 'copy()'
        'copyRecursive()'                               | 'migratingLocked("custom", ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_DEPENDENCY_SCOPE)'   | 'copyRecursive()'
        'copy(Spec)'                                    | 'migratingLocked("custom", ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_DEPENDENCY_SCOPE)'   | 'copy { } as Spec'
        'copyRecursive(Spec)'                           | 'migratingLocked("custom", ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_DEPENDENCY_SCOPE)'   | 'copyRecursive { } as Spec'
        'defaultDependencies(Action)'                   | 'migratingLocked("custom", ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_RESOLVABLE)'         | 'defaultDependencies { }'
        'disableConsistentResolution()'                 | 'migratingLocked("custom", ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_DEPENDENCY_SCOPE)'   | 'disableConsistentResolution()'
        'shouldResolveConsistentlyWith(Configuration)'  | 'migratingLocked("custom", ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_DEPENDENCY_SCOPE)'   | 'shouldResolveConsistentlyWith(null)'
    }

    def "calling a valid but deprecated public API method #methodName that causes resolution for role #role warns"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration

            configurations.$role
            configurations.custom.$methodCall
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Calling $methodName on configuration ':custom' has been deprecated. This will fail with an error in Gradle 10. This configuration does not allow this method to be called. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
        executer.expectDocumentedDeprecationWarning("The custom configuration has been deprecated for resolution. This will fail with an error in Gradle 10. Please resolve another configuration instead. For more information, please refer to https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
        succeeds('help')

        where:
        methodName                                      | role                                                                                                          | methodCall
        'getResolvedConfiguration()'                    | 'migratingLocked("custom", ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_DEPENDENCY_SCOPE)'   | 'getResolvedConfiguration()'
        'resolve()'                                     | 'migratingLocked("custom", ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_DEPENDENCY_SCOPE)'   | 'resolve()'
    }

    def "calling an invalid internal API method #methodName for role #role fails"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            configurations.$role('custom')
            configurations.custom.$methodCall
        """

        when:
        fails('help')

        then:
        failureCauseContains(message)

        where:
        methodName                                      | role              | methodCall                                                        || message
        'callAndResetResolutionState()'                 | 'consumable'      | "callAndResetResolutionState { 'foo' }"                           || buildMethodNotAllowedMessage('callAndResetResolutionState(Factory)')
        'callAndResetResolutionState()'                 | 'dependencyScope' | "callAndResetResolutionState { 'foo' }"                           || buildMethodNotAllowedMessage('callAndResetResolutionState(Factory)')
        'getConsistentResolutionSource()'               | 'consumable'      | "getConsistentResolutionSource()"                                 || buildMethodNotAllowedMessage('getConsistentResolutionSource()')
        'getConsistentResolutionSource()'               | 'dependencyScope' | "getConsistentResolutionSource()"                                 || buildMethodNotAllowedMessage('getConsistentResolutionSource()')
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

    def "configuration explicitly deprecated for resolution will warn if resolved, but not fail"() {
        buildFile << """
            configurations {
                migratingLocked('foo', org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_DEPENDENCY_SCOPE)
            }
            ${mavenCentralRepository()}
            dependencies {
                foo 'org.apache.commons:commons-lang3:3.9'
            }
            configurations.foo.files
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The foo configuration has been deprecated for resolution. This will fail with an error in Gradle 10. Please resolve another configuration instead. For more information, please refer to https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
        succeeds("help")
    }

    private String buildMethodNotAllowedMessage(String methodName, String confName = 'custom') {
        return """Calling configuration method '$methodName' is not allowed for configuration '$confName'"""
    }

    private void assertCallNotAllowed(String methodName, String confName) {
        failureDescriptionContains("A problem occurred evaluating root project '${buildFile.parentFile.name}'.")

        // Exact usage printing isn't important to test here, it's tested elsewhere; just ensure we're printing the right message
        failureCauseContains(buildMethodNotAllowedMessage(methodName, confName))
        failureCauseContains("This method is only meant to be called on configurations which allow")
    }
}
