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
import org.gradle.integtests.fixtures.versions.SmokeTestedPluginVersions
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

    protected static final SmokeTestedPluginVersions SMOKE_TESTED_PLUGINS = new SmokeTestedPluginVersions()

    static class TestedVersions {
        // https://developer.android.com/studio/releases/gradle-plugin
        // Update by running `./gradlew updateAgpVersions`
        static androidGradle = Versions.of(*AGP_VERSIONS.latestsPlusNightly)

        // https://search.maven.org/search?q=g:org.jetbrains.kotlin%20AND%20a:kotlin-project&core=gav
        // Update by running `./gradlew updateKotlinVersions`
        static kotlin = Versions.of(*KOTLIN_VERSIONS.latests)

        // Update by running `./gradlew updateSmokeTestedPluginVersions`
        static bnd = SMOKE_TESTED_PLUGINS.get("biz.aQute.bnd")
        static nebulaDependencyRecommender = SMOKE_TESTED_PLUGINS.get("com.netflix.nebula.dependency-recommender")
        static nebulaPluginPlugin = SMOKE_TESTED_PLUGINS.get("com.netflix.nebula.plugin-plugin")
        static nebulaLint = SMOKE_TESTED_PLUGINS.get("com.netflix.nebula.lint")
        static ideaExt = SMOKE_TESTED_PLUGINS.get("org.jetbrains.gradle.plugin.idea-ext")
        static nebulaDependencyLock = SMOKE_TESTED_PLUGINS.get("com.netflix.nebula.dependency-lock")
        static nebulaResolutionRules = SMOKE_TESTED_PLUGINS.get("com.netflix.nebula.resolution-rules")
        static shadow = SMOKE_TESTED_PLUGINS.get("com.gradleup.shadow")
        static asciidoctor = SMOKE_TESTED_PLUGINS.get("org.asciidoctor.jvm.convert")
        static spotbugs = SMOKE_TESTED_PLUGINS.get("com.github.spotbugs")
        static docker = SMOKE_TESTED_PLUGINS.get("com.bmuschko.docker-java-application")
        static springDependencyManagement = SMOKE_TESTED_PLUGINS.get("io.spring.dependency-management")
        static springBoot = SMOKE_TESTED_PLUGINS.get("org.springframework.boot")
        static gretty = [
            [version: SMOKE_TESTED_PLUGINS.get("org.gretty"), servletContainer: "jetty11", javaMinVersion: JavaVersion.VERSION_11]
        ]
        static gradleVersions = SMOKE_TESTED_PLUGINS.get("com.github.ben-manes.versions")
        static playframework = SMOKE_TESTED_PLUGINS.get("org.gradle.playframework")
        static errorProne = SMOKE_TESTED_PLUGINS.get("net.ltgt.errorprone")
        static protobufPlugin = SMOKE_TESTED_PLUGINS.get("com.google.protobuf")
        static testRetryPlugin = SMOKE_TESTED_PLUGINS.get("org.gradle.test-retry")
        static aspectj = SMOKE_TESTED_PLUGINS.get("io.freefair.aspectj")
        static undercouchDownload = SMOKE_TESTED_PLUGINS.get("de.undercouch.download")
        static micronaut = SMOKE_TESTED_PLUGINS.get("io.micronaut.application")
        static gradleGitProperties = SMOKE_TESTED_PLUGINS.get("com.gorylenko.gradle-git-properties")
        static flyway = SMOKE_TESTED_PLUGINS.get("org.flywaydb.flyway")
        static detekt = SMOKE_TESTED_PLUGINS.get("io.gitlab.arturbosch.detekt")
        static spotless = SMOKE_TESTED_PLUGINS.get("com.diffplug.spotless")
        static jib = SMOKE_TESTED_PLUGINS.get("com.google.cloud.tools.jib")
        static lombok = SMOKE_TESTED_PLUGINS.get("io.freefair.lombok")
        static node = SMOKE_TESTED_PLUGINS.get("com.moowork.node")
        static newNode = SMOKE_TESTED_PLUGINS.get("com.github.node-gradle.node")
        static ktlint = SMOKE_TESTED_PLUGINS.get("org.jlleitschuh.gradle.ktlint")
        static ktlintIdea = SMOKE_TESTED_PLUGINS.get("org.jlleitschuh.gradle.ktlint-idea")
        static avro = SMOKE_TESTED_PLUGINS.get("com.github.davidmc24.gradle.plugin.avro")
        static nohttp = SMOKE_TESTED_PLUGINS.get("io.spring.nohttp")
        static jenkinsJpi = SMOKE_TESTED_PLUGINS.get("org.jenkins-ci.jpi")
        static paparazzi = SMOKE_TESTED_PLUGINS.get("app.cash.paparazzi")
        static palantirConsistentVersions = SMOKE_TESTED_PLUGINS.get("com.palantir.consistent-versions")
        static vanniktechMavenPublish = SMOKE_TESTED_PLUGINS.get("com.vanniktech.maven.publish")
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
            "-Dorg.gradle.java.installations.paths=${AvailableJavaHomes.getAvailableJvms().collect { it.javaHome.absolutePath }.join(",")}" as String,
            '-Dorg.gradle.java.installations.auto-detect=false',
            '-Dorg.gradle.java.installations.auto-download=false',
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

class SmokeTestedVersionsSanityCheck extends Specification {
    def specialPlugins = [
        AbstractSmokeTest.TestedVersions.androidGradle,
        AbstractSmokeTest.TestedVersions.kotlin,
    ].size()

    def "all configured plugins are used"() {
        def usedPlugins = AbstractSmokeTest.TestedVersions.declaredFields.findAll { !it.synthetic }.collect { it.name }
        expect:
        usedPlugins.size() - specialPlugins == AbstractSmokeTest.SMOKE_TESTED_PLUGINS.getPluginsCount()
    }
}
