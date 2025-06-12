/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ConfigurationUsageChangingFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue

class ConfigurationRoleUsageIntegrationTest extends AbstractIntegrationSpec implements ConfigurationUsageChangingFixture {
    // region Roleless (Implicit LEGACY Role) Configurations
    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "default usage for roleless configuration is to allow anything"() {
        given:
        buildFile << """
            configurations {
                custom
            }

            tasks.register('checkConfUsage') {
                doLast {
                    assert configurations.custom.canBeConsumed
                    assert configurations.custom.canBeResolved
                    assert configurations.custom.canBeDeclared
                    assert !configurations.custom.deprecatedForConsumption
                    assert !configurations.custom.deprecatedForResolution
                    assert !configurations.custom.deprecatedForDeclarationAgainst
                }
            }
        """

        expect:
        succeeds('checkConfUsage')
    }

    def "can create configuration named #configuration with same legacy behavior"() {
        given:
        buildFile << """
            configurations {
                $configuration {
                    assert canBeConsumed
                    assert canBeResolved
                    assert canBeDeclared
                    assert !deprecatedForConsumption
                    assert !deprecatedForResolution
                    assert !deprecatedForDeclarationAgainst
                }
            }
        """

        expect:
        succeeds 'help'

        where:
        configuration << ["legacy", "consumable", "resolvable", "consumableLocked", "resolvableLocked", "dependencyScopeUnlocked"]
    }

    def "can prevent usage mutation of roleless configurations"() {
        given:
        buildFile << """
            configurations {
                custom {
                    assert canBeResolved == true
                    preventUsageMutation()
                    canBeResolved = false
                }
            }
        """

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure('custom')
    }

    def "can prevent usage mutation of roleless configuration meant for consumption"() {
        given:
        buildFile << """
            configurations {
                testConf {
                    assert canBeConsumed
                    canBeResolved = false
                    canBeDeclared = false
                    preventUsageMutation()
                    canBeConsumed = false
                }
            }
        """

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure('testConf')
    }

    def "can add resolution alternatives to configuration deprecated for resolution"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        buildFile << """
            configurations {
                deps
                migratingLocked("testConf", org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_DEPENDENCY_SCOPE) {
                    addResolutionAlternatives("anotherConf")
                    extendsFrom(deps)
                }
            }

            repositories { maven { url = "${mavenRepo.uri}" } }

            dependencies {
                deps "org:foo:1.0"
            }

            task resolve {
                configurations.testConf.files
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The testConf configuration has been deprecated for resolution. This will fail with an error in Gradle 10. Please resolve the anotherConf configuration instead. For more information, please refer to https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
        succeeds 'resolve'
    }

    def "can prevent usage mutation of roleless configuration #configuration added by java plugin meant for resolution"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            configurations {
                $configuration {
                    assert canBeResolved == true
                    preventUsageMutation()
                    canBeResolved = false
                }
            }
        """

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure(configuration, 'Resolvable')

        where:
        configuration << ['runtimeClasspath', 'compileClasspath']
    }

    def "configurations created by buildSrc automatically can not have usage changed (#configuration - #method(#value))"() {
        given:
        file("buildSrc/src/main/java/MyTask.java") << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            public abstract class MyTask extends DefaultTask {
                @TaskAction
                void run() {}
            }
        """

        file("buildSrc/build.gradle") << """
            configurations {
                assert findByName('implementation')

                implementation {
                    assert !canBeConsumed
                    assert !canBeResolved
                    assert canBeDeclared

                    $method($value)
                }
            }
        """

        buildFile << """
            tasks.register('myTask', MyTask)
        """

        when:
        fails 'myTask'

        then:
        failure.assertHasDescription("A problem occurred evaluating project ':buildSrc'.")
        assertUsageLockedFailure(configuration, role)

        where:
        configuration               | method               | role               | value
        'buildSrc:implementation'   | 'setCanBeResolved'   | 'Dependency Scope' | true
        'buildSrc:implementation'   | 'setCanBeConsumed'   | 'Dependency Scope' | true
        'buildSrc:implementation'   | 'setCanBeDeclared'   | 'Dependency Scope' | false
    }

    def "configurations can not have usage changed from other projects (#configuration - #method(#value)"() {
        given:
        file("projectA/build.gradle") << """
            plugins {
                id 'java'
            }
        """

        file("projectB/build.gradle") << """
            plugins {
                id 'java'
            }
        """

        file("settings.gradle") << """
            include 'projectA', 'projectB'
        """

        file("build.gradle") << """
            subprojects {
                afterEvaluate {
                    configurations {
                        println project.name
                        assert findByName('implementation')

                        implementation {
                            assert !canBeConsumed
                            assert !canBeResolved
                            assert canBeDeclared

                            $method($value)
                        }
                    }
                }
            }
        """

        when:
        fails 'help'

        then:
        failure.assertHasDescription("A problem occurred configuring project ':projectA'.")
        assertUsageLockedFailure(configuration, role)

        where:
        configuration               | method               | role               | value
        'projectA:implementation'   | 'setCanBeConsumed'   | 'Dependency Scope' | true
        'projectA:implementation'   | 'setCanBeResolved'   | 'Dependency Scope' | true
        'projectA:implementation'   | 'setCanBeDeclared'   | 'Dependency Scope' | false
    }

    def "can update all roles for non-locked configurations"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            configurations {
                create('c1')
                create('c2') { }
                register('c3')
                register('c4') { }
                maybeCreate('c5')
                c6
                c7 { }
            }

            configurations.all {
                canBeResolved = !canBeResolved
                canBeConsumed = !canBeConsumed
                canBeDeclared = !canBeDeclared
            }
        """

        expect:
        executer.noDeprecationChecks() // These are checked in the other tests, and there would be many of them here
        succeeds 'help'
    }

    def "changing usage for a configuration in the legacy role is allowed"() {
        given:
        buildFile << """
            configurations.create('custom')
            assert configurations.custom.canBeResolved
            configurations.custom.canBeResolved = false
        """

        expect:
        succeeds 'help'
    }

    def "using a reserved configuration (#conf) fails if JavaBasePlugin applied"() {
        given:
        file("buildSrc/src/main/groovy/MyPlugin.groovy") << """
            import org.gradle.api.Plugin
            import org.gradle.api.plugins.BasePlugin
            import org.gradle.api.plugins.JvmEcosystemPlugin

            class MyPlugin implements Plugin {
                void apply(project) {
                    project.pluginManager.apply(BasePlugin.class)
                    project.pluginManager.apply(JvmEcosystemPlugin.class)

                    project.sourceSets.create('custom')
                    project.configurations.create('$conf')
                }
            }
        """

        file('buildSrc/build.gradle') << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """

        file("buildSrc/src/main/resources/META-INF/gradle-plugins/my-plugin.properties") << "implementation-class=MyPlugin"

        buildFile << """
            plugins {
                id 'my-plugin'
                id 'java'
            }
        """

        when:
        fails 'help'

        then:
        failure.assertHasDescription("An exception occurred applying plugin request [id: 'java']")
        failureHasCause("""Cannot add a configuration with name '$conf' as a configuration with that name already exists.""")

        where:
        conf                | sourceSet
        'customCompileOnly' | 'custom'
        'implementation'    | 'main'
    }

    @Issue("https://github.com/gradle/gradle/issues/26461")
    def "cannot anticipate configuration names to be created from sourcesets"() {
        given:
        settingsFile """
            include 'resolver', 'producer'
        """

        file("resolver/build.gradle") << """
            plugins {
                id 'java-base'
            }

            configurations {
                noAttributes
            }

            dependencies {
                noAttributes project(":producer")
            }

            abstract class ResolveTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getMyInputs()

                @TaskAction
                void doTask() {
                    /**
                     * If additionalRuntimeClasspath in producer is consumable, it will
                     * be selected instead of runtimeElements.  This ensures it does not
                     * remain consumable after the sourceset is created.
                     */
                    assert myInputs*.name == ['producer.jar']
                }
            }

            tasks.register("resolve", ResolveTask) {
                myInputs.from(configurations.noAttributes)
            }
        """

        file("producer/build.gradle") << """
            plugins {
                id 'java'
            }

            ${confCreationCode}

            configurations.additionalRuntimeClasspath.outgoing {
                artifact file('wrong-file.jar')
            }

            sourceSets {
                additional
            }
        """

        expect:
        fails "resolve"
        failure.assertHasDescription("A problem occurred evaluating project ':producer'.")
        failure.assertHasCause("""Cannot add a configuration with name 'additionalRuntimeClasspath' as a configuration with that name already exists.""")

        where:
        confCreationCode | createdRole | description
        """
            configurations {
                additionalRuntimeClasspath
            }
        """                                                                             | ConfigurationRoles.ALL        | "legacy configuration with implicit allowed usage"
        """
            configurations {
                additionalRuntimeClasspath {
                    canBeConsumed = true
                }
            }
        """                                                                             | ConfigurationRoles.ALL        | "legacy configuration with explicit set consumed = true"
        "configurations.consumable('additionalRuntimeClasspath')"                       | ConfigurationRoles.CONSUMABLE | "role-based configuration"
        "configurations.consumableLocked('additionalRuntimeClasspath')"                 | ConfigurationRoles.CONSUMABLE | "internal locked role-based configuration"
    }

    def "redundantly changing usage on a legacy configuration does not warn even if flag is set"() {
        given:
        buildFile << """
            def test = configurations.create('test')

            assert test.canBeResolved
            assert test.canBeConsumed
            assert test.canBeDeclared

            test.setCanBeConsumed(true)
            test.setCanBeResolved(true)
            test.setCanBeDeclared(true)
        """

        expect:
        succeeds('help', "-Dorg.gradle.internal.deprecation.preliminary.Configuration.redundantUsageChangeWarning.enabled=true")
    }

    def "changing usage for configuration #configuration fails"() {
        given: "a buildscript which attempts to change a configuration's usage"
        buildFile << """
            plugins {
                id 'java-library'
            }

            configurations {
                $configuration {
                    canBeResolved = !canBeResolved
                }
            }
        """

        when: "the build fails"
        fails 'help'

        then:
        failure.assertHasDescription("A problem occurred evaluating root project '${buildFile.parentFile.name}'.")
        assertUsageLockedFailure(configuration, role)

        where: "a non-exhaustive list of configurations is tested"
        configuration       | role
        'api'               | 'Dependency Scope'
        'implementation'    | 'Dependency Scope'
        'compileOnly'       | 'Dependency Scope'
        'runtimeOnly'       | 'Dependency Scope'
        'archives'          | 'Consumable'
    }

    def "setting consumable = false fails for consumable configurations added by java plugin"() {
        given: "a buildscript which attempts to change a configuration's usage"
        buildFile << """
            plugins {
                id 'java-library'
            }

            configurations {
                "$configuration" {
                    assert canBeConsumed
                    canBeConsumed = false
                }
            }
        """

        when: "the build fails because the configuration is not allowed to change"
        fails 'help'

        then:
        failure.assertHasDescription("A problem occurred evaluating root project '${buildFile.parentFile.name}'.")
        assertUsageLockedFailure(configuration, role)

        where: "a non-exhaustive list of configurations is tested"
        configuration       | role
        'default'           | 'Consumable'
        'archives'          | 'Consumable'
        'apiElements'       | 'Consumable'
        'runtimeElements'   | 'Consumable'
    }

    def "changing consumable to true always fails for non-LEGACY configurations (can not change #configuration usage)"() {
        given: "a buildscript which attempts to change a configuration's usage"
        buildFile << """
            plugins {
                id 'java-library'
            }

            configurations {
                $configuration {
                    assert !canBeConsumed
                    canBeConsumed = true
                }
            }
        """

        when:
        fails 'help'

        then:
        failure.assertHasDescription("A problem occurred evaluating root project '${buildFile.parentFile.name}'.")
        assertUsageLockedFailure(configuration, role)

        where:
        configuration       | role
        'api'               | 'Dependency Scope'
        'implementation'    | 'Dependency Scope'
        'runtimeOnly'       | 'Dependency Scope'
        'compileOnly'       | 'Dependency Scope'
        'compileOnlyApi'    | 'Dependency Scope'
        'runtimeClasspath'  | 'Resolvable'
        'compileClasspath'  | 'Resolvable'
    }
    // endregion Roleless (Implicit LEGACY Role) Configurations

    // region Role-Based Configurations
    def "intended usage is allowed for role-based configuration #role"() {
        given:
        buildFile << """
            configurations.$customRoleBasedConf

            tasks.register('checkConfUsage') {
                assert configurations.custom.canBeConsumed == $consumable
                assert configurations.custom.canBeResolved == $resolvable
                assert configurations.custom.canBeDeclared == $declarable
                assert configurations.custom.deprecatedForConsumption == $consumptionDeprecated
                assert configurations.custom.deprecatedForResolution == $resolutionDeprecated
                assert configurations.custom.deprecatedForDeclarationAgainst == $declarationAgainstDeprecated
            }
        """

        expect:
        succeeds('checkConfUsage')

        where:
        role                    | customRoleBasedConf               || consumable  | resolvable    | declarable | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated
        'consumable'            | "consumable('custom')"            || true        | false         | false             | false                 | false                 | false
        'resolvable'            | "resolvable('custom')"            || false       | true          | false             | false                 | false                 | false
        'dependencyScope'       | "dependencyScope('custom')"       || false       | false         | true              | false                 | false                 | false
    }

    def "can prevent usage mutation of role-based configuration #configuration added by java plugin meant for consumption"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }
            configurations {
                $configuration {
                    assert canBeConsumed == true
                    preventUsageMutation()
                    canBeConsumed = false
                }
            }
        """

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure(configuration, 'Consumable')

        where:
        configuration << ['runtimeElements', 'apiElements']
    }

    def "can prevent usage mutation of role-based configuration #role"() {
        given:
        buildFile << """
            configurations.$customRoleBasedConf

            configurations.custom {
                preventUsageMutation()
                canBeResolved = !canBeResolved
            }
        """
        executer.noDeprecationChecks()

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure('custom', displayName)

        where:
        role                    | customRoleBasedConf               | displayName
        'consumable'            | "consumable('custom')"            | 'Consumable'
        'resolvable'            | "resolvable('custom')"            | 'Resolvable'
        'dependencyScope'       | "dependencyScope('custom')"       | 'Dependency Scope'
    }

    def "exhaustively try all new role-based creation syntax"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            configurations {
                consumable('consumable1')
                resolvable('resolvable1')
                dependencyScope('dependencyScope1')

                consumable('consumable2') { }
                resolvable('resolvable2') { }
                dependencyScope('dependencyScope2') { }
            }
        """

        expect:
        succeeds 'help'
    }

    def "locked configurations' usage can not be updated with all"() {
        given:
        buildFile << """
            configurations {
                $createCode
            }

            configurations.all {
                canBeResolved = !canBeResolved
                canBeConsumed = !canBeConsumed
                canBeDeclared = !canBeDeclared
            }
        """

        expect:
        fails 'help'
        assertUsageLockedFailure('conf', type)

        where:
        createCode                     | type
        "consumable('conf')"           | 'Consumable'
        "consumable('conf') { }"       | 'Consumable'
        "resolvable('conf')"           | 'Resolvable'
        "resolvable('conf') { }"       | 'Resolvable'
        "dependencyScope('conf')"      | 'Dependency Scope'
        "dependencyScope('conf') { }"  | 'Dependency Scope'
    }

    def "redundantly changing usage on a role-locked configuration emits warning when flag is set (#configuration - #method(#value))"() {
        given:
        buildFile << """
            configurations {
                consumable('cons')
                resolvable('res')
                dependencyScope('dep')
            }
            configurations.cons.canBeConsumed = true
            configurations.cons.canBeResolved = false
            configurations.cons.canBeDeclared = false
            configurations.res.canBeConsumed = false
            configurations.res.canBeResolved = true
            configurations.res.canBeDeclared = false
            configurations.dep.canBeConsumed = false
            configurations.dep.canBeResolved = false
            configurations.dep.canBeDeclared = true
        """
        expect:
        expectConsumableChanging(":cons", true)
        expectResolvableChanging(":cons", false)
        expectDeclarableChanging(":cons", false)
        expectConsumableChanging(":res", false)
        expectResolvableChanging(":res", true)
        expectDeclarableChanging(":res", false)
        expectConsumableChanging(":dep", false)
        expectResolvableChanging(":dep", false)
        expectDeclarableChanging(":dep", true)
        succeeds('help', "-Dorg.gradle.internal.deprecation.preliminary.Configuration.redundantUsageChangeWarning.enabled=true")
    }
    // endregion Role-Based Configurations

    // region Migrating configurations
    def "can add declaration alternatives to configuration deprecated for declaration"() {
        given:
        buildFile << """
            configurations {
                migratingLocked("testConf", org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_RESOLVABLE) {
                    addDeclarationAlternatives("anotherConf")
                }
            }

            dependencies {
                testConf "org:foo:1.0"
            }
        """
        expect:
        executer.expectDocumentedDeprecationWarning("The testConf configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 10. Please use the anotherConf configuration instead. For more information, please refer to https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
        succeeds 'help'
    }
    // endregion Migrating configurations

    // region Detached configurations

    // Note that this is not desired behavior and ideally any change to a detached configuration's
    // usage should fail, however we have to allow changes to false for now as KGP does this.
    def "changing usage #property = #change (change property to false) on detached configurations is permitted"() {
        given:
        buildFile << """
            def detached = project.configurations.detachedConfiguration()

            assert detached.canBeResolved
            assert detached.canBeDeclared

            detached.$property($change)
        """

        expect:
        run "help"

        where:
        property | change
        "setCanBeResolved" | false
        "setCanBeDeclared" | false
    }

    def "changing usage #property = #change (change property to true) on detached configurations fails"() {
        given:
        buildFile << """
            def detached = project.configurations.detachedConfiguration()

            assert !detached.canBeConsumed

            detached.$property($change)
        """

        when:
        fails "help"

        then:
        failure.assertHasDescription("A problem occurred evaluating root project '${buildFile.parentFile.name}'.")
        failure.assertHasCause("""Method call not allowed
  Calling $property($change) on configuration ':detachedConfiguration1' is not allowed.  This configuration's role was set upon creation and its usage should not be changed.""")

        where:
        property | change
        "setCanBeConsumed" | true
    }

    def "changing usage redundantly on detached configurations warns when flag is set"() {
        given:
        buildFile << """
            def detached = project.configurations.detachedConfiguration()

            assert detached.canBeResolved
            assert !detached.canBeConsumed
            assert detached.canBeDeclared

            detached.canBeResolved = true
            detached.canBeConsumed = false
            detached.canBeDeclared = true
        """

        expect:
        expectConsumableChanging(":detachedConfiguration1", false)
        expectResolvableChanging(":detachedConfiguration1", true)
        expectDeclarableChanging(":detachedConfiguration1", true)
        succeeds('help', "-Dorg.gradle.internal.deprecation.preliminary.Configuration.redundantUsageChangeWarning.enabled=true")
    }

    def "changing usage redundantly on detached configurations does NOT warn when flag is NOT set"() {
        given:
        buildFile << """
            def detached = project.configurations.detachedConfiguration()

            assert detached.canBeResolved
            assert !detached.canBeConsumed
            assert detached.canBeDeclared

            detached.canBeResolved = true
            detached.canBeConsumed = false
            detached.canBeDeclared = true
        """

        expect:
        succeeds('help')
    }
    // endregion Detached configurations
}
