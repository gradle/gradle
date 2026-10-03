/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.tooling.r51


import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.BinaryPluginIdentifier
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ScriptPluginIdentifier
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationResult
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.TimeUnit

import static org.gradle.integtests.tooling.fixture.TextUtil.escapeString
import static org.junit.Assume.assumeTrue

@TargetGradleVersion('>=5.1')
class ProjectConfigurationProgressEventCrossVersionSpec extends ToolingApiSpecification {

    private static final long WORK_MILLIS = 100

    ProgressEvents events = ProgressEvents.create()

    @Rule
    public BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        createProjectSubDirs("buildSrc/a", "b", "included/c")
        file("buildSrc/settings.gradle") << """
            include 'a'
        """
        settingsFile << """
            rootProject.name = 'root'
            include 'b'
            includeBuild 'included'
        """
        file("included/settings.gradle") << """
            include 'c'
        """
    }

    def "reports successful project configuration progress events"() {
        when:
        runBuild("tasks")

        then:
        events.operations.size() == 6
        events.trees == events.operations
        containsSuccessfulProjectConfigurationOperation(":buildSrc", file("buildSrc"), ":")
        containsSuccessfulProjectConfigurationOperation(":buildSrc:a", file("buildSrc"), ":a")
        containsSuccessfulProjectConfigurationOperation(":", projectDir, ":")
        containsSuccessfulProjectConfigurationOperation(":b", projectDir, ":b")
        containsSuccessfulProjectConfigurationOperation(":included", file("included"), ":")
        containsSuccessfulProjectConfigurationOperation(":included:c", file("included"), ":c")
    }

    void containsSuccessfulProjectConfigurationOperation(String displayName, TestFile rootDir, String projectPath) {
        with(events.operation("Configure project $displayName")) {
            assert successful
            assertIsProjectConfiguration()
            assert descriptor.project.projectPath == projectPath
            assert descriptor.project.buildIdentifier.rootDir == rootDir
        }
    }

    def "reports failed project configuration progress events"() {
        given:
        buildFile << """
            throw new GradleException("something went horribly wrong")
        """

        when:
        runBuild("tasks")

        then:
        thrown(BuildException)
        with(events.operation("Configure project :")) {
            failed
            assertIsProjectConfiguration()
            failures.size() == 1
            with(failures[0]) {
                message == "A problem occurred configuring root project 'root'."
                description.contains("GradleException: something went horribly wrong")
            }
        }
    }

    def "does not report project configuration progress events when PROJECT_CONFIGURATION operations are not requested"() {
        when:
        runBuild("tasks", EnumSet.complementOf(EnumSet.of(OperationType.PROJECT_CONFIGURATION)))

        then:
        !events.operations.any { it.projectConfiguration }
    }

    def "reports plugin configuration results for binary plugins"() {
        given:
        file("buildSrc/build.gradle") << """
            allprojects {
                apply plugin: 'java'
            }
        """
        buildFile << """
            allprojects {
                apply plugin: 'java'
            }
        """
        file("included/build.gradle") << """
            allprojects {
                apply plugin: 'java'
            }
        """

        when:
        runBuild("tasks")

        then:
        if (targetVersion >= GradleVersion.version("9.9")) {
            // Plugin applications are attributed to the project the plugin is applied to,
            // rather than the project being configured when the application happens.
            // The buildSrc root project has the java plugin applied by class (without a plugin id)
            // before its build script runs, which is why the java application has no plugin ID.
            containsPluginApplicationResultsForJavaPlugin(":buildSrc", null)
            containsPluginApplicationResultsForJavaPlugin(":buildSrc:a")
            containsPluginApplicationResultsForJavaPlugin(":")
            containsPluginApplicationResultsForJavaPlugin(":b")
            containsPluginApplicationResultsForJavaPlugin(":included")
            containsPluginApplicationResultsForJavaPlugin(":included:c")
        } else {
            containsPluginApplicationResultsForJavaPlugin(":buildSrc")
            doesNotContainPluginApplicationResultsForJavaPlugin(":buildSrc:a")
            containsPluginApplicationResultsForJavaPlugin(":")
            doesNotContainPluginApplicationResultsForJavaPlugin(":b")
            containsPluginApplicationResultsForJavaPlugin(":included")
            doesNotContainPluginApplicationResultsForJavaPlugin(":included:c")
        }
    }

    def "reports plugin configuration results for script plugins"() {
        given:
        def escapedRootDir = escapeString(projectDir.absolutePath)
        file("script.gradle") << """
            apply plugin: 'java'
        """
        file("buildSrc/build.gradle") << """
            allprojects {
                apply from: "$escapedRootDir/script.gradle"
            }
        """
        buildFile << """
            allprojects {
                apply from: "$escapedRootDir/script.gradle"
            }
        """
        file("included/build.gradle") << """
            allprojects {
                apply from: "$escapedRootDir/script.gradle"
            }
        """

        when:
        runBuild("tasks")

        then:
        if (targetVersion >= GradleVersion.version("9.9")) {
            // Plugin applications are attributed to the project the plugin is applied to,
            // rather than the project being configured when the application happens.
            // Subprojects have no build script of their own, so they only contain results
            // for the applied script plugin and the plugins it applies.
            containsPluginApplicationResultsForJavaPluginAndScriptPlugins(":buildSrc", file("buildSrc"), null)
            containsScriptPluginApplicationResults(":buildSrc:a")
            containsPluginApplicationResultsForJavaPluginAndScriptPlugins(":", projectDir)
            containsScriptPluginApplicationResults(":b")
            containsPluginApplicationResultsForJavaPluginAndScriptPlugins(":included", file("included"))
            containsScriptPluginApplicationResults(":included:c")
        } else {
            containsPluginApplicationResultsForJavaPluginAndScriptPlugins(":buildSrc", file("buildSrc"))
            doesNotContainPluginApplicationResultsForJavaPluginAndScriptPlugins(":buildSrc:a")
            containsPluginApplicationResultsForJavaPluginAndScriptPlugins(":", projectDir)
            doesNotContainPluginApplicationResultsForJavaPluginAndScriptPlugins(":b")
            containsPluginApplicationResultsForJavaPluginAndScriptPlugins(":included", file("included"))
            doesNotContainPluginApplicationResultsForJavaPluginAndScriptPlugins(":included:c")
        }
    }

    def "reports plugin configuration results for subprojects"() {
        given:
        file("script.gradle") << """
            apply plugin: 'java'
        """
        file("b/build.gradle") << """
            apply from: "\$rootDir/script.gradle"
        """

        when:
        runBuild("tasks")

        then:
        containsPluginApplicationResultsForJavaPluginAndScriptPlugins(":b", file("b"))
    }

    def "reports plugin configuration results in reliable order"() {
        given:
        file("script.gradle") << """
            apply plugin: 'java'
        """
        buildFile << """
            apply from: "script.gradle"
        """

        when:
        runBuild("tasks")

        then:
        def plugins = getPluginConfigurationOperationResult(":").getPluginApplicationResults().collect { it.plugin.displayName }
        if (targetVersion >= GradleVersion.version("8.13")) {
            assert plugins == [
                "org.gradle.help-tasks",
                "org.gradle.software-reporting-tasks",
                "org.gradle.build-init", "org.gradle.wrapper",
                "build.gradle", "script.gradle",
                "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin",
                "org.gradle.api.plugins.BasePlugin",
                "org.gradle.language.base.plugins.LifecycleBasePlugin",
                "org.gradle.api.plugins.JvmEcosystemPlugin",
                "org.gradle.api.plugins.ReportingBasePlugin",
                "org.gradle.api.plugins.JvmToolchainsPlugin",
                "org.gradle.jvm-test-suite",
                "org.gradle.testing.base.plugins.TestSuiteBasePlugin"
            ]
        } else if (targetVersion >= GradleVersion.version("8.5")) {
            assert plugins == [
                "org.gradle.help-tasks", "org.gradle.build-init", "org.gradle.wrapper",
                "build.gradle", "script.gradle",
                "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin",
                "org.gradle.api.plugins.BasePlugin",
                "org.gradle.language.base.plugins.LifecycleBasePlugin",
                "org.gradle.api.plugins.JvmEcosystemPlugin",
                "org.gradle.api.plugins.ReportingBasePlugin",
                "org.gradle.api.plugins.JvmToolchainsPlugin",
                "org.gradle.jvm-test-suite",
                "org.gradle.testing.base.plugins.TestSuiteBasePlugin"
            ]
        } else if (targetVersion >= GradleVersion.version("7.6")) {
            assert plugins == [
                "org.gradle.help-tasks", "org.gradle.build-init", "org.gradle.wrapper",
                "build.gradle", "script.gradle",
                "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin",
                "org.gradle.api.plugins.BasePlugin",
                "org.gradle.language.base.plugins.LifecycleBasePlugin",
                "org.gradle.api.plugins.JvmEcosystemPlugin",
                "org.gradle.api.plugins.ReportingBasePlugin",
                "org.gradle.api.plugins.JvmToolchainsPlugin",
                "org.gradle.jvm-test-suite", "org.gradle.test-suite-base"
            ]
        } else if (targetVersion > GradleVersion.version("7.2")) {
            assert plugins == [
                "org.gradle.help-tasks", "org.gradle.build-init", "org.gradle.wrapper",
                "build.gradle", "script.gradle",
                "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin",
                "org.gradle.api.plugins.BasePlugin",
                "org.gradle.language.base.plugins.LifecycleBasePlugin",
                "org.gradle.api.plugins.JvmEcosystemPlugin",
                "org.gradle.api.plugins.ReportingBasePlugin",
                "org.gradle.jvm-test-suite", "org.gradle.test-suite-base"
            ]
        } else if (targetVersion >= GradleVersion.version("6.7")) {
            assert plugins == [
                "org.gradle.help-tasks", "org.gradle.build-init", "org.gradle.wrapper",
                "build.gradle", "script.gradle",
                "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin",
                "org.gradle.api.plugins.BasePlugin",
                "org.gradle.language.base.plugins.LifecycleBasePlugin",
                "org.gradle.api.plugins.JvmEcosystemPlugin",
                "org.gradle.api.plugins.ReportingBasePlugin"
            ]
        } else if (targetVersion > GradleVersion.version("5.5.1")) {
            assert plugins == [
                "org.gradle.help-tasks", "org.gradle.build-init", "org.gradle.wrapper",
                "build.gradle", "script.gradle",
                "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin",
                "org.gradle.api.plugins.BasePlugin",
                "org.gradle.language.base.plugins.LifecycleBasePlugin",
                "org.gradle.api.plugins.ReportingBasePlugin"
            ]
        } else {
            assert plugins == [
                    "org.gradle.build-init", "org.gradle.wrapper", "org.gradle.help-tasks",
                    "build.gradle", "script.gradle",
                    "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin",
                    "org.gradle.api.plugins.BasePlugin",
                    "org.gradle.language.base.plugins.LifecycleBasePlugin",
                    "org.gradle.api.plugins.ReportingBasePlugin"
            ]
        }
    }

    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    def "reports plugin configuration results for remote script plugins"() {
        given:
        toolingApi.requireIsolatedUserHome() // So that the script is not cached
        server.start()
        def scriptUri = server.uri("script.gradle")
        server.expect(server.get("script.gradle").send("""
            apply plugin: 'java'
        """))
        file("build.gradle") << """
            apply from: '$scriptUri'
        """

        when:
        runBuild("tasks")

        then:
        def result = getPluginConfigurationOperationResult(":").getPluginApplicationResults().find { it.plugin.displayName == "script.gradle" }
        result.plugin instanceof ScriptPluginIdentifier
        result.plugin.uri == scriptUri
    }

    def "ignores non-project plugins"() {
        given:
        file("build.gradle") << """
            apply(plugin: MyPlugin, to: gradle)
            class MyPlugin implements Plugin<Gradle> {
                void apply(Gradle gradle) {}
            }
        """

        when:
        runBuild("tasks")

        then:
        getPluginConfigurationOperationResult(":").getPluginApplicationResults().findAll { it.plugin.displayName.contains("MyPlugin") }.empty
    }

    def "includes execution time of project evaluation listener callbacks"() {
        given:
        file("build.gradle") << """
            apply plugin: MyPlugin
            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.afterEvaluate {
                        ${simulateWork()}
                    }
                }
            }
        """

        when:
        runBuild("tasks")

        then:
        def pluginResults = getPluginConfigurationOperationResult(":").getPluginApplicationResults()
        def result = pluginResults.find { it.plugin.displayName.contains("MyPlugin") }
        result.totalConfigurationTime >= Duration.ofMillis(WORK_MILLIS)
    }

    def "includes execution time of container callbacks"() {
        given:
        file("build.gradle") << """
            apply plugin: MyPlugin

            configurations {
                foo
            }

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.configurations.all {
                        if (name == 'foo') {
                            ${simulateWork()}
                        }
                    }
                }
            }
        """

        when:
        runBuild("tasks", EnumSet.of(OperationType.PROJECT_CONFIGURATION))

        then:
        def pluginResults = getPluginConfigurationOperationResult(":").getPluginApplicationResults()
        def result = pluginResults.find { it.plugin.displayName.contains("MyPlugin") }
        result.totalConfigurationTime >= Duration.ofMillis(WORK_MILLIS)
    }

    def "attributes plugins applied from a settings #callback callback to the project they are applied to"() {
        given:
        assumeTrue(minVersion == null || targetVersion >= GradleVersion.version(minVersion))
        settingsFile << """
            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    ${simulateWork()}
                }
            }

            $callback {
                it.apply(plugin: MyPlugin)
            }
        """

        when:
        runBuild("tasks")

        then:
        def expectedApplications = targetVersion >= GradleVersion.version("9.9") ? currentApplications : legacyApplications
        assertReportedApplications(expectedApplications)
        assertReportedDurationCoversWork(expectedApplications, "MyPlugin", WORK_MILLIS)

        where:
        // rootProject and allprojects callbacks run before the project they configure starts being
        // configured, so before 9.9 the plugins they applied were not reported at all. beforeProject
        // callbacks run during project configuration, so they were always reported.
        callback                         | minVersion | currentApplications                     | legacyApplications
        "gradle.rootProject"             | null       | [":": ["MyPlugin"], ":b": []]           | [":": [], ":b": []]
        "gradle.allprojects"             | null       | [":": ["MyPlugin"], ":b": ["MyPlugin"]] | [":": [], ":b": []]
        "gradle.lifecycle.beforeProject" | "8.8"      | [":": ["MyPlugin"], ":b": ["MyPlugin"]] | [":": ["MyPlugin"], ":b": ["MyPlugin"]]
    }

    def "does not attribute code run directly in a settings #callback callback to any project"() {
        given:
        assumeTrue(minVersion == null || targetVersion >= GradleVersion.version(minVersion))
        settingsFile << """
            $callback {
                ${simulateWork()}
            }
        """

        when:
        runBuild("tasks")

        then:
        // The callback belongs to the settings script, which is not applied to a project, so no
        // application from the build's own code is reported for any project.
        assertReportedApplications([":": [], ":b": []])

        where:
        callback                         | minVersion
        "gradle.rootProject"             | null
        "gradle.allprojects"             | null
        "gradle.lifecycle.beforeProject" | "8.8"
    }

    def "attributes plugins applied from a root build script #callback block to the project they are applied to"() {
        given:
        buildFile << """
            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    ${simulateWork()}
                }
            }

            $callback {
                apply plugin: MyPlugin
            }
        """

        when:
        runBuild("tasks")

        then:
        def expectedApplications = targetVersion >= GradleVersion.version("9.9") ? currentApplications : legacyApplications
        assertReportedApplications(expectedApplications)
        assertReportedDurationCoversWork(expectedApplications, "MyPlugin", WORK_MILLIS)

        where:
        // These blocks all run while the root project is being configured, so before 9.9 the plugin
        // was reported under the root project even when it was applied to :b.
        callback        | currentApplications                                     | legacyApplications
        "allprojects"   | [":": ["build.gradle", "MyPlugin"], ":b": ["MyPlugin"]] | [":": ["build.gradle", "MyPlugin"], ":b": []]
        "subprojects"   | [":": ["build.gradle"], ":b": ["MyPlugin"]]             | [":": ["build.gradle", "MyPlugin"], ":b": []]
        'project(":b")' | [":": ["build.gradle"], ":b": ["MyPlugin"]]             | [":": ["build.gradle", "MyPlugin"], ":b": []]
    }

    def "attributes code run directly in a root build script #callback block to the root build script"() {
        given:
        buildFile << """
            $callback {
                ${simulateWork()}
            }
        """

        when:
        runBuild("tasks")

        then:
        // The code belongs to the root build script, which is applied to the root project even
        // while it is configuring another project, so this is the same in every version.
        def expectedApplications = [":": ["build.gradle"], ":b": []]
        assertReportedApplications(expectedApplications)
        assertReportedDurationCoversWork(expectedApplications, "build.gradle", WORK_MILLIS)

        where:
        callback << ["allprojects", "subprojects", 'project(":b")']
    }

    /**
     * Assert each project reports exactly the given applications from the build's own code.
     */
    private void assertReportedApplications(Map<String, List<String>> expectedApplications) {
        expectedApplications.each { projectPath, applications ->
            assert userCodeApplicationsFor(projectPath) == applications
        }
    }

    /**
     * Assert the given application's reported duration covers the work it did, in every project
     * that is expected to report it. Only this application is checked, since the others reported
     * for a project do not necessarily do any work of their own.
     */
    private void assertReportedDurationCoversWork(Map<String, List<String>> expectedApplications, String pluginDisplayName, long workMillis) {
        expectedApplications.each { projectPath, applications ->
            if (pluginDisplayName in applications) {
                assertPluginDurationAtLeast(pluginDisplayName, projectPath, workMillis)
            }
        }
    }

    /**
     * Assert the plugin was reported as applied to the given project,
     * and that its reported duration is at least the given amount of time.
     */
    private void assertPluginDurationAtLeast(String pluginDisplayName, String projectPath, long workMillis) {
        def result = pluginApplicationResult(projectPath, pluginDisplayName)
        assert result != null, "No plugin application result for $pluginDisplayName in project $projectPath"
        assert result.totalConfigurationTime >= Duration.ofMillis(workMillis)
    }

    /**
     * The applications reported for the given project that come from the build's own code, in the
     * order they were applied. The plugins that Gradle applies to every project are left out, since
     * which of those exist varies by version.
     */
    List<String> userCodeApplicationsFor(String projectPath) {
        getPluginConfigurationOperationResult(projectPath).pluginApplicationResults
            .collect { it.plugin.displayName }
            .findAll { !it.startsWith("org.gradle.") }
    }

    def pluginApplicationResult(String projectPath, String pluginDisplayName) {
        getPluginConfigurationOperationResult(projectPath).pluginApplicationResults.find {
            it.plugin.displayName == pluginDisplayName
        }
    }

    /**
     * A snippet that occupies the executing thread long enough for the durations reported for it
     * to cover {@link #WORK_MILLIS}.
     * <p>
     * Which clock to wait on depends on the target version. Starting in 9.9, the durations come
     * from the user code application timings, measured with {@code Time.nanoTime()}.
     * Earlier versions measure with the  {@code Time.currentTimeMillis()}.
     */
    def simulateWork() {
        if (targetVersion >= GradleVersion.version("9.9")) {
            return """
                def deadline = org.gradle.internal.time.Time.nanoTime() + ${TimeUnit.MILLISECONDS.toNanos(WORK_MILLIS)}L
                def remaining
                while ((remaining = deadline - org.gradle.internal.time.Time.nanoTime()) > 0) {
                    Thread.sleep(Math.max(1L, (long) (remaining / 1_000_000L)))
                }
            """
        }
        """
            def deadline = org.gradle.internal.time.Time.currentTimeMillis() + $WORK_MILLIS
            def remaining
            while ((remaining = deadline - org.gradle.internal.time.Time.currentTimeMillis()) > 0) {
                Thread.sleep(remaining)
            }
        """
    }

    void containsPluginApplicationResultsForJavaPluginAndScriptPlugins(String displayName, File buildscriptDir, String expectedJavaPluginId = "org.gradle.java") {
        with(containsPluginApplicationResultsForJavaPlugin(displayName, expectedJavaPluginId)) {
            def buildScript = pluginApplicationResults.find { it.plugin instanceof ScriptPluginIdentifier && it.plugin.uri == new File(buildscriptDir, "build.gradle").toURI() }
            assert buildScript.totalConfigurationTime >= Duration.ZERO
            assert buildScript.plugin.displayName == "build.gradle"
            def scriptPlugin = pluginApplicationResults.find { it.plugin instanceof ScriptPluginIdentifier && it.plugin.uri == new File(projectDir, "script.gradle").toURI() }
            assert scriptPlugin.plugin.displayName == "script.gradle"
            assert scriptPlugin.totalConfigurationTime >= Duration.ZERO
        }
    }

    void containsScriptPluginApplicationResults(String displayName) {
        with(containsPluginApplicationResultsForJavaPlugin(displayName)) {
            def scriptPlugin = pluginApplicationResults.find { it.plugin instanceof ScriptPluginIdentifier && it.plugin.uri == new File(projectDir, "script.gradle").toURI() }
            assert scriptPlugin.plugin.displayName == "script.gradle"
            assert scriptPlugin.totalConfigurationTime >= Duration.ZERO
        }
    }

    ProjectConfigurationOperationResult containsPluginApplicationResultsForJavaPlugin(String displayName, String expectedJavaPluginId = "org.gradle.java") {
        def result = getPluginConfigurationOperationResult(displayName)
        with(result) {
            def javaPluginResult = pluginApplicationResults.find { it.plugin instanceof BinaryPluginIdentifier && it.plugin.className == "org.gradle.api.plugins.JavaPlugin" }
            assert javaPluginResult.plugin.pluginId == expectedJavaPluginId
            assert javaPluginResult.plugin.displayName == (expectedJavaPluginId ?: "org.gradle.api.plugins.JavaPlugin")
            assert javaPluginResult.totalConfigurationTime >= Duration.ZERO
            def basePluginResult = pluginApplicationResults.find { it.plugin instanceof BinaryPluginIdentifier && it.plugin.className == "org.gradle.api.plugins.BasePlugin" }
            assert basePluginResult.plugin.pluginId == null
            assert basePluginResult.plugin.displayName == "org.gradle.api.plugins.BasePlugin"
            assert basePluginResult.totalConfigurationTime >= Duration.ZERO
        }
        return result
    }

    void doesNotContainPluginApplicationResultsForJavaPluginAndScriptPlugins(String displayName) {
        with(doesNotContainPluginApplicationResultsForJavaPlugin(displayName)) {
            assert pluginApplicationResults.findAll { it.plugin instanceof ScriptPluginIdentifier }.empty
        }
    }

    ProjectConfigurationOperationResult doesNotContainPluginApplicationResultsForJavaPlugin(String displayName) {
        def result = getPluginConfigurationOperationResult(displayName)
        with(result) {
            assert pluginApplicationResults.find { it.plugin.className == "org.gradle.api.plugins.JavaPlugin" } == null
        }
        return result
    }

    def getPluginConfigurationOperationResult(String displayName) {
        (ProjectConfigurationOperationResult) events.operation("Configure project $displayName").result
    }

    private void runBuild(String task, Set<OperationType> operationTypes = EnumSet.of(OperationType.PROJECT_CONFIGURATION)) {
        withConnection { connection ->
            connection.newBuild()
                .forTasks(task)
                .addProgressListener(events, operationTypes)
                .run()
        }
    }

}
