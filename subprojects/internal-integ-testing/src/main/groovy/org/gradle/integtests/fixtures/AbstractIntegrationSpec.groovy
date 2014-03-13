/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.fixtures

import org.gradle.api.Action
import org.gradle.integtests.fixtures.executer.*
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenLocalRepository
import org.junit.Rule
import spock.lang.Specification

/**
 * Spockified version of AbstractIntegrationTest.
 * 
 * Plan is to bring features over as needed.
 */
class AbstractIntegrationSpec extends Specification implements TestDirectoryProvider {

    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    GradleDistribution distribution = new UnderDevelopmentGradleDistribution()
    GradleExecuter executer = new GradleContextualExecuter(distribution, temporaryFolder)

    ExecutionResult result
    ExecutionFailure failure
    private MavenFileRepository mavenRepo
    private IvyFileRepository ivyRepo

    protected TestFile getBuildFile() {
        testDirectory.file('build.gradle')
    }

    protected TestFile buildScript(String script) {
        buildFile.text = script
        buildFile
    }

    protected TestFile getSettingsFile() {
        testDirectory.file('settings.gradle')
    }

    TestFile getTestDirectory() {
        temporaryFolder.testDirectory
    }

    protected TestFile file(Object... path) {
        if (path.length == 1 && path[0] instanceof TestFile) {
            return path[0] as TestFile
        }
        getTestDirectory().file(path);
    }

    protected GradleExecuter sample(Sample sample) {
        inDirectory(sample.dir)
    }

    protected GradleExecuter inDirectory(String path) {
        inDirectory(file(path))
    }

    protected GradleExecuter inDirectory(File directory) {
        executer.inDirectory(directory);
    }

    protected GradleExecuter projectDir(path) {
        executer.usingProjectDirectory(file(path))
    }

    protected GradleExecuter requireOwnGradleUserHomeDir() {
        executer.requireOwnGradleUserHomeDir()
        executer
    }

    /**
     * Synonym for succeeds()
     */
    protected ExecutionResult run(String... tasks) {
        succeeds(*tasks)
    }

    protected GradleExecuter args(String... args) {
        executer.withArguments(args)
    }

    protected GradleExecuter withDebugLogging() {
        executer.withArguments("-d")
    }

    protected ExecutionResult succeeds(String... tasks) {
        result = executer.withTasks(*tasks).run()
    }

    protected ExecutionFailure runAndFail(String... tasks) {
        fails(*tasks)
    }
    
    protected ExecutionFailure fails(String... tasks) {
        failure = executer.withTasks(*tasks).runWithFailure()
        result = failure
    }
    
    protected List<String> getExecutedTasks() {
        assertHasResult()
        result.executedTasks
    }
    
    protected Set<String> getSkippedTasks() {
        assertHasResult()
        result.skippedTasks
    }
    
    protected List<String> getNonSkippedTasks() {
        executedTasks - skippedTasks
    }
    
    protected void executedAndNotSkipped(String... tasks) {
        tasks.each {
            assert it in executedTasks
            assert !skippedTasks.contains(it)
        }
    }

    protected void skipped(String... tasks) {
        tasks.each {
            assert it in executedTasks
            assert skippedTasks.contains(it)
        }
    }

    protected void notExecuted(String... tasks) {
        tasks.each {
            assert !(it in executedTasks)
        }
    }

    protected void executed(String... tasks) {
        tasks.each {
            assert (it in executedTasks)
        }
    }

    protected void failureHasCause(String cause) {
        failure.assertHasCause(cause)
    }
    
    private assertHasResult() {
        assert result != null : "result is null, you haven't run succeeds()"
    }

    String getOutput() {
        result.output
    }

    String getErrorOutput() {
        result.error
    }

    ArtifactBuilder artifactBuilder() {
        def executer = distribution.executer(temporaryFolder)
        executer.withGradleUserHomeDir(this.executer.getGradleUserHomeDir())
        return new GradleBackedArtifactBuilder(executer, getTestDirectory().file("artifacts"))
    }

    public MavenFileRepository maven(TestFile repo) {
        return new MavenFileRepository(repo)
    }

    public MavenFileRepository maven(Object repo) {
        return new MavenFileRepository(file(repo))
    }

    public MavenLocalRepository mavenLocal(Object repo) {
        return new MavenLocalRepository(file(repo))
    }

    public MavenFileRepository getMavenRepo() {
        if (mavenRepo == null) {
            mavenRepo = new MavenFileRepository(file("maven-repo"))
        }
        return mavenRepo
    }

    public IvyFileRepository ivy(TestFile repo) {
        return new IvyFileRepository(repo)
    }

    public IvyFileRepository ivy(Object repo) {
        return new IvyFileRepository(file(repo))
    }

    public IvyFileRepository getIvyRepo() {
        if (ivyRepo == null) {
            ivyRepo = new IvyFileRepository(file("ivy-repo"))
        }
        return ivyRepo
    }

    public GradleExecuter using(Action<GradleExecuter> action) {
        action.execute(executer)
        executer
    }

    def createZip(String name, Closure cl) {
        TestFile zipRoot = file("${name}.root")
        TestFile zip = file(name)
        zipRoot.create(cl)
        zipRoot.zipTo(zip)
    }

    def createDir(String name, Closure cl) {
        TestFile root = file(name)
        root.create(cl)
    }
}