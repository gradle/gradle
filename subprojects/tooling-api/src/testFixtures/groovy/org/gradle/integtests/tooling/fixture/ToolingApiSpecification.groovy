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
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestDistributionDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.RetryRule
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestPrecondition
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import spock.lang.Specification

import static org.gradle.testing.internal.util.RetryRule.retryIf
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
@ToolingApiVersion('>=2.0')
@TargetGradleVersion('>=1.2')
@RunWith(ToolingApiCompatibilitySuiteRunner)
abstract class ToolingApiSpecification extends Specification {

    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties()

    GradleConnectionException caughtGradleConnectionException

    @Rule
    RetryRule retryRule = retryIf(
        { t ->
            Throwable failure = t

            def targetDistVersion = GradleVersion.version(targetDist.version.baseVersion.version)
            println "ToolingAPI test failure with target version " + targetDistVersion
            println "Failure: " + failure
            println "Cause  : " + failure?.cause

            if (caughtGradleConnectionException != null) {
                failure = caughtGradleConnectionException
                println "Failure (caught during test): " + failure
                println "Cause   (caught during test): " + failure?.cause
            }

            // known issue with pre 1.3 daemon versions: https://github.com/gradle/gradle/commit/29d895bc086bc2bfcf1c96a6efad22c602441e26
            if (targetDistVersion < GradleVersion.version("1.3") &&
                (failure.cause?.message ==~ /(?s)Timeout waiting to connect to (the )?Gradle daemon.*/
                    || failure.cause?.message == "Gradle build daemon disappeared unexpectedly (it may have been stopped, killed or may have crashed)"
                    || failure.message == "Gradle build daemon disappeared unexpectedly (it may have been stopped, killed or may have crashed)")) {
                println "Retrying ToolingAPI test because of <1.3 daemon connection issue"
                return retryWithCleanProjectDir()
            }

            // this is cause by a bug in Gradle <1.8, where a NPE is thrown when DaemonInfo is removed from the daemon registry by another process
            if (targetDistVersion < GradleVersion.version("1.8") &&
                failure instanceof GradleConnectionException && failure.cause instanceof NullPointerException) {
                return retryWithCleanProjectDir()
            }

            if (targetDistVersion < GradleVersion.version('2.10')) {
                if (getRootCauseMessage(failure) ==~ /Unable to calculate percentage: .* of .*\. All inputs must be >= 0/) {
                    println "Retrying ToolingAPI test because of timing issue in Gradle versions <2.10"
                    return retryWithCleanProjectDir()
                }
            }

            // daemon connection issue that does not appear anymore with 3.x versions of Gradle
            if (targetDistVersion < GradleVersion.version("3.0") &&
                failure.cause?.message ==~ /(?s)Timeout waiting to connect to (the )?Gradle daemon\..*/) {

                println "Retrying ToolingAPI test because daemon connection is broken."
                return retryWithCleanProjectDir()
            }

            // sometime sockets are unexpectedly disappearing on daemon side (running on windows): https://github.com/gradle/gradle/issues/1111
            if (runsOnWindowsAndJava7or8()) {
                if (getRootCauseMessage(failure) == "An existing connection was forcibly closed by the remote host" ||
                    getRootCauseMessage(failure) == "An established connection was aborted by the software in your host machine") {

                    for (def daemon : toolingApi.daemons.daemons) {
                        if (daemon.log.contains("java.net.SocketException: Socket operation on nonsocket: no further information")
                            || daemon.log.contains("java.io.IOException: An operation was attempted on something that is not a socket")) {

                            println "Retrying ToolingAPI test because socket disappeared. Check log of daemon with PID " + daemon.context.pid
                            return retryWithCleanProjectDir()
                        }
                        println "Analyzed daemon log (socket issue)"
                        println "  Daemon Context:  ${daemon.context}"
                        println "  Daemon Log Size: ${daemon.log.size()}"
                    }
                }
            }
            false
        }
    )

    boolean retryWithCleanProjectDir() {
        temporaryFolder.testDirectory.listFiles().each {
            if (it.name != "user-home-dir") { //preserve logs in user home, if it exists
                it.deleteDir()
            }
        }
        caughtGradleConnectionException = null
        true
    }

    static String getRootCauseMessage(Throwable throwable) {
        final List<Throwable> list = getThrowableList(throwable)
        return list.size() < 2 ? "" : list.get(list.size() - 1).message
    }

    static String getDirectlyCausedByRootMessage(Throwable throwable) {
        final List<Throwable> list = getThrowableList(throwable)
        return list.size() < 3 ? "" : list.get(list.size() - 2).message
    }

    static List<Throwable> getThrowableList(Throwable throwable) {
        final List<Throwable> list = new ArrayList<Throwable>()
        while (throwable != null && !list.contains(throwable)) {
            list.add(throwable)
            throwable = throwable.cause
        }
        list
    }

    static boolean runsOnWindowsAndJava7or8() {
        return TestPrecondition.WINDOWS.fulfilled && [JavaVersion.VERSION_1_7, JavaVersion.VERSION_1_8].contains(JavaVersion.current())
    }

    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    final GradleDistribution dist = new UnderDevelopmentGradleDistribution()
    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private static final ThreadLocal<GradleDistribution> VERSION = new ThreadLocal<GradleDistribution>()

    TestDistributionDirectoryProvider temporaryDistributionFolder = new TestDistributionDirectoryProvider();
    final ToolingApi toolingApi = new ToolingApi(targetDist, temporaryFolder)

    @Rule
    public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(temporaryDistributionFolder).around(toolingApi);

    static void selectTargetDist(GradleDistribution version) {
        VERSION.set(version)
    }

    static GradleDistribution getTargetDist() {
        VERSION.get()
    }

    TestFile getProjectDir() {
        temporaryFolder.testDirectory
    }

    TestFile getBuildFile() {
        file("build.gradle")
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
        toolingApi.withConnector(cl)
    }

    public <T> T withConnection(GradleConnector connector, @DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        toolingApi.withConnection(connector, cl)
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

    /**
     * Returns the set of implicit task names expected for a non-root project for the target Gradle version.
     */
    Set<String> getImplicitTasks() {
        if (targetVersion > GradleVersion.version("3.1")) {
            return ['buildEnvironment', 'components', 'dependencies', 'dependencyInsight', 'dependentComponents', 'help', 'projects', 'properties', 'tasks', 'model']
        } else if (GradleVersion.version(targetDist.version.baseVersion.version) >= GradleVersion.version("2.10")) {
            return ['buildEnvironment', 'components', 'dependencies', 'dependencyInsight', 'help', 'projects', 'properties', 'tasks', 'model']
        } else if (GradleVersion.version(targetDist.version.baseVersion.version) >= GradleVersion.version("2.4")) {
            return ['components', 'dependencies', 'dependencyInsight', 'help', 'projects', 'properties', 'tasks', 'model']
        } else if (GradleVersion.version(targetDist.version.baseVersion.version) >= GradleVersion.version("2.1")) {
            return ['components', 'dependencies', 'dependencyInsight', 'help', 'projects', 'properties', 'tasks']
        } else {
            return ['dependencies', 'dependencyInsight', 'help', 'projects', 'properties', 'tasks']
        }
    }

    /**
     * Returns the set of implicit selector names expected for a non-root project for the target Gradle version.
     *
     * <p>Note that in some versions the handling of implicit selectors was broken, so this method may return a different value
     * to {@link #getImplicitTasks()}.
     */
    Set<String> getImplicitSelectors() {
        if (targetVersion <= GradleVersion.version("2.0")) {
            // Implicit tasks were ignored
            return []
        }
        return getImplicitTasks()
    }

    /**
     * Returns the set of implicit task names expected for a root project for the target Gradle version.
     */
    Set<String> getRootProjectImplicitTasks() {
        if (targetVersion == GradleVersion.version("1.6")) {
            return implicitTasks + ['setupBuild']
        }
        return implicitTasks + ['init', 'wrapper']
    }

    /**
     * Returns the set of implicit selector names expected for a root project for the target Gradle version.
     *
     * <p>Note that in some versions the handling of implicit selectors was broken, so this method may return a different value
     * to {@link #getRootProjectImplicitTasks()}.
     */
    Set<String> getRootProjectImplicitSelectors() {
        if (targetVersion == GradleVersion.version("1.6")) {
            // Implicit tasks were ignored, and setupBuild was added as a regular task
            return ['setupBuild']
        }
        if (targetVersion <= GradleVersion.version("2.0")) {
            // Implicit tasks were ignored
            return []
        }
        return rootProjectImplicitTasks
    }

    /**
     * Returns the set of implicit tasks returned by GradleProject.getTasks()
     *
     * <p>Note that in some versions the handling of implicit tasks was broken, so this method may return a different value
     * to {@link #getRootProjectImplicitTasks()}.
     */
    Set<String> getRootProjectImplicitTasksForGradleProjectModel() {
        if (targetVersion == GradleVersion.version("1.6")) {
            // Implicit tasks were ignored, and setupBuild was added as a regular task
            return ['setupBuild']
        }

        targetVersion < GradleVersion.version("2.3") ? [] : rootProjectImplicitTasks
    }

    public <T> T loadToolingModel(Class<T> modelClass) {
        withConnection { connection -> connection.getModel(modelClass) }
    }

    protected static GradleVersion getTargetVersion() {
        GradleVersion.version(targetDist.version.baseVersion.version)
    }
}
