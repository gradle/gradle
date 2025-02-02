/*
 * Copyright 2016 the original author or authors.
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

import org.apache.commons.io.FileUtils
import org.gradle.api.JavaVersion
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheMaxProblemsOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil
import spock.lang.Specification
import spock.lang.TempDir

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.createMirrorInitScript
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.gradlePluginRepositoryMirrorUrl
import static org.gradle.test.fixtures.dsl.GradleDsl.GROOVY
import static org.gradle.test.fixtures.server.http.MavenHttpPluginRepository.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY

abstract class AbstractSmokeTest extends Specification {

    protected static final AndroidGradlePluginVersions AGP_VERSIONS = new AndroidGradlePluginVersions()

    protected static final KotlinGradlePluginVersions KOTLIN_VERSIONS = new KotlinGradlePluginVersions()

    static class TestedVersions {
        /**
         * May also need to update
         * @see BuildScanPluginSmokeTest
         */

        // https://plugins.gradle.org/plugin/biz.aQute.bnd
        static bnd = "7.0.0"

        // https://plugins.gradle.org/plugin/com.netflix.nebula.dependency-recommender
        static nebulaDependencyRecommender = "12.5.0"

        // https://plugins.gradle.org/plugin/com.netflix.nebula.plugin-plugin
        static nebulaPluginPlugin = "21.2.0"

        // https://plugins.gradle.org/plugin/com.netflix.nebula.lint
        static nebulaLint = "19.0.3"

        // https://plugins.gradle.org/plugin/org.jetbrains.gradle.plugin.idea-ext
        static ideaExt = "1.1.8"

        // https://plugins.gradle.org/plugin/com.netflix.nebula.dependency-lock
        static nebulaDependencyLock = Versions.of("15.1.0")

        // https://plugins.gradle.org/plugin/com.netflix.nebula.resolution-rules
        static nebulaResolutionRules = "11.3.0"

        // https://plugins.gradle.org/plugin/com.gradleup.shadow
        static shadow = "8.3.4"

        // https://github.com/asciidoctor/asciidoctor-gradle-plugin/tags
        static asciidoctor = Versions.of("3.3.2", "4.0.3")

        // https://plugins.gradle.org/plugin/com.github.spotbugs
        static spotbugs = "6.0.20"

        // https://plugins.gradle.org/plugin/com.bmuschko.docker-java-application
        static docker = "9.4.0"

        // https://plugins.gradle.org/plugin/io.spring.dependency-management
        static springDependencyManagement = "1.1.6"

        // https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-gradle-plugin
        static springBoot = "3.3.2"

        // https://developer.android.com/studio/releases/build-tools
        static androidTools = "35.0.0"

        // https://developer.android.com/studio/releases/gradle-plugin
        // Update by running `./gradlew updateAgpVersions`
        static androidGradle = Versions.of(*AGP_VERSIONS.latestsPlusNightly)

        // https://search.maven.org/search?q=g:org.jetbrains.kotlin%20AND%20a:kotlin-project&core=gav
        // Update by running `./gradlew updateKotlinVersions`
        static kotlin = Versions.of(*KOTLIN_VERSIONS.latests)

        // https://plugins.gradle.org/plugin/org.gretty
        static gretty = [
            [version: "3.1.4", servletContainer: "jetty9.4", javaMinVersion: JavaVersion.VERSION_1_8, javaMaxVersion: JavaVersion.VERSION_20],
            [version: "4.1.5", servletContainer: "jetty11", javaMinVersion: JavaVersion.VERSION_11]
        ]

        // https://plugins.gradle.org/plugin/org.ajoberstar.grgit
        static grgit = "4.1.1"

        // https://plugins.gradle.org/plugin/com.github.ben-manes.versions
        static gradleVersions = "0.51.0"

        // https://plugins.gradle.org/plugin/org.gradle.playframework
        static playframework = "0.13" // Can't upgrade to 0.14 as it breaks CC compat - see https://github.com/gradle/playframework/issues/184

        // https://plugins.gradle.org/plugin/net.ltgt.errorprone
        static errorProne = "4.0.1"

        // https://plugins.gradle.org/plugin/com.google.protobuf
        static protobufPlugin = "0.9.4"

        // https://central.sonatype.com/artifact/com.google.protobuf/protobuf-java/versions
        static protobufTools = "4.27.3"

        // https://plugins.gradle.org/plugin/org.gradle.test-retry
        static testRetryPlugin = "1.5.10"

        // https://plugins.gradle.org/plugin/io.freefair.aspectj
        static aspectj = "8.12"

        // https://plugins.gradle.org/plugin/de.undercouch.download
        static undercouchDownload = Versions.of("5.6.0")

        // https://github.com/micronaut-projects/micronaut-gradle-plugin/releases
        static micronaut = "4.4.2"

        // https://plugins.gradle.org/plugin/com.gorylenko.gradle-git-properties
        static gradleGitProperties = Versions.of("2.4.2")

        // https://plugins.gradle.org/plugin/org.flywaydb.flyway
        static flyway = Versions.of("10.17.1")

        // https://plugins.gradle.org/plugin/net.ltgt.apt
        static apt = Versions.of("0.21")

        // https://plugins.gradle.org/plugin/io.gitlab.arturbosch.detekt
        static detekt = Versions.of("1.23.6")

        // https://plugins.gradle.org/plugin/com.diffplug.spotless
        static spotless = Versions.of("6.25.0")

        // https://plugins.gradle.org/plugin/com.google.cloud.tools.jib
        static jib = Versions.of("3.4.3")

        // https://plugins.gradle.org/plugin/io.freefair.lombok
        static lombok = Versions.of("8.6")

        // https://plugins.gradle.org/plugin/com.moowork.node
        static node = Versions.of("1.3.1")

        // https://plugins.gradle.org/plugin/com.github.node-gradle.node
        static newNode = Versions.of("7.0.2")

        // https://plugins.gradle.org/plugin/org.jlleitschuh.gradle.ktlint
        static ktlint = Versions.of("12.1.1")

        // https://plugins.gradle.org/plugin/org.jlleitschuh.gradle.ktlint-idea
        static ktlintIdea = Versions.of("11.6.1")

        // https://github.com/davidmc24/gradle-avro-plugin/releases
        static avro = Versions.of("1.9.1")

        // https://plugins.gradle.org/plugin/io.spring.nohttp
        static nohttp = Versions.of("0.0.11")

        // https://plugins.gradle.org/plugin/org.jenkins-ci.jpi
        static jenkinsJpi = Versions.of("0.50.0")

        // https://github.com/cashapp/paparazzi/releases/tag/1.3.5
        static paparazzi = "1.3.5"

        // https://mvnrepository.com/artifact/com.guardsquare/proguard-gradle
        static proguardGradle = "7.5.0"

        // https://plugins.gradle.org/plugin/com.palantir.consistent-versions
        static palantirConsistentVersions = "2.23.0"

        // https://github.com/etiennestuder/teamcity-build-scan-plugin/releases
        static teamCityGradlePluginRef = "v0.35"

        // https://github.com/jenkinsci/gradle-plugin/releases
        static jenkinsGradlePluginRef = "gradle-2.10"

        // https://github.com/gradle/gradle-enterprise-bamboo-plugin/releases
        static bambooGradlePluginRef = "gradle-enterprise-bamboo-plugin-1.2.0"
    }

    static class Versions implements Iterable<String> {
        static Versions of(String... versions) {
            new Versions(versions)
        }

        final List<String> versions

        private Versions(String... given) {
            versions = Arrays.asList(given)
        }

        @Override
        Iterator<String> iterator() {
            return versions.iterator()
        }
    }

    private static final String INIT_SCRIPT_LOCATION = "org.gradle.smoketests.init.script"

    @TempDir
    File testProjectDir
    File buildFile
    File settingsFile
    @TempDir
    File buildCacheDir

    def setup() {
        buildFile = new File(testProjectDir, defaultBuildFileName)
        settingsFile = new File(testProjectDir, "settings.gradle")
    }

    protected String getDefaultBuildFileName() {
        'build.gradle'
    }

    void withKotlinBuildFile() {
        buildFile = new File(testProjectDir, "${getDefaultBuildFileName()}.kts")
    }

    TestFile file(String filename) {
        def file = new TestFile(testProjectDir, filename)
        def parentDir = file.getParentFile()
        assert parentDir.isDirectory() || parentDir.mkdirs()

        file
    }

    SmokeTestGradleRunner runner(String... tasks) {
        def args = tasks.toList() +
            outputParameters() +
            repoMirrorParameters() +
            configurationCacheParameters() +
            toolchainParameters() +
            kotlinDslParameters()

        def jvmArgs = ["-Xmx8g", "-XX:MaxMetaspaceSize=1024m", "-XX:+HeapDumpOnOutOfMemoryError"]

        return new SmokeTestGradleRunner(
            IntegrationTestBuildContext.INSTANCE,
            args,
            jvmArgs,
            testProjectDir
        )
    }

    private List<String> configurationCacheParameters() {
        List<String> parameters = []
        if (GradleContextualExecuter.isConfigCache()) {
            def maxProblems = maxConfigurationCacheProblems()
            parameters += [
                "--${ConfigurationCacheOption.LONG_OPTION}".toString(),
                "-D${ConfigurationCacheMaxProblemsOption.PROPERTY_NAME}=$maxProblems".toString(),
            ]
            if (maxProblems > 0) {
                parameters += ["--${ConfigurationCacheProblemsOption.LONG_OPTION}=warn".toString(),]
            }
        }
        return parameters
    }

    private static List<String> outputParameters() {
        return [
            '--warning-mode=all',
            "-D${LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME}=false" as String,
        ]
    }

    private static List<String> repoMirrorParameters() {
        if (RepoScriptBlockUtil.isMirrorEnabled()) {
            String mirrorInitScriptPath = createMirrorInitScript().absolutePath
            return [
                '--init-script', mirrorInitScriptPath,
                "-D${PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY}=${gradlePluginRepositoryMirrorUrl()}" as String,
                "-D${INIT_SCRIPT_LOCATION}=${mirrorInitScriptPath}" as String,
            ]
        } else {
            return []
        }
    }

    private static List<String> toolchainParameters() {
        return [
            "-Porg.gradle.java.installations.paths=${AvailableJavaHomes.getAvailableJvms().collect { it.javaHome.absolutePath }.join(",")}" as String,
            '-Porg.gradle.java.installations.auto-detect=false',
            '-Porg.gradle.java.installations.auto-download=false',
        ]
    }

    private static List<String> kotlinDslParameters() {
        return [
            // Having this unset is now deprecated, will default to `false` in Gradle 9.0
            // TODO remove - see https://github.com/gradle/gradle/issues/26810
            '-Dorg.gradle.kotlin.dsl.skipMetadataVersionCheck=false',
        ]
    }

    protected int maxConfigurationCacheProblems() {
        return 0
    }

    protected void useSample(String sampleDirectory) {
        def smokeTestDirectory = new File(this.getClass().getResource(sampleDirectory).toURI())
        FileUtils.copyDirectory(smokeTestDirectory, testProjectDir)
    }

    protected void replaceVariablesInBuildFile(Map binding) {
        replaceVariablesInFile(binding, buildFile)
    }

    protected void replaceVariablesInFile(Map binding, File file) {
        String text = file.text
        binding.each { String var, String value ->
            text = text.replaceAll("\\\$${var}".toString(), value)
        }
        file.text = text
    }

    protected void setupLocalBuildCache() {
        settingsFile << """
            buildCache {
                local {
                    directory = new File("${TextUtil.normaliseFileSeparators(buildCacheDir.absolutePath)}")
                }
            }
        """
    }

    protected static String mavenCentralRepository(GradleDsl dsl = GROOVY) {
        RepoScriptBlockUtil.mavenCentralRepository(dsl)
    }

    protected static String googleRepository(GradleDsl dsl = GROOVY) {
        RepoScriptBlockUtil.googleRepository(dsl)
    }

    void copyRemoteProject(String remoteProject, File targetDir) {
        new TestFile(new File("build/$remoteProject")).copyTo(targetDir)
    }
}
