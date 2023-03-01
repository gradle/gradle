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
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestDistributionDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import org.junit.rules.RuleChain
import spock.lang.Retry
import spock.lang.Specification

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
 */
@ToolingApiTest
@CleanupTestDirectory
@ToolingApiVersion('>=7.0') // The lowest tested version should be the first release of the previous major.
@TargetGradleVersion('>=3.0')
@Retry(condition = { onIssueWithReleasedGradleVersion(instance, failure) }, mode = SETUP_FEATURE_CLEANUP, count = 2)
abstract class ToolingApiSpecification extends Specification {
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
    final ToolingApi toolingApi = new ToolingApi(null, temporaryFolder)

    @Rule
    public RuleChain cleanupRule = RuleChain.outerRule(temporaryFolder).around(temporaryDistributionFolder).around(toolingApi)

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
        if (targetGradleDistribution == null)  {
            throw new IllegalStateException("targetDist is not yet set by the testing framework")
        }
        return targetGradleDistribution
    }

    def setup() {
        // this is to avoid the working directory to be the Gradle directory itself
        // which causes isolation problems for tests. This one is for _embedded_ mode
        System.setProperty("user.dir", temporaryFolder.testDirectory.absolutePath)
        settingsFile.touch()
    }

    DaemonsFixture getDaemonsFixture() {
        toolingApi.daemons
    }

    TestFile getProjectDir() {
        temporaryFolder.testDirectory
    }

    TestFile getBuildFile() {
        file("build.gradle")
    }

    TestFile getPropertiesFile() {
        file("gradle.properties")
    }

    TestFile getBuildFileKts() {
        file("build.gradle.kts")
    }

    TestFile getBuildKotlinFile() {
        getBuildFileKts()
    }

    TestFile getSettingsFile() {
        file("settings.gradle")
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
        try {
            toolingApi.withConnector(cl)
        } catch (GradleConnectionException e) {
            caughtGradleConnectionException = e
            throw e
        }
    }

    def <T> T withConnection(GradleConnector connector, @DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        try {
            return toolingApi.withConnection(connector, cl)
        } catch (GradleConnectionException e) {
            caughtGradleConnectionException = e
            throw e
        }
    }

    GradleConnector connector() {
        toolingApi.connector()
    }

    def <T> T withConnection(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        try {
            toolingApi.withConnection(cl)
        } catch (GradleConnectionException e) {
            caughtGradleConnectionException = e
            throw e
        }
    }

    ConfigurableOperation withModel(Class modelType, Closure cl = {}) {
        withConnection {
            def model = it.model(modelType)
            cl(model)
            new ConfigurableOperation(model).buildModel()
        }
    }

    ConfigurableOperation withBuild(Closure cl = {}) {
        withConnection {
            def build = it.newBuild()
            cl(build)
            def out = new ConfigurableOperation(build)
            build.run()
            out
        }
    }

    void collectOutputs(LongRunningOperation op) {
        op.setStandardOutput(new TeeOutputStream(stdout, System.out))
        op.setStandardError(new TeeOutputStream(stderr, System.err))
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
        return implicitTasks + ['init', 'wrapper'] + rootProjectImplicitInvisibleTasks
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

    void assertHasBuildSuccessfulLogging() {
        assertHasNoDeprecationWarnings()
        assert stdout.toString().contains("BUILD SUCCESSFUL")
    }

    void assertHasBuildFailedLogging() {
        assertHasNoDeprecationWarnings()
        def failureOutput = targetDist.selectOutputWithFailureLogging(stdout, stderr).toString()
        assert failureOutput.contains("BUILD FAILED")
    }

    void assertHasConfigureSuccessfulLogging() {
        assertHasNoDeprecationWarnings()
        if (targetDist.isToolingApiLogsConfigureSummary()) {
            assert stdout.toString().contains("CONFIGURE SUCCESSFUL")
        } else {
            assert stdout.toString().contains("BUILD SUCCESSFUL")
        }
    }

    void assertHasConfigureFailedLogging() {
        assertHasNoDeprecationWarnings()
        def failureOutput = targetDist.selectOutputWithFailureLogging(stdout, stderr).toString()
        if (targetDist.isToolingApiLogsConfigureSummary()) {
            assert failureOutput.contains("CONFIGURE FAILED")
        } else {
            assert failureOutput.contains("BUILD FAILED")
        }
    }

    private void assertHasNoDeprecationWarnings() {
        if (targetVersion < GradleVersion.version("6.9")) {
            // Older versions have deprecations
            return
        }
        assert !stdout.toString()
            .replace("[deprecated]", "IGNORE") // deprecated command-line argument
            .containsIgnoreCase("deprecated")
    }

    ExecutionResult getResult() {
        return OutputScrapingExecutionResult.from(stdout.toString(), stderr.toString())
    }

    ExecutionFailure getFailure() {
        return OutputScrapingExecutionFailure.from(stdout.toString(), stderr.toString())
    }

    def <T> T loadToolingModel(Class<T> modelClass) {
        def result = withConnection { connection ->
            def builder = connection.model(modelClass)
            collectOutputs(builder)
            builder.get()
        }
        assertHasConfigureSuccessfulLogging()
        return result
    }

    protected GradleVersion getTargetVersion() {
        GradleVersion.version(targetDist.version.baseVersion.version)
    }

    protected static String mavenCentralRepository() {
        RepoScriptBlockUtil.mavenCentralRepository()
    }
}
