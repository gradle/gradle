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
        configuration << ["legacy", "consumable", "resolvable", "consumableUnlocked", "resolvableUnlocked", "dependencyScopeUnlocked"]
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

    def "can add declaration alternatives to configuration deprecated for declaration"() {
        given:
        buildFile << """
            configurations {
                migratingUnlocked("testConf", org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_RESOLVABLE) {
                    addDeclarationAlternatives("anotherConf")
                }
            }

            dependencies {
                testConf "org:foo:1.0"
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The testConf configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 9.0. Please use the anotherConf configuration instead. For more information, please refer to https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
        succeeds 'help'
    }

    def "can add resolution alternatives to configuration deprecated for resolution"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        buildFile << """
            configurations {
                deps
                migratingUnlocked("testConf", org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.LEGACY_TO_CONSUMABLE) {
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
        executer.expectDocumentedDeprecationWarning("The testConf configuration has been deprecated for resolution. This will fail with an error in Gradle 9.0. Please resolve the anotherConf configuration instead. For more information, please refer to https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
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

    def "configurations created by buildSrc automatically can have usage changed"() {
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
                    canBeConsumed = !canBeConsumed
                    canBeResolved = !canBeResolved
                    canBeDeclared = !canBeDeclared
                }
            }
        """

        buildFile << """
            tasks.register('myTask', MyTask)
        """

        expect:
        expectConsumableChanging(':buildSrc:implementation', true)
        expectResolvableChanging(':buildSrc:implementation', true)
        expectDeclarableChanging(':buildSrc:implementation', false)
        succeeds 'myTask'
    }

    def "configurations can have usage changed from other projects"() {
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
                            canBeConsumed = !canBeConsumed
                            canBeResolved = !canBeResolved
                            canBeDeclared = !canBeDeclared
                        }
                    }
                }
            }
        """

        expect:
        expectConsumableChanging(':projectA:implementation', true)
        expectResolvableChanging(':projectA:implementation', true)
        expectDeclarableChanging(':projectA:implementation', false)
        expectConsumableChanging(':projectB:implementation', true)
        expectResolvableChanging(':projectB:implementation', true)
        expectDeclarableChanging(':projectB:implementation', false)
        succeeds 'help'
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
    // endregion Role-Based Configurations

    // region Warnings
    def "changing usage for configuration #configuration produces warnings"() {
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

        expect: "the build succeeds and a deprecation warning is logged"
        expectResolvableChanging(":$configuration", true)
        succeeds 'help'

        where: "a non-exhaustive list of configurations is tested"
        configuration << ['api', 'implementation', 'compileOnly', 'runtimeOnly', 'archives']
    }

    def "setting consumable = false is deprecated for consumable configurations added by java plugin"() {
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

        expect: "the build succeeds and a deprecation warning is logged if the configuration is not allowed to change"
        expectConsumableChanging(":$configuration", false)
        succeeds 'help'

        where: "a non-exhaustive list of configurations is tested"
        configuration << ['default', 'archives', 'apiElements', 'runtimeElements']
    }

    def "changing consumable to true always warns for non-LEGACY configurations (can not change #configuration usage)"() {
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

        expect:
        expectConsumableChanging(":$configuration", true)
        succeeds 'help'

        where:
        configuration << ['api', 'implementation', 'runtimeOnly', 'compileOnly', 'compileOnlyApi', 'runtimeClasspath', 'compileClasspath']
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

    def "using a reserved configuration name emits a deprecation warning if JavaBasePlugin applied"() {
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
                    project.configurations.create('customCompileOnly')

                    project.configurations.create('implementation')
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

        expect:
        executer.expectDocumentedDeprecationWarning("The configuration customCompileOnly was created explicitly. This configuration name is reserved for creation by Gradle. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Do not create a configuration with the name customCompileOnly. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
        executer.expectDocumentedDeprecationWarning("Gradle will mutate the usage of configuration customCompileOnly to match the expected usage. This may cause unexpected behavior. Creating configurations with reserved names has been deprecated. This will fail with an error in Gradle 9.0. Create source set custom prior to creating or accessing the configurations associated with it. For more information, please refer to https://docs.gradle.org/current/userguide/building_java_projects.html#sec:implicit_sourceset_configurations in the Gradle documentation.")
        executer.expectDocumentedDeprecationWarning("The configuration implementation was created explicitly. This configuration name is reserved for creation by Gradle. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Do not create a configuration with the name implementation. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
        executer.expectDocumentedDeprecationWarning("Gradle will mutate the usage of configuration implementation to match the expected usage. This may cause unexpected behavior. Creating configurations with reserved names has been deprecated. This will fail with an error in Gradle 9.0. Create source set main prior to creating or accessing the configurations associated with it. For more information, please refer to https://docs.gradle.org/current/userguide/building_java_projects.html#sec:implicit_sourceset_configurations in the Gradle documentation.")
        succeeds 'help'
    }

    @Issue("https://github.com/gradle/gradle/issues/26461")
    def "when anticipating configurations to be created from sourcesets, their usage is reset (creation = #description)"() {
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
        executer.expectDocumentedDeprecationWarning("The configuration additionalRuntimeClasspath was created explicitly. This configuration name is reserved for creation by Gradle. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Do not create a configuration with the name additionalRuntimeClasspath. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
        executer.expectDocumentedDeprecationWarning("Gradle will mutate the usage of configuration additionalRuntimeClasspath to match the expected usage. This may cause unexpected behavior. Creating configurations with reserved names has been deprecated. This will fail with an error in Gradle 9.0. Create source set additional prior to creating or accessing the configurations associated with it. For more information, please refer to https://docs.gradle.org/current/userguide/building_java_projects.html#sec:implicit_sourceset_configurations in the Gradle documentation.")
        if (canMutate) {
            succeeds "resolve"
        } else {
            fails "resolve"
            failure.assertHasErrorOutput("Gradle cannot mutate the usage of configuration 'additionalRuntimeClasspath' because it is locked.")
        }

        where:
        confCreationCode | createdRole | canMutate | description
        """
            configurations {
                additionalRuntimeClasspath
            }
        """                                                                             | ConfigurationRoles.LEGACY     | true      | "legacy configuration with implicit allowed usage"
        """
            configurations {
                additionalRuntimeClasspath {
                    canBeConsumed = true
                }
            }
        """                                                                             | ConfigurationRoles.LEGACY     | true      | "legacy configuration with explicit set consumed = true"
        "configurations.consumable('additionalRuntimeClasspath')"                       | ConfigurationRoles.CONSUMABLE | false     | "role-based configuration"
        "configurations.consumableUnlocked('additionalRuntimeClasspath')"               | ConfigurationRoles.CONSUMABLE | true      | "internal unlocked role-based configuration"
        "configurations.maybeCreateConsumableUnlocked('additionalRuntimeClasspath')"    | ConfigurationRoles.CONSUMABLE | true      | "internal unlocked role-based configuration, if it doesn't already exist"
    }

    def "changing usage on detached configurations does not warn"() {
        given:
        buildFile << """
            def detached = project.configurations.detachedConfiguration()

            assert detached.canBeConsumed
            assert detached.canBeResolved
            assert detached.canBeDeclared

            detached.canBeResolved = false
            detached.canBeConsumed = false
            detached.canBeDeclared = false
        """

        expect:
        run "help"
    }

    def "changing usage on detached configurations warns when flag is set"() {
        given:
        buildFile << """
            def detached = project.configurations.detachedConfiguration()

            detached.canBeResolved = false
            detached.canBeConsumed = false
            detached.canBeDeclared = false
        """

        expect:
        expectConsumableChanging(":detachedConfiguration1", false)
        expectResolvableChanging(":detachedConfiguration1", false)
        expectDeclarableChanging(":detachedConfiguration1", false)
        succeeds('help', "-Dorg.gradle.internal.deprecation.preliminary.Configuration.redundantUsageChangeWarning.enabled=true")
    }

    def "redundantly changing usage on a role-locked configuration warns when flag is set"() {
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

    def "redundantly changing usage on a legacy configuration does not warn"() {
        given:
        buildFile << """
            def test = configurations.create('test')
            test.setCanBeConsumed(true)
            test.setCanBeResolved(true)
            test.setCanBeDeclared(true)
        """

        expect:
        succeeds('help', "-Dorg.gradle.internal.deprecation.preliminary.Configuration.redundantUsageChangeWarning.enabled=true")
    }
    // endregion Warnings

    private void assertUsageLockedFailure(String configurationName, String roleName = null) {
        String suffix = roleName ? "as it was locked upon creation to the role: '$roleName'." : "as it has been locked."
        failure.assertHasCause("Cannot change the allowed usage of configuration ':$configurationName', $suffix")
    }
}
