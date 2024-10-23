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

package org.gradle.integtests.tooling.fixture

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.integtests.fixtures.build.KotlinDslTestProjectInitiation
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.integtests.fixtures.executer.DocumentationUtils
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionFailureWithThrowable
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.ExpectedDeprecationWarning
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.fixtures.executer.ResultAssertion
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestDistributionDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import org.junit.rules.RuleChain
import spock.lang.Retry
import spock.lang.Specification

import java.util.function.Supplier

import static org.gradle.integtests.fixtures.RetryConditions.onIssueWithReleasedGradleVersion
import static spock.lang.Retry.Mode.SETUP_FEATURE_CLEANUP

/**
 * A spec that executes tests against all compatible versions of tooling API consumer and testDirectoryProvider, including the current Gradle version under test.
 *
 * <p>A test class or test method can be annotated with the following annotations to specify which versions the test is compatible with:
 * </p>
 *
 * <ul>
 *     <li>{@link ToolingApiVersion} - specifies the tooling API consumer versions that the test is compatible with.
 *     <li>{@link TargetGradleVersion} - specifies the tooling API testDirectoryProvider versions that the test is compatible with.
 * </ul>
 *
 * The supported ranges for the tooling API versions and for the target Gradle versions are documented in the Gradle user guide.
 * For up-to-date information, check the 'Compatibility of Java and Gradle versions` section of the 'Third-party Tools' chapter.
 * The parameters of the @ToolingApiVersion and the @TargetGradleVersion annotations on this class should always match with the documentation.
 */
@ToolingApiTest
@CleanupTestDirectory
@ToolingApiVersion('>=7.0')
@TargetGradleVersion('>=3.0')
@Retry(condition = { onIssueWithReleasedGradleVersion(instance, failure) }, mode = SETUP_FEATURE_CLEANUP, count = 2)
abstract class ToolingApiSpecification extends Specification implements KotlinDslTestProjectInitiation {
    /**
     * See https://github.com/gradle/gradle-private/issues/3216
     * To avoid flakiness when reusing daemons between CLI and TAPI
     */
    public static final List NORMALIZED_BUILD_JVM_OPTS = ["-Dfile.encoding=UTF-8", "-Duser.country=US", "-Duser.language=en", "-Duser.variant"]

    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties()

    GradleConnectionException caughtGradleConnectionException
    TestOutputStream stderr = new TestOutputStream()
    TestOutputStream stdout = new TestOutputStream()

    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    final GradleDistribution dist = new UnderDevelopmentGradleDistribution()
    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private GradleDistribution targetGradleDistribution

    TestDistributionDirectoryProvider temporaryDistributionFolder = new TestDistributionDirectoryProvider(getClass())

    @Delegate
    final ToolingApi toolingApi = new ToolingApi(null, temporaryFolder, stdout, stderr)

    // TODO: react to the isolatedProjects prop coming from build settings

    @Rule
    public RuleChain cleanupRule = RuleChain.outerRule(temporaryFolder).around(temporaryDistributionFolder).around(toolingApi)

    private List<String> expectedDeprecations = []
    private boolean stackTraceChecksOn = true

    private ExecutionResult result
    private ExecutionFailure failure

    // used reflectively by retry rule
    String getReleasedGradleVersion() {
        return targetDist.version.baseVersion.version
    }

    // reflectively invoked by ToolingApiExecution
    void setTargetDist(GradleDistribution targetDist) {
        targetGradleDistribution = targetDist
        toolingApi.setDist(targetGradleDistribution)
    }

    GradleDistribution getTargetDist() {
        if (targetGradleDistribution == null) {
            throw new IllegalStateException("targetDist is not yet set by the testing framework")
        }
        return targetGradleDistribution
    }

    def setup() {
        // These properties are set on CI. Reset them to allow tests to configure toolchains explicitly.
        System.setProperty("org.gradle.java.installations.auto-download", "false")
        System.setProperty("org.gradle.java.installations.auto-detect", "false")
        System.clearProperty("org.gradle.java.installations.paths")

        // this is to avoid the working directory to be the Gradle directory itself
        // which causes isolation problems for tests. This one is for _embedded_ mode
        System.setProperty("user.dir", temporaryFolder.testDirectory.absolutePath)

        // Enable deprecation logging for all tests
        System.setProperty("org.gradle.warning.mode", "all")

        settingsFile.touch()
    }

    DaemonsFixture getDaemonsFixture() {
        toolingApi.daemons
    }

    TestFile getProjectDir() {
        temporaryFolder.testDirectory
    }

    @Override
    TestFile getBuildFileKts() {
        validateKotlinCompatibility()
        KotlinDslTestProjectInitiation.super.getBuildFileKts()
    }

    @Override
    TestFile getSettingsFileKts() {
        validateKotlinCompatibility()
        KotlinDslTestProjectInitiation.super.getSettingsFileKts()
    }


    private validateKotlinCompatibility() {
        if (targetGradleDistribution && !targetGradleDistribution.supportsKotlinScript) {
            throw new RuntimeException("The current Gradle target version ($targetGradleDistribution.version) does not support execution of Kotlin build scripts.")
        }
    }

    TestFile file(Object... path) {
        projectDir.file(path)
    }

    BuildTestFile populate(String projectName, @DelegatesTo(BuildTestFile) Closure cl) {
        new BuildTestFixture(projectDir).withBuildInSubDir().populate(projectName, cl)
    }

    TestFile singleProjectBuildInSubfolder(String projectName, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        new BuildTestFixture(projectDir).withBuildInSubDir().singleProjectBuild(projectName, cl)
    }

    TestFile singleProjectBuildInRootFolder(String projectName, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        new BuildTestFixture(projectDir).withBuildInRootDir().singleProjectBuild(projectName, cl)
    }

    TestFile multiProjectBuildInSubFolder(String projectName, List<String> subprojects, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        new BuildTestFixture(projectDir).withBuildInSubDir().multiProjectBuild(projectName, subprojects, cl)
    }

    void multiProjectBuildInRootFolder(String projectName, List<String> subprojects, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        new BuildTestFixture(projectDir).withBuildInRootDir().multiProjectBuild(projectName, subprojects, cl)
    }

    void withConnector(@DelegatesTo(GradleConnector) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.GradleConnector"]) Closure cl) {
        toolingApi.withConnector(cl)
    }

    def <T> T withConnection(ToolingApiConnector connector, @DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        return toolingApi.withConnection(connector, cl)
    }

    ToolingApiConnector connector() {
        toolingApi.connector()
    }

    ToolingApiConnector connectorWithoutOutputRedirection() {
        toolingApi.connectorWithoutOutputRedirection()
    }

    /**
     * Prefer {@link #succeeds(Closure)} and {@link #fails(Closure)} over this method, as they automatically verify build output.
     */
    <T> T withConnection(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        return toolingApi.withConnection(cl)
    }

    /**
     * Open a new project connection and execute the given closure against it, closing the connection afterwards.
     * Then, verify that the build succeeded and verify emitted deprecation warnings.
     */
    <T> T succeeds(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        runSuccessfully {
            withConnection(cl)
        }
    }

    /**
     * Open a new project connection and execute the given closure against it, closing the connection afterwards.
     * Then, verify that the build failed and verify emitted deprecation warnings.
     */
    void fails(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure cl) {
        runUnsuccessfully {
            withConnection(cl)
        }
    }

    def <T> T loadToolingModel(Class<T> modelClass, @DelegatesTo(ModelBuilder<T>) Closure cl = {}) {
        runSuccessfully {
            withConnection {
                def builder = it.model(modelClass)
                builder.tap(cl)
                builder.get()
            }
        }
    }

    ConfigurableOperation withModel(Class modelType, Closure cl = {}) {
        runSuccessfully {
            withConnection {
                def model = it.model(modelType)
                cl(model)
                new ConfigurableOperation(model).buildModel()
            }
        }
    }

    ConfigurableOperation withBuild(Closure cl = {}) {
        runSuccessfully {
            withConnection {
                def build = it.newBuild()
                cl(build)
                def out = new ConfigurableOperation(build)
                build.run()
                out
            }
        }
    }

    /**
     * Runs some action that presumably executes a tooling API request. Afterwards,
     * verify the request was successful by scanning the output streams. Finally,
     * reset this integration spec to prepare to run another action.
     *
     * TODO: We should migrate almost all of the methods in this class to use this method
     *       and runUnsuccessfully() instead of the raw withConnection methods
     */
    private <T> T runSuccessfully(Supplier<T> action) {
        // While there are still other tests that do not reset the streams after execution
        // we will need to do this ourselves here.
        stdout.reset()
        stderr.reset()

        try {
            T value
            try {
                value = action.get()
            } catch (Exception e) {
                throw new AssertionError("Expected action to not throw an exception", e)
            }
            this.result = assertSuccessful()
            return value
        } finally {
            reset()
        }
    }

    // Same as above but for the failure case
    private void runUnsuccessfully(Runnable action) {
        // While there are still other tests that do not reset the streams after execution
        // we will need to do this ourselves here.
        stdout.reset()
        stderr.reset()

        try {
            try {
                action.run()
                throw new AssertionError("Expected action to throw an exception" as Object)
            } catch (Exception e) {
                this.failure = assertFailure(e)
                throw e
            }
        } finally {
            reset()
        }
    }

    /**
     * Returns the set of implicit task names expected for any project for the target Gradle version.
     */
    Set<String> getImplicitTasks() {
        if (targetVersion >= GradleVersion.version("7.5")) {
            return ['buildEnvironment', 'components', 'dependencies', 'dependencyInsight', 'dependentComponents', 'help', 'javaToolchains', 'projects', 'properties', 'tasks', 'model', 'outgoingVariants', 'resolvableConfigurations']
        } else if (targetVersion >= GradleVersion.version("6.8")) {
            return ['buildEnvironment', 'components', 'dependencies', 'dependencyInsight', 'dependentComponents', 'help', 'javaToolchains', 'projects', 'properties', 'tasks', 'model', 'outgoingVariants']
        } else if (targetVersion >= GradleVersion.version("6.5")) {
            return ['buildEnvironment', 'components', 'dependencies', 'dependencyInsight', 'dependentComponents', 'help', 'projects', 'properties', 'tasks', 'model', 'outgoingVariants']
        } else if (targetVersion >= GradleVersion.version("6.0")) {
            return ['buildEnvironment', 'components', 'dependencies', 'dependencyInsight', 'dependentComponents', 'help', 'projects', 'properties', 'tasks', 'model', 'outgoingVariants', 'prepareKotlinBuildScriptModel']
        } else if (targetVersion >= GradleVersion.version("5.3")) {
            return ['buildEnvironment', 'components', 'dependencies', 'dependencyInsight', 'dependentComponents', 'help', 'projects', 'properties', 'tasks', 'model', 'prepareKotlinBuildScriptModel']
        } else if (targetVersion > GradleVersion.version("3.1")) {
            return ['buildEnvironment', 'components', 'dependencies', 'dependencyInsight', 'dependentComponents', 'help', 'projects', 'properties', 'tasks', 'model']
        } else if (targetVersion >= GradleVersion.version("2.10")) {
            return ['buildEnvironment', 'components', 'dependencies', 'dependencyInsight', 'help', 'projects', 'properties', 'tasks', 'model']
        } else {
            return ['components', 'dependencies', 'dependencyInsight', 'help', 'projects', 'properties', 'tasks', 'model']
        }
    }

    /**
     * Returns the set of implicit selector names expected for any project for the target Gradle version.
     *
     * <p>Note that in some versions the handling of implicit selectors was broken, so this method may return a different value
     * to {@link #getImplicitTasks()}.
     */
    Set<String> getImplicitSelectors() {
        return getImplicitTasks()
    }

    /**
     * Returns the set of invisible implicit task names expected for a root project for the target Gradle version.
     */
    Set<String> getRootProjectImplicitInvisibleTasks() {
        if (targetVersion >= GradleVersion.version("6.8")) {
            return ['prepareKotlinBuildScriptModel', 'components', 'dependentComponents', 'model']
        } else if (targetVersion >= GradleVersion.version("5.3")) {
            return ['prepareKotlinBuildScriptModel']
        } else {
            return []
        }
    }

    /**
     * Returns the set of invisible implicit selector names expected for a root project for the target Gradle version.
     *
     * See {@link #getRootProjectImplicitInvisibleTasks}.
     */
    Set<String> getRootProjectImplicitInvisibleSelectors() {
        return rootProjectImplicitInvisibleTasks
    }

    /**
     * Returns the set of implicit task names expected for a root project for the target Gradle version.
     */
    Set<String> getRootProjectImplicitTasks() {
        final rootOnlyTasks
        if (targetVersion >= GradleVersion.version("8.8")) {
            rootOnlyTasks = ['init', 'wrapper', 'updateDaemonJvm']
        } else {
            rootOnlyTasks = ['init', 'wrapper']
        }
        return implicitTasks + rootOnlyTasks + rootProjectImplicitInvisibleTasks
    }

    /**
     * Returns the set of implicit selector names expected for a root project for the target Gradle version.
     */
    Set<String> getRootProjectImplicitSelectors() {
        return rootProjectImplicitTasks
    }

    /**
     * Returns the set of implicit tasks returned by GradleProject.getTasks()
     */
    Set<String> getRootProjectImplicitTasksForGradleProjectModel() {
        rootProjectImplicitTasks
    }

    ExecutionResult assertSuccessful() {
        def result = OutputScrapingExecutionResult.from(stdout.toString(), stderr.toString())

        // We get BUILD SUCCESSFUL when we run tasks, and CONFIGURE SUCCESSFUL when we fetch models without requesting tasks
        assert result.output.contains("BUILD SUCCESSFUL") || result.output.contains("CONFIGURE SUCCESSFUL")

        validateOutput(result)
        return result
    }

    ExecutionFailure assertFailure(Exception exception) {
        def failure = new ExecutionFailureWithThrowable(
            OutputScrapingExecutionFailure.from(stdout.toString(), stderr.toString()),
            exception
        )

        // We get BUILD FAILED when we run tasks, and CONFIGURE FAILED when we fetch models without requesting tasks
        String failureOutput = targetDist.selectOutputWithFailureLogging(failure.output, failure.error)
        boolean hasFailureLog = failureOutput.contains("BUILD FAILED") || failureOutput.contains("CONFIGURE FAILED")
        if (!hasFailureLog) {
            // The build failed, but the failure is not in the output. We must have failed before the build could
            // even start. Make sure we at least do not emit a BUILD SUCCESSFUL message.
            assert !failure.output.contains("BUILD SUCCESSFUL") && !failure.output.contains("CONFIGURE SUCCESSFUL")
        }

        validateOutput(failure)
        return failure
    }

    void assertHasBuildSuccessfulLogging() {
        assert stdout.toString().contains("BUILD SUCCESSFUL")
        validateOutput(getResult())
    }

    void assertHasBuildFailedLogging() {
        def failureOutput = targetDist.selectOutputWithFailureLogging(stdout, stderr).toString()
        assert failureOutput.contains("BUILD FAILED")
        validateOutput(getFailure())
    }

    void assertHasConfigureSuccessfulLogging() {
        if (targetDist.isToolingApiLogsConfigureSummary()) {
            assert stdout.toString().contains("CONFIGURE SUCCESSFUL")
        } else {
            assert stdout.toString().contains("BUILD SUCCESSFUL")
        }
        validateOutput(getResult())
    }

    void assertHasConfigureFailedLogging() {
        def failureOutput = targetDist.selectOutputWithFailureLogging(stdout, stderr).toString()
        if (targetDist.isToolingApiLogsConfigureSummary()) {
            assert failureOutput.contains("CONFIGURE FAILED")
        } else {
            assert failureOutput.contains("BUILD FAILED")
        }
        validateOutput(getFailure())
    }

    private void reset() {
        stdout.reset()
        stderr.reset()
        expectedDeprecations.clear()
        stackTraceChecksOn = true
    }

    def shouldCheckForDeprecationWarnings() {
        // Older versions have deprecations
        GradleVersion.version("6.9") < targetVersion
    }

    private boolean filterJavaVersionDeprecation = true
    boolean disableDaemonJavaVersionDeprecationFiltering() {
        filterJavaVersionDeprecation = false
    }

    ExecutionResult getResult() {
        if (result != null) {
            return result
        }

        // Legacy path. Tests should instead use methods that call runSuccessfully
        return OutputScrapingExecutionResult.from(stdout.toString(), stderr.toString())
    }

    ExecutionFailure getFailure() {
        if (failure != null) {
            return failure
        }

        // Legacy path. Tests should instead use methods that call runUnsuccessfully
        return OutputScrapingExecutionFailure.from(stdout.toString(), stderr.toString())
    }

    void validateOutput(ExecutionResult result) {
        List<String> maybeExpectedDeprecations = []
        if (filterJavaVersionDeprecation) {
            maybeExpectedDeprecations.add(normalizeDeprecationWarning(
                "Executing Gradle on JVM versions 16 and lower has been deprecated. " +
                    "This will fail with an error in Gradle 9.0. " +
                    "Use JVM 17 or greater to execute Gradle. " +
                    "Projects can continue to use older JVM versions via toolchains. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/${targetDist.version.version}/userguide/upgrading_version_8.html#minimum_daemon_jvm_version"
            ))
        }

        // Check for deprecation warnings.
        new ResultAssertion(
            0,
            expectedDeprecations.collect { ExpectedDeprecationWarning.withMessage(it) },
            maybeExpectedDeprecations.collect { ExpectedDeprecationWarning.withMessage(it) },
            !stackTraceChecksOn,
            shouldCheckForDeprecationWarnings(),
            true
        ).execute(result)
    }

    protected GradleVersion getTargetVersion() {
        GradleVersion.version(targetDist.version.baseVersion.version)
    }

    protected static String mavenCentralRepository() {
        RepoScriptBlockUtil.mavenCentralRepository()
    }

    boolean withStackTraceChecksDisabled() {
        stackTraceChecksOn = false
    }

    void expectDocumentedDeprecationWarning(String message) {
        expectedDeprecations << normalizeDeprecationWarning(message)
    }

    private String normalizeDeprecationWarning(String message) {
        def normalizedLink = DocumentationUtils.normalizeDocumentationLink(message, targetDist.version)

        return normalizedLink
    }
}
