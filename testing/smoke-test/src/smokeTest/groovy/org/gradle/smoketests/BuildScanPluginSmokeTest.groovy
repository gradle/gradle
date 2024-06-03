/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

// https://plugins.gradle.org/plugin/com.gradle.develocity
class BuildScanPluginSmokeTest extends AbstractSmokeTest {

    enum CI {
        TEAM_CITY(
            AbstractSmokeTest.TestedVersions.teamCityGradlePluginRef,
            "https://raw.githubusercontent.com/etiennestuder/teamcity-build-scan-plugin/%s/agent/src/main/resources/build-scan-init.gradle",
            "teamCityBuildScanPlugin"
        ),
        JENKINS(
            AbstractSmokeTest.TestedVersions.jenkinsGradlePluginRef,
            "https://raw.githubusercontent.com/jenkinsci/gradle-plugin/%s/src/main/resources/hudson/plugins/gradle/injection/init-script.gradle",
            "jenkinsGradlePlugin"
        ),
        BAMBOO(
            AbstractSmokeTest.TestedVersions.bambooGradlePluginRef,
            "https://raw.githubusercontent.com/gradle/gradle-enterprise-bamboo-plugin/%s/src/main/resources/gradle-enterprise/gradle/gradle-enterprise-init-script.gradle",
            "ge-plugin"
        );

        String gitRef
        String urlTemplate
        String propPrefix

        CI(String gitRef, String urlTemplate, String propPrefix) {
            this.gitRef = gitRef
            this.urlTemplate = urlTemplate
            this.propPrefix = propPrefix
        }

        String getUrl() {
            return String.format(urlTemplate, gitRef)
        }
    }

    private static final Map<CI, String> CI_INJECTION_SCRIPT_CONTENTS = new ConcurrentHashMap<>()

    private static String getCiInjectionScriptContent(CI ci) {
        return CI_INJECTION_SCRIPT_CONTENTS.computeIfAbsent(ci) { new URL(it.getUrl()).getText(StandardCharsets.UTF_8.name()) }
    }

    private static final List<String> UNSUPPORTED = [
        "2.4.2",
        "2.4.1",
        "2.4",
        "2.3",
        "2.2.1",
        "2.2",
        "2.1",
        "2.0.2",
        "2.0.1",
        "2.0",
        "1.16",
        "1.15",
        "1.14"
    ]

    private static final List<String> SUPPORTED = [
        "3.0",
        "3.1",
        "3.1.1",
        "3.2",
        "3.2.1",
        "3.3",
        "3.3.1",
        "3.3.2",
        "3.3.3",
        "3.3.4",
        "3.4",
        "3.4.1",
        "3.5",
        "3.5.1",
        "3.5.2",
        "3.6",
        "3.6.1",
        "3.6.2",
        "3.6.3",
        "3.6.4",
        "3.7",
        "3.7.1",
        "3.7.2",
        "3.8",
        "3.8.1",
        "3.9",
        "3.10",
        "3.10.1",
        "3.10.2",
        "3.10.3",
        // "3.11", This doesn't work on Java 8, so let's not test it.
        "3.11.1",
        "3.11.2",
        "3.11.3",
        "3.11.4",
        "3.12",
        "3.12.1",
        "3.12.2",
        "3.12.3",
        "3.12.4",
        "3.12.5",
        "3.12.6",
        "3.13",
        "3.13.1",
        "3.13.2",
        "3.13.3",
        "3.13.4",
        "3.14",
        "3.14.1",
        "3.15",
        "3.15.1",
        "3.16",
        "3.16.1",
        "3.16.2",
        "3.17",
        "3.17.1",
        "3.17.2",
        "3.17.3",
        "3.17.4"
    ]

    // Current injection scripts support Develocity plugin 3.3 and above
    private static final List<String> SUPPORTED_BY_CI_INJECTION = SUPPORTED
        .findAll { VersionNumber.parse("3.3") <= VersionNumber.parse(it) }

    // This refers to GradleEnterprisePluginCheckInService and does not cover LegacyGradleEnterprisePluginCheckInService
    private static final VersionNumber FIRST_VERSION_SUPPORTING_CHECK_IN_SERVICE = VersionNumber.parse("3.4")

    private static final VersionNumber FIRST_VERSION_SUPPORTING_GRADLE_8_CONFIGURATION_CACHE = VersionNumber.parse("3.12")
    private static final VersionNumber FIRST_VERSION_SUPPORTING_ISOLATED_PROJECTS = VersionNumber.parse("3.15")
    private static final VersionNumber FIRST_VERSION_SUPPORTING_ISOLATED_PROJECTS_FOR_TEST_ACCELERATION = VersionNumber.parse("3.17")
    private static final VersionNumber FIRST_VERSION_CALLING_BUILD_PATH = VersionNumber.parse("3.13.1")
    private static final VersionNumber FIRST_VERSION_BUNDLING_TEST_RETRY_PLUGIN = VersionNumber.parse("3.12")
    private static final VersionNumber FIRST_VERSION_SUPPORTING_SAFE_MODE = VersionNumber.parse("3.15")
    private static final VersionNumber FIRST_VERSION_UNDER_DEVELOCITY_BRAND = VersionNumber.parse("3.17")

    private static final List<String> SUPPORTED_WITH_GRADLE_8_CONFIGURATION_CACHE = SUPPORTED
        .findAll { FIRST_VERSION_SUPPORTING_GRADLE_8_CONFIGURATION_CACHE <= VersionNumber.parse(it) }

    def "coverage at least up to auto-applied version"() {
        expect:
        VersionNumber.parse(AutoAppliedDevelocityPlugin.VERSION) <= VersionNumber.parse(SUPPORTED.last())
    }

    @Requires(value = IntegTestPreconditions.NotConfigCached, reason = "Usage with Configuration Cache is tested separately, because not all versions are supported")
    def "can use plugin #version"() {
        given:
        def versionNumber = VersionNumber.parse(version)

        when:
        usePluginVersion version

        then:
        scanRunner()
            .expectLegacyDeprecationWarningIf(FIRST_VERSION_SUPPORTING_CHECK_IN_SERVICE <= versionNumber && versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "Gradle Enterprise plugin $version has been deprecated. " +
                    "Starting with Gradle 9.0, only Gradle Enterprise plugin 3.13.1 or newer is supported. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#unsupported_ge_plugin_3.13"
            )
            .expectLegacyDeprecationWarningIf(versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "The BuildIdentifier.getName() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Use getBuildPath() to get a unique identifier for the build. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation"
            )
            .build().output.contains("Build scan written to")

        where:
        version << SUPPORTED
    }

    @Requires(IntegTestPreconditions.IsConfigCached)
    def "can use plugin #version with Gradle 8 configuration cache"() {
        given:
        def versionNumber = VersionNumber.parse(version)

        when:
        usePluginVersion version

        then:
        scanRunner()
            .expectLegacyDeprecationWarningIf(versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "Gradle Enterprise plugin $version has been deprecated. " +
                    "Starting with Gradle 9.0, only Gradle Enterprise plugin 3.13.1 or newer is supported. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#unsupported_ge_plugin_3.13"
            )
            .expectLegacyDeprecationWarningIf(versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "The BuildIdentifier.getName() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Use getBuildPath() to get a unique identifier for the build. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation"
            ).build().output.contains("Build scan written to")

        where:
        version << SUPPORTED_WITH_GRADLE_8_CONFIGURATION_CACHE
    }

    @Requires(value = IntegTestPreconditions.NotConfigCached, reason = "Isolated projects implies config cache")
    def "can use plugin #version with isolated projects"() {
        given:
        def versionNumber = VersionNumber.parse(version)

        when:
        usePluginVersion version

        then:
        scanRunner("-Dorg.gradle.unsafe.isolated-projects=true")
            .expectLegacyDeprecationWarningIf(versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "Gradle Enterprise plugin $version has been deprecated. " +
                    "Starting with Gradle 9.0, only Gradle Enterprise plugin 3.13.1 or newer is supported. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#unsupported_ge_plugin_3.13"
            )
            .expectLegacyDeprecationWarningIf(versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "The BuildIdentifier.getName() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Use getBuildPath() to get a unique identifier for the build. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation"
            ).build().output.contains("Build scan written to")

        where:
        // isolated projects requires configuration cache support
        version << SUPPORTED_WITH_GRADLE_8_CONFIGURATION_CACHE
            .findAll { FIRST_VERSION_SUPPORTING_ISOLATED_PROJECTS <= VersionNumber.parse(it) }
    }

    @Requires(value = IntegTestPreconditions.NotConfigCached, reason = "Isolated projects implies config cache")
    def "can use plugin #version with isolated projects and test acceleration features"() {
        when:
        usePluginVersion version
        ["project1", "project2"].each { projectName ->
            setupJavaProject(file(projectName)).with {
                buildFile << """
                    tasks.withType(Test).configureEach {
                        develocity {
                            testRetry {
                                maxRetries = 3
                            }
                        }
                    }

                """
            }
            settingsFile << """
                include ':${projectName}'
            """
        }
        settingsFile << """
            include ':project1'
            include ':project2'
        """


        then:
        scanRunner("-Dorg.gradle.unsafe.isolated-projects=true")
            .build().output.contains("Build scan written to")

        when:
        createTest(file("project1"), "MyTest1")
        createTest(file("project2"), "MyTest1")

        then:
        with(scanRunner("-Dorg.gradle.unsafe.isolated-projects=true")
            .build()) {
            output.contains("Build scan written to")
            output.contains("Reusing configuration cache.")
            task(":project1:test").outcome == TaskOutcome.SUCCESS
            task(":project2:test").outcome == TaskOutcome.SUCCESS
        }

        when:
        file("project1/build.gradle") << """
            println("Change a project so it's reconfigured")
        """
        createTest(file("project1"), "MyTest2")
        createTest(file("project2"), "MyTest2")

        then:
        with(scanRunner("-Dorg.gradle.unsafe.isolated-projects=true")
            .build()) {
            output.contains("Build scan written to")
            task(":project1:test").outcome == TaskOutcome.SUCCESS
            task(":project2:test").outcome == TaskOutcome.SUCCESS
        }

        where:
        // isolated projects requires configuration cache support
        version << SUPPORTED_WITH_GRADLE_8_CONFIGURATION_CACHE
            .findAll { VersionNumber.parse(it) >= FIRST_VERSION_SUPPORTING_ISOLATED_PROJECTS_FOR_TEST_ACCELERATION }
    }

    @Requires(value = IntegTestPreconditions.NotConfigCached, reason = "Isolated projects implies config cache")
    def "cannot use plugin #version with isolated projects"() {
        when:
        usePluginVersion version

        and:
        def output = scanRunner("-Dorg.gradle.unsafe.isolated-projects=true")
            .build().output

        then:
        output.contains("Gradle Enterprise plugin has been disabled as it is incompatible with the isolated projects feature")
        !output.contains("Build scan written to")

        where:
        // isolated projects requires configuration cache support
        version << SUPPORTED_WITH_GRADLE_8_CONFIGURATION_CACHE
            .findAll { VersionNumber.parse(it) < FIRST_VERSION_SUPPORTING_ISOLATED_PROJECTS }
    }

    def "cannot use plugin #version"() {
        when:
        usePluginVersion version

        and:
        def output = runner("--stacktrace")
            .buildAndFail().output

        then:
        output.contains(GradleEnterprisePluginManager.OLD_SCAN_PLUGIN_VERSION_MESSAGE)

        where:
        version << UNSUPPORTED
    }

    @Requires(IntegTestPreconditions.NotConfigCached)
    def "can inject plugin #pluginVersion in #ci using '#ciScriptVersion' script version"() {
        def versionNumber = VersionNumber.parse(pluginVersion)
        def initScript = "init-script.gradle"
        file(initScript) << getCiInjectionScriptContent(ci)

        // URL is not relevant as long as it's valid due to the `-Dscan.dump` parameter
        file("gradle.properties") << """
            systemProp.${ci.propPrefix}.gradle-enterprise.plugin.version=$pluginVersion
            systemProp.${ci.propPrefix}.init-script.name=$initScript
            systemProp.${ci.propPrefix}.gradle-enterprise.url=http://localhost:5086
        """.stripIndent()

        setupLocalBuildCache()
        setupJavaProject()
        if (doesNotBundleTestRetryPluginOrSupportsSafeMode(versionNumber)) {
            new TestFile(buildFile).with {
                touch()
                prepend("""
                    plugins {
                        id "org.gradle.test-retry" version "${TestedVersions.testRetryPlugin}"
                    }
                """)
            }
        }

        expect:
        scanRunner("--init-script", initScript)
            .maybeExpectLegacyDeprecationWarningIf(FIRST_VERSION_UNDER_DEVELOCITY_BRAND <= versionNumber,
                "WARNING: The following functionality has been deprecated and will be removed in the next major release of the Develocity Gradle plugin:")
            .maybeExpectLegacyDeprecationWarningIf(FIRST_VERSION_UNDER_DEVELOCITY_BRAND <= versionNumber,
                "WARNING: The following functionality has been deprecated and will be removed in the next major release of the Develocity Gradle plugin. " +
                    "For assistance with migration, see https://gradle.com/help/gradle-plugin-develocity-migration.")
            .maybeExpectLegacyDeprecationWarningIf(FIRST_VERSION_UNDER_DEVELOCITY_BRAND <= versionNumber,
                "WARNING: The following functionality has been deprecated and will be removed in the next major release of the Develocity Gradle plugin. Run with '-Ddevelocity.deprecation.captureOrigin=true' to see where the deprecated functionality is being used. " +
                    "For assistance with migration, see https://gradle.com/help/gradle-plugin-develocity-migration.")
            .expectLegacyDeprecationWarningIf(FIRST_VERSION_SUPPORTING_CHECK_IN_SERVICE <= versionNumber && versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "Gradle Enterprise plugin $pluginVersion has been deprecated. " +
                    "Starting with Gradle 9.0, only Gradle Enterprise plugin 3.13.1 or newer is supported. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#unsupported_ge_plugin_3.13"
            )
            .expectLegacyDeprecationWarningIf(versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "The BuildIdentifier.getName() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Use getBuildPath() to get a unique identifier for the build. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation"
            )
            .build().output.contains("Build scan written to")

        where:
        [ci, pluginVersion] << [CI.values(), SUPPORTED_BY_CI_INJECTION].combinations()
        ciScriptVersion = ci.gitRef
    }

    private boolean doesNotBundleTestRetryPluginOrSupportsSafeMode(VersionNumber pluginVersion) {
        pluginVersion < FIRST_VERSION_BUNDLING_TEST_RETRY_PLUGIN || pluginVersion >= FIRST_VERSION_SUPPORTING_SAFE_MODE
    }

    BuildResult build(String... args) {
        scanRunner(args).build()
    }

    SmokeTestGradleRunner scanRunner(String... args) {
        // Run with --build-cache to test also build-cache events
        runner("build", "-Dscan.dump", "--build-cache", *args).forwardOutput()
    }

    void usePluginVersion(String version) {
        def develocityPlugin = VersionNumber.parse(version) >= VersionNumber.parse("3.17")
        def gradleEnterprisePlugin = VersionNumber.parse(version) >= VersionNumber.parse("3.0")
        if (develocityPlugin) {
            settingsFile << """
                plugins {
                    id "com.gradle.develocity" version "$version"
                }

                develocity {
                    buildScan {
                        termsOfUseUrl = 'https://gradle.com/help/legal-terms-of-use'
                        termsOfUseAgree = 'yes'
                    }
                }
            """
        } else if (gradleEnterprisePlugin) {
            settingsFile << """
                plugins {
                    id "com.gradle.enterprise" version "$version"
                }

                gradleEnterprise {
                    buildScan {
                        termsOfServiceUrl = 'https://gradle.com/terms-of-service'
                        termsOfServiceAgree = 'yes'
                    }
                }
            """
        } else {
            buildFile << """
                plugins {
                    id "com.gradle.build-scan" version "$version"
                }

                buildScan {
                    termsOfServiceUrl = 'https://gradle.com/terms-of-service'
                    termsOfServiceAgree = 'yes'
                }
            """
        }

        setupLocalBuildCache()
        setupJavaProject()
    }

    private TestFile setupJavaProject(TestFile projectDir = new TestFile(testProjectDir)) {
        projectDir.file("build.gradle") << """
            apply plugin: 'java'
            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """

        projectDir.file("src/main/java/MySource.java") << """
            public class MySource {
                public static boolean isTrue() { return true; }
            }
        """

        createTest(projectDir, "MyTest")
        projectDir
    }

    void createTest(TestFile projectDir, String testName) {
        projectDir.file("src/test/java/${testName}.java") << """
            import org.junit.*;

            public class ${testName} {
               @Test
               public void test() {
                  Assert.assertTrue(MySource.isTrue());
               }
            }
        """
    }
}
