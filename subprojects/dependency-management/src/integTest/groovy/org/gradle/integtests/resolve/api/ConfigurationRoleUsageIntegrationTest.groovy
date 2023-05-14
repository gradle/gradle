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
        configuration << ["legacy", "consumable", "resolvable", "resolvableBucket", "consumableBucket", "bucket"]
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
                createWithRole("testConf", org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.RESOLVABLE_BUCKET_TO_RESOLVABLE) {
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
                createWithRole("testConf", org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.LEGACY_TO_CONSUMABLE) {
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
    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "intended usage is allowed for role-based configuration #role"() {
        given:
        buildFile << """
            configurations.$customRoleBasedConf

            tasks.register('checkConfUsage') {
                doLast {
                    assert configurations.custom.canBeConsumed == $consumable
                    assert configurations.custom.canBeResolved == $resolvable
                    assert configurations.custom.canBeDeclared == $declarable
                    assert configurations.custom.deprecatedForConsumption == $consumptionDeprecated
                    assert configurations.custom.deprecatedForResolution == $resolutionDeprecated
                    assert configurations.custom.deprecatedForDeclarationAgainst == $declarationAgainstDeprecated
                }
            }
        """

        expect:
        succeeds('checkConfUsage')

        where:
        role                    | customRoleBasedConf               || consumable  | resolvable    | declarable | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated
        'consumable'            | "consumable('custom')"            || true        | false         | false             | false                 | false                 | false
        'resolvable'            | "resolvable('custom')"            || false       | true          | false             | false                 | false                 | false
        'resolvableBucket'      | "resolvableBucket('custom')"      || false       | true          | true              | false                 | false                 | false
        'bucket'                | "bucket('custom')"                || false       | false         | true              | false                 | false                 | false
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
        'bucket'                | "bucket('custom')"                | 'Bucket'
    }

    def "exhaustively try all new role-based creation syntax"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            configurations {
                consumable('consumable1')
                resolvable('resolvable1')
                resolvableBucket('resolvableBucket1')
                bucket('bucket1')

                consumable('consumable2', true)
                resolvable('resolvable2', true)
                resolvableBucket('resolvableBucket2', true)
                bucket('bucket2', true)

                createWithRole('consumable3', ConfigurationRoles.CONSUMABLE)
                createWithRole('consumable4', ConfigurationRoles.CONSUMABLE, true)
                createWithRole('consumable5', ConfigurationRoles.CONSUMABLE, true) {
                    visible = false
                }
                createWithRole('consumable6', ConfigurationRoles.CONSUMABLE) {
                    visible = false
                }

                maybeCreateWithRole('resolvable7', ConfigurationRoles.RESOLVABLE, true, true)
            }
        """

        expect:
        succeeds 'help'
    }

    def "maybeCreateWithRole reuses existing roles"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            plugins {
                id 'java'
            }

            configurations {
                def existing = findByName('implementation')
                def result = maybeCreateWithRole('implementation', ConfigurationRoles.LEGACY, false, false)
                assert result == existing
            }
        """

        expect:
        succeeds 'help'
    }

    def "maybeCreateWithRole verifies usage of existing built-in roles when match is found, succeeding on match"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            plugins {
                id 'java'
            }

            configurations {
                assert findByName('implementation')
                assert maybeCreateWithRole('implementation', ConfigurationRoles.BUCKET, false, true)
            }
        """

        expect:
        succeeds 'help'
    }

    def "maybeCreateWithRole verifies usage of existing built-in configurations' roles when matching configuration is found, failing on mismatch"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            plugins {
                id 'java'
            }

            configurations {
                assert findByName('implementation')
                maybeCreateWithRole('implementation', ConfigurationRoles.RESOLVABLE, false, true)
            }
        """

        expect:
        fails 'help'
        result.assertHasErrorOutput("""Usage for configuration: implementation is not consistent with the role: Resolvable.
  Expected that it is:
  \tResolvable - this configuration can be resolved by this project to a set of files
  But is actually is:
  \tDeclarable - this configuration can have dependencies added to it""")
    }

    def "maybeCreateWithRole verifies usage of existing custom configurations' roles when matching configuration is found, failing on mismatch"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            configurations {
                consumable('custom')
                assert findByName('custom')
                maybeCreateWithRole('custom', ConfigurationRoles.RESOLVABLE, false, true)
            }
        """

        expect:
        fails 'help'
        result.assertHasErrorOutput("""Usage for configuration: custom is not consistent with the role: Resolvable.
  Expected that it is:
  \tResolvable - this configuration can be resolved by this project to a set of files
  But is actually is:
  \tConsumable - this configuration can be selected by another project as a dependency""")
    }

    def "maybeCreateWithRole can lock new roles"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            configurations {
                assert !findByName('custom')
                def result = maybeCreateWithRole('custom', ConfigurationRoles.RESOLVABLE, true, false)
                result.canBeResolved = !result.canBeResolved
            }
        """

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure('custom', 'Resolvable')
    }

    def "maybeCreateWithRole can lock existing roles"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            plugins {
                id 'java'
            }

            configurations {
                def existing = findByName('implementation')
                def result = maybeCreateWithRole('implementation', ConfigurationRoles.LEGACY, true, false)
                result.canBeResolved = !result.canBeResolved
            }
        """

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure('implementation', 'Bucket')
    }

    def "can update all roles for non-locked configurations"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            configurations {
                def c1 = createWithRole('c1', ConfigurationRoles.LEGACY)
                def c2 = createWithRole('c2', ConfigurationRoles.CONSUMABLE)
                def c3 = createWithRole('c3', ConfigurationRoles.RESOLVABLE)
                def c4 = createWithRole('c4', ConfigurationRoles.RESOLVABLE_BUCKET)
                def c5 = createWithRole('c5', ConfigurationRoles.CONSUMABLE_BUCKET)
                def c6 = createWithRole('c6', ConfigurationRoles.BUCKET)
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
                consumable('custom', true)
            }

            configurations.all {
                canBeResolved = !canBeResolved
                canBeConsumed = !canBeConsumed
                canBeDeclared = !canBeDeclared
            }
        """

        expect:
        fails 'help'
        assertUsageLockedFailure('custom', 'Consumable')
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
                $configuration {
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
        'mainSourceElements'    || false
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

    def "changing usage for custom configuration in the legacy role is allowed"() {
        given:
        buildFile << """
            configurations.createWithRole('custom', org.gradle.api.internal.artifacts.configurations.ConfigurationRoles.LEGACY)
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

    def "incorrect usage combinations properly log at #logLevel when #desc"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            configurations.$elementsCreationCode
            configurations.fooElements.preventFromFurtherMutation()
        """

        when:
        executer.noDeprecationChecks() // These are checked in other tests, this one is just concerned with the log output
        succeeds 'help', logLevel

        then:
        if (warningMessage) {
            assert output.contains(warningMessage)
        }

        where:
        desc                                                        | elementsCreationCode                                                  | logLevel      || warningMessage
        "using create to make an implicitly LEGACY configuration"   | "create('fooElements')"                                               | "--warn"      || null
        "using consumable to make a configuration"                  | "consumable('fooElements')"                                           | "--warn"      || null
        "using resolvable to make a configuration"                  | "resolvable('fooElements')"                                           | "--warn"      || null
        "using resolvable_bucket to make a configuration"           | "createWithRole('fooElements', ConfigurationRoles.RESOLVABLE_BUCKET)" | "--warn"      || null
        "using consumable_bucket to make a configuration"           | "createWithRole('fooElements', ConfigurationRoles.CONSUMABLE_BUCKET)" | "--warn"      || null
        "using create to make an implicitly LEGACY configuration"   | "create('fooElements')"                                               | "--info"      || null
        "using consumable to make a configuration"                  | "consumable('fooElements')"                                           | "--info"      || null
        "using resolvable to make a configuration"                  | "resolvable('fooElements')"                                           | "--info"      || null
        "using resolvable_bucket to make a configuration"           | "createWithRole('fooElements', ConfigurationRoles.RESOLVABLE_BUCKET)" | "--info"      || null
        "using consumable_bucket to make a configuration"           | "createWithRole('fooElements', ConfigurationRoles.CONSUMABLE_BUCKET)" | "--info"      || 'The configuration :fooElements is both consumable and declarable. This combination is incorrect, only one of these flags should be set.'
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
        desc                                                | confCreationCode                                                 | usage                | isSetMethod                   | setMethod
        "using consumable to make a configuration"          | "consumable('test')"                                             | "consumable"         | "isCanBeConsumed()"           | "setCanBeConsumed(true)"
        "using resolvable to make a configuration"          | "resolvable('test')"                                             | "resolvable"         | "isCanBeResolved()"           | "setCanBeResolved(true)"
        "using resolvable_bucket to make a configuration"   | "createWithRole('test', ConfigurationRoles.RESOLVABLE_BUCKET)"   | "resolvable"         | "isCanBeResolved()"           | "setCanBeResolved(true)"
        "using consumable_bucket to make a configuration"   | "createWithRole('test', ConfigurationRoles.CONSUMABLE_BUCKET)"   | "declarable"         | "isCanBeDeclared()"           | "setCanBeDeclared(true)"
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
