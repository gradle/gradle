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


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ConfigurationUsageChangingFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

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
        executer.expectDocumentedDeprecationWarning("The testConf configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 9.0. Please use the anotherConf configuration instead. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
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

            repositories { maven { url "${mavenRepo.uri}" } }

            dependencies {
                deps "org:foo:1.0"
            }

            task resolve {
                configurations.testConf.files
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The testConf configuration has been deprecated for resolution. This will fail with an error in Gradle 9.0. Please resolve the anotherConf configuration instead. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
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

    /**
     * This test ensures that the Kotlin plugin will not emit deprecation warnings when it prevents these configurations created by the
     * Java plugin from being consumed.
     *
     * @see <a href="https://github.com/JetBrains/kotlin/blob/4be359ba02fba4c5539ba50392126b5367fa9169/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/jvm/KotlinJvmTarget.kt#L101">KotlinJvmTarget.kt</a>
     */
    def "setting consumable = false is permitted without warning for special cases to support Kotlin plugin (can change #configuration usage without warning = #allowed)"() {
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
        if (!allowed) {
            expectConsumableChanging(":$configuration", false)
        }
        succeeds 'help'

        where: "a non-exhaustive list of configurations is tested"
        configuration           || allowed
        'apiElements'           || true
        'runtimeElements'       || true
        'default'               || false
        'archives'              || false
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
            apply plugin: 'my-plugin'
            apply plugin: 'java'
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The configuration customCompileOnly was created explicitly. This configuration name is reserved for creation by Gradle. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Do not create a configuration with this name. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
        executer.expectDocumentedDeprecationWarning("The configuration implementation was created explicitly. This configuration name is reserved for creation by Gradle. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Do not create a configuration with this name. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
        succeeds 'help'
    }

    def "changing usage on detached configurations does not warn"() {
        given:
        buildFile << """
            def detached = project.configurations.detachedConfiguration()
            assert detached.canBeResolved
            detached.canBeResolved = false
        """

        expect:
        run "help"
    }

    def "redundantly calling #setMethod on a configuration that is already #isSetMethod warns when #desc"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            configurations.$confCreationCode

            configurations.test {
                assert $isSetMethod
                $setMethod
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The $usage usage is already allowed on configuration ':test'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Remove the call to $setMethod, it has no effect. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#redundant_configuration_usage_activation")
        succeeds 'help'

        where:
        desc                                              | confCreationCode         | usage                | isSetMethod                   | setMethod
        "using consumable to make a configuration"        | "consumable('test')"     | "consumable"         | "isCanBeConsumed()"           | "setCanBeConsumed(true)"
        "using resolvable to make a configuration"        | "resolvable('test')"     | "resolvable"         | "isCanBeResolved()"           | "setCanBeResolved(true)"
        "using dependencyScope to make a configuration"   | "dependencyScope('test')"   | "declarable"         | "isCanBeDeclared()"           | "setCanBeDeclared(true)"
    }

    def "redundantly calling #setMethod on a configuration that is already #isSetMethod does not warn when #desc"() {
        given:
        buildFile << """
            def test = configurations.$confCreationCode
            assert test.$isSetMethod
            test.$setMethod
        """

        expect:
        succeeds 'help'

        where:
        desc                                                        | confCreationCode              | usage                | isSetMethod            | setMethod
        "using create to make an implicitly LEGACY configuration"   | "create('test')"              | "consumable"         | "isCanBeConsumed()"    | "setCanBeConsumed(true)"
        "creating a detachedConfiguration"                          | "detachedConfiguration()"     | "consumable"         | "isCanBeConsumed()"    | "setCanBeConsumed(true)"
    }
    // endregion Warnings

    private void assertUsageLockedFailure(String configurationName, String roleName = null) {
        String suffix = roleName ? "as it was locked upon creation to the role: '$roleName'." : "as it has been locked."
        failure.assertHasCause("Cannot change the allowed usage of configuration ':$configurationName', $suffix")
    }
}
