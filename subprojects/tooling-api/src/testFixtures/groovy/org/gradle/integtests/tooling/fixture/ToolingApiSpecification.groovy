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
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
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
import org.junit.runner.RunWith
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
@CleanupTestDirectory
@ToolingApiVersion('>=3.0')
@TargetGradleVersion('>=2.6')
@RunWith(ToolingApiCompatibilitySuiteRunner)
@Retry(condition = { onIssueWithReleasedGradleVersion(instance, failure) }, mode = SETUP_FEATURE_CLEANUP, count = 2)
abstract class ToolingApiSpecification extends Specification {

    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties()

    GradleConnectionException caughtGradleConnectionException
    TestOutputStream stderr = new TestOutputStream()
    TestOutputStream stdout = new TestOutputStream()

    String getReleasedGradleVersion() {
        return targetDist.version.baseVersion.version
    }

    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    final GradleDistribution dist = new UnderDevelopmentGradleDistribution()
    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private static final ThreadLocal<GradleDistribution> VERSION = new ThreadLocal<GradleDistribution>()

    TestDistributionDirectoryProvider temporaryDistributionFolder = new TestDistributionDirectoryProvider();
    final ToolingApi toolingApi = new ToolingApi(targetDist, temporaryFolder)

    @Rule
    public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(temporaryDistributionFolder).around(toolingApi)

    static void selectTargetDist(GradleDistribution version) {
        VERSION.set(version)
    }

    static GradleDistribution getTargetDist() {
        VERSION.get()
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

    TestFile getBuildFileKts() {
        file("build.gradle.kts")
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

    public void withConnector(@DelegatesTo(GradleConnector) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.GradleConnector"]) Closure cl) {
        try {
            toolingApi.withConnector(cl)
        } catch (GradleConnectionException e) {
            caughtGradleConnectionException = e
            throw e
        }
    }

    public <T> T withConnection(GradleConnector connector, @DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        try {
            return toolingApi.withConnection(connector, cl)
        } catch (GradleConnectionException e) {
            caughtGradleConnectionException = e
            throw e
        }
    }

    def connector() {
        toolingApi.connector()
    }

    public <T> T withConnection(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        toolingApi.withConnection(cl)
    }

    public ConfigurableOperation withModel(Class modelType, Closure cl = {}) {
        withConnection {
            def model = it.model(modelType)
            cl(model)
            new ConfigurableOperation(model).buildModel()
        }
    }

    public ConfigurableOperation withBuild(Closure cl = {}) {
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
        if (targetVersion >= GradleVersion.version("5.3")) {
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
     * Returns the set of invisible implicit task names expected for any project for the target Gradle version.
     */
    Set<String> getImplicitInvisibleTasks() {
        return targetVersion >= GradleVersion.version("5.3") ? ['prepareKotlinBuildScriptModel'] : []
    }

    /**
     * Returns the set of invisible implicit selector names expected for any project for the target Gradle version.
     *
     * See {@link #getImplicitInvisibleTasks}.
     */
    Set<String> getImplicitInvisibleSelectors() {
        return implicitInvisibleTasks
    }

    /**
     * Returns the set of implicit task names expected for a root project for the target Gradle version.
     */
    Set<String> getRootProjectImplicitTasks() {
        return implicitTasks + ['init', 'wrapper']
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
        assert stdout.toString().contains("BUILD SUCCESSFUL")
    }

    void assertHasBuildFailedLogging() {
        def failureOutput = targetDist.selectOutputWithFailureLogging(stdout, stderr).toString()
        assert failureOutput.contains("BUILD FAILED")
    }

    void assertHasConfigureSuccessfulLogging() {
        if (targetDist.isToolingApiLogsConfigureSummary()) {
            assert stdout.toString().contains("CONFIGURE SUCCESSFUL")
        } else {
            assert stdout.toString().contains("BUILD SUCCESSFUL")
        }
    }

    void assertHasConfigureFailedLogging() {
        def failureOutput = targetDist.selectOutputWithFailureLogging(stdout, stderr).toString()
        if (targetDist.isToolingApiLogsConfigureSummary()) {
            assert failureOutput.contains("CONFIGURE FAILED")
        } else {
            assert failureOutput.contains("BUILD FAILED")
        }
    }

    public <T> T loadToolingModel(Class<T> modelClass) {
        withConnection { connection -> connection.getModel(modelClass) }
    }

    protected static GradleVersion getTargetVersion() {
        GradleVersion.version(targetDist.version.baseVersion.version)
    }

    protected static String jcenterRepository() {
        RepoScriptBlockUtil.jcenterRepository()
    }

    protected static String mavenCentralRepository() {
        RepoScriptBlockUtil.mavenCentralRepository()
    }
}
