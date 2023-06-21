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
import org.gradle.integtests.fixtures.BuildOperationTreeFixture
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheBuildOperationsFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler
import org.gradle.internal.operations.trace.BuildOperationTrace
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.DefaultGradleRunner
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
        static bnd = "6.4.0"

        // https://plugins.gradle.org/plugin/com.netflix.nebula.dependency-recommender
        static nebulaDependencyRecommender = "12.2.0"

        // https://plugins.gradle.org/plugin/com.netflix.nebula.plugin-plugin
        static nebulaPluginPlugin = "20.7.5"

        // https://plugins.gradle.org/plugin/nebula.lint
        static nebulaLint = "18.0.3"

        // https://plugins.gradle.org/plugin/org.jetbrains.gradle.plugin.idea-ext
        static ideaExt = "1.1.7"

        // https://plugins.gradle.org/plugin/com.netflix.nebula.dependency-lock
        static nebulaDependencyLock = Versions.of("13.2.1")

        // https://plugins.gradle.org/plugin/com.netflix.nebula.resolution-rules
        static nebulaResolutionRules = "10.2.0"

        // https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow
        static shadow = Versions.of("8.1.1")

        // https://github.com/asciidoctor/asciidoctor-gradle-plugin/tags
        static asciidoctor = Versions.of("3.3.2", "4.0.0-alpha.1")

        // https://plugins.gradle.org/plugin/com.github.spotbugs
        static spotbugs = "5.0.14"

        // https://plugins.gradle.org/plugin/com.bmuschko.docker-java-application
        static docker = "9.3.1"

        // https://plugins.gradle.org/plugin/io.spring.dependency-management
        static springDependencyManagement = "1.1.0"

        // https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-gradle-plugin
        static springBoot = "2.7.11"

        // https://developer.android.com/studio/releases/build-tools
        static androidTools = "33.0.2"
        // https://developer.android.com/studio/releases/gradle-plugin
        static androidGradle = Versions.of(*AGP_VERSIONS.latestsPlusNightly)

        // https://search.maven.org/search?q=g:org.jetbrains.kotlin%20AND%20a:kotlin-project&core=gav
        static kotlin = Versions.of(*KOTLIN_VERSIONS.latests)

        // https://plugins.gradle.org/plugin/org.gretty
        static gretty = [
            [version: "3.1.1", servletContainer: "jetty9.4", javaMinVersion: JavaVersion.VERSION_1_8],
            [version: "4.0.3", servletContainer: "jetty11", javaMinVersion: JavaVersion.VERSION_11]
        ]

        // https://plugins.gradle.org/plugin/org.ajoberstar.grgit
        static grgit = "4.1.1"

        // https://plugins.gradle.org/plugin/com.github.ben-manes.versions
        static gradleVersions = "0.46.0"

        // https://plugins.gradle.org/plugin/org.gradle.playframework
        static playframework = "0.13"

        // https://plugins.gradle.org/plugin/net.ltgt.errorprone
        static errorProne = "3.1.0"

        // https://plugins.gradle.org/plugin/com.google.protobuf
        static protobufPlugin = "0.9.2"

        // https://central.sonatype.com/artifact/com.google.protobuf/protobuf-java/4.0.0-rc-2/versions
        static protobufTools = "3.22.3"

        // https://plugins.gradle.org/plugin/org.gradle.test-retry
        static testRetryPlugin = "1.5.2"

        // https://plugins.gradle.org/plugin/com.jfrog.artifactory
        static artifactoryPlugin = "4.31.9"

        // https://docker.bintray.io/ui/packages/gav:%2F%2Forg.artifactory.oss.docker:artifactory-oss-docker?name=artifactory-oss&type=packages
        static artifactoryRepoOSSVersion = "6.23.21"

        // https://plugins.gradle.org/plugin/io.freefair.aspectj
        static aspectj = "8.0.1"

        // https://plugins.gradle.org/plugin/de.undercouch.download
        static undercouchDownload = Versions.of("5.4.0")

        // https://github.com/micronaut-projects/micronaut-gradle-plugin/releases
        static micronaut = "3.7.8"

        // https://plugins.gradle.org/plugin/com.gorylenko.gradle-git-properties
        static gradleGitProperties = Versions.of("2.4.1")

        // https://plugins.gradle.org/plugin/org.flywaydb.flyway
        static flyway = Versions.of("9.16.3")

        // https://plugins.gradle.org/plugin/net.ltgt.apt
        static apt = Versions.of("0.21")

        // https://plugins.gradle.org/plugin/io.gitlab.arturbosch.detekt
        static detekt = Versions.of("1.22.0")

        // https://plugins.gradle.org/plugin/com.diffplug.spotless
        static spotless = Versions.of("6.18.0")

        // https://plugins.gradle.org/plugin/com.google.cloud.tools.jib
        static jib = Versions.of("3.3.1")

        // https://plugins.gradle.org/plugin/io.freefair.lombok
        static lombok = Versions.of("8.0.1")

        // https://plugins.gradle.org/plugin/com.moowork.grunt
        // https://plugins.gradle.org/plugin/com.moowork.gulp
        // https://plugins.gradle.org/plugin/com.moowork.node
        static node = Versions.of("1.3.1")

        // https://plugins.gradle.org/plugin/com.github.node-gradle.node
        static newNode = Versions.of("4.0.0")

        // https://plugins.gradle.org/plugin/org.jlleitschuh.gradle.ktlint
        static ktlint = Versions.of("11.3.2")

        // https://github.com/davidmc24/gradle-avro-plugin
        static avro = Versions.of("1.7.0")

        // https://plugins.gradle.org/plugin/io.spring.nohttp
        static nohttp = Versions.of("0.0.11")

        // https://plugins.gradle.org/plugin/org.jenkins-ci.jpi
        static jenkinsJpi = Versions.of("0.48.0")

        // https://mvnrepository.com/artifact/com.guardsquare/proguard-gradle
        static proguardGradle = "7.3.2"

        // https://plugins.gradle.org/plugin/com.palantir.consistent-versions
        static palantirConsistentVersions = "2.12.0"

        // https://github.com/etiennestuder/teamcity-build-scan-plugin/releases
        static teamCityGradlePluginRef = "v0.33"

        // https://github.com/jenkinsci/gradle-plugin/releases
        static jenkinsGradlePluginRef = "gradle-2.8"

        // https://github.com/gradle/gradle-enterprise-bamboo-plugin/releases
        static bambooGradlePluginRef = "gradle-enterprise-bamboo-plugin-1.1.0"
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
        def gradleRunner = GradleRunner.create()
            .withGradleInstallation(IntegrationTestBuildContext.INSTANCE.gradleHomeDir)
            .withTestKitDir(IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir)
            .withProjectDir(testProjectDir)
            .forwardOutput()
            .withArguments(
                tasks.toList() + outputParameters() + repoMirrorParameters() + configurationCacheParameters() + toolchainParameters()
            ) as DefaultGradleRunner
        gradleRunner.withJvmArguments(["-Xmx8g", "-XX:MaxMetaspaceSize=1024m", "-XX:+HeapDumpOnOutOfMemoryError"])
        return new SmokeTestGradleRunner(gradleRunner)
    }

    private List<String> configurationCacheParameters() {
        List<String> parameters = []
        if (GradleContextualExecuter.isConfigCache()) {
            def maxProblems = maxConfigurationCacheProblems()
            parameters += [
                "--${ConfigurationCacheOption.LONG_OPTION}".toString(),
                "-D${ConfigurationCacheMaxProblemsOption.PROPERTY_NAME}=$maxProblems".toString(),
                "-D${BuildOperationTrace.SYSPROP}=${buildOperationTracePath()}".toString()
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
        String mirrorInitScriptPath = createMirrorInitScript().absolutePath
        return [
            '--init-script', mirrorInitScriptPath,
            "-D${PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY}=${gradlePluginRepositoryMirrorUrl()}" as String,
            "-D${INIT_SCRIPT_LOCATION}=${mirrorInitScriptPath}" as String,
        ]
    }

    private static List<String> toolchainParameters() {
        return [
            "-Porg.gradle.java.installations.paths=${AvailableJavaHomes.getAvailableJvms().collect { it.javaHome.absolutePath }.join(",")}" as String,
            '-Porg.gradle.java.installations.auto-detect=false',
            '-Porg.gradle.java.installations.auto-download=false',
        ]
    }

    protected int maxConfigurationCacheProblems() {
        return 0
    }

    protected void assertConfigurationCacheStateStored() {
        if (GradleContextualExecuter.isConfigCache()) {
            newConfigurationCacheBuildOperationsFixture().assertStateStored()
        }
    }

    protected void assertConfigurationCacheStateLoaded() {
        if (GradleContextualExecuter.isConfigCache()) {
            newConfigurationCacheBuildOperationsFixture().assertStateLoaded()
        }
    }

    private ConfigurationCacheBuildOperationsFixture newConfigurationCacheBuildOperationsFixture() {
        return new ConfigurationCacheBuildOperationsFixture(
            new BuildOperationTreeFixture(
                BuildOperationTrace.read(buildOperationTracePath())
            )
        )
    }

    private String buildOperationTracePath() {
        return file("operations").absolutePath
    }

    protected void useSample(String sampleDirectory) {
        def smokeTestDirectory = new File(this.getClass().getResource(sampleDirectory).toURI())
        FileUtils.copyDirectory(smokeTestDirectory, testProjectDir)
    }

    protected SmokeTestGradleRunner useAgpVersion(String agpVersion, SmokeTestGradleRunner runner) {
        def extraArgs = [AGP_VERSIONS.OVERRIDE_VERSION_CHECK]
        if (AGP_VERSIONS.isAgpNightly(agpVersion)) {
            def init = AGP_VERSIONS.createAgpNightlyRepositoryInitScript()
            extraArgs += ["-I", init.canonicalPath]
        }
        return runner.withArguments([runner.arguments, extraArgs].flatten())
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

    protected static String jcenterRepository(GradleDsl dsl = GROOVY) {
        RepoScriptBlockUtil.jcenterRepository(dsl)
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
