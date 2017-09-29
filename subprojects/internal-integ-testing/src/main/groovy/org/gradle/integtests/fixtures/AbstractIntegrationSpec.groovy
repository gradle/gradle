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
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.integtests.fixtures.executer.ArtifactBuilder
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleBackedArtifactBuilder
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.maven.M2Installation
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenLocalRepository
import org.gradle.testing.internal.util.Specification
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher
import org.junit.Rule

import static org.gradle.util.Matchers.normalizedLineSeparators

/**
 * Spockified version of AbstractIntegrationTest.
 *
 * Plan is to bring features over as needed.
 */
@CleanupTestDirectory
class AbstractIntegrationSpec extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    GradleDistribution distribution = new UnderDevelopmentGradleDistribution(getBuildContext())
    GradleExecuter executer = new GradleContextualExecuter(distribution, temporaryFolder, getBuildContext())
    BuildTestFixture buildTestFixture = new BuildTestFixture(temporaryFolder)

    IntegrationTestBuildContext getBuildContext() {
        return IntegrationTestBuildContext.INSTANCE;
    }

//    @Rule
    M2Installation m2 = new M2Installation(temporaryFolder)

    ExecutionResult result
    ExecutionFailure failure
    private MavenFileRepository mavenRepo
    private IvyFileRepository ivyRepo

    def cleanup() {
        executer.cleanup()
    }

    protected TestFile getBuildFile() {
        testDirectory.file(getDefaultBuildFileName())
    }

    protected String getDefaultBuildFileName() {
        'build.gradle'
    }

    protected TestFile buildScript(String script) {
        buildFile.text = script
        buildFile
    }

    protected TestFile getSettingsFile() {
        testDirectory.file('settings.gradle')
    }

    def singleProjectBuild(String projectName, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        buildTestFixture.singleProjectBuild(projectName, cl)
    }

    def multiProjectBuild(String projectName, List<String> subprojects, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        buildTestFixture.multiProjectBuild(projectName, subprojects, cl)
    }

    protected TestNameTestDirectoryProvider getTestDirectoryProvider() {
        temporaryFolder
    }

    TestFile getTestDirectory() {
        temporaryFolder.testDirectory
    }

    TestFile file(Object... path) {
        if (path.length == 1 && path[0] instanceof TestFile) {
            return path[0] as TestFile
        }
        getTestDirectory().file(path);
    }

    TestFile javaClassFile(String fqcn) {
        classFile("java", "main", fqcn)
    }

    TestFile groovyClassFile(String fqcn) {
        classFile("groovy", "main", fqcn)
    }

    TestFile scalaClassFile(String fqcn) {
        classFile("scala", "main", fqcn)
    }

    TestFile classFile(String language, String sourceSet, String fqcn) {
        file("build/classes/", language, sourceSet, fqcn)
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

    protected GradleExecuter requireGradleDistribution() {
        executer.requireGradleDistribution()
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
        executer.withArgument("-d")
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

    protected void assertTaskOrder(Object... tasks) {
        assertHasResult()
        result.assertTaskOrder(tasks)
    }

    protected void failureHasCause(String cause) {
        failure.assertHasCause(cause)
    }

    protected void failureDescriptionStartsWith(String description) {
        failure.assertThatDescription(containsNormalizedString(description))
    }

    protected void failureDescriptionContains(String description) {
        failure.assertThatDescription(containsNormalizedString(description))
    }

    protected void failureCauseContains(String description) {
        failure.assertThatCause(containsNormalizedString(description))
    }

    protected Matcher<String> containsNormalizedString(String description) {
        normalizedLineSeparators(CoreMatchers.containsString(description))
    }

    private assertHasResult() {
        assert result != null: "result is null, you haven't run succeeds()"
    }

    String getOutput() {
        result.output
    }

    String getErrorOutput() {
        result.error
    }

    ArtifactBuilder artifactBuilder() {
        def executer = distribution.executer(temporaryFolder, getBuildContext())
        executer.withGradleUserHomeDir(this.executer.getGradleUserHomeDir())
        for (int i = 1; ; i++) {
            def dir = getTestDirectory().file("artifacts-$i")
            if (!dir.exists()) {
                return new GradleBackedArtifactBuilder(executer, dir)
            }
        }
    }

    def jarWithClasses(Map<String, String> javaSourceFiles, TestFile jarFile) {
        def builder = artifactBuilder()
        for (Map.Entry<String, String> entry : javaSourceFiles.entrySet()) {
            builder.sourceFile(entry.key + ".java").text = entry.value
        }
        builder.buildJar(jarFile)
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

    public MavenFileRepository publishedMavenModules(String... modulesToPublish) {
        modulesToPublish.each { String notation ->
            def modules = notation.split("->").reverse()
            def current
            modules.each { String module ->
                def s = new TestDependency(module)
                def m = mavenRepo.module(s.group, s.name, s.version)
                current = current ? m.dependsOn(current.groupId, current.artifactId, current.version).publish() : m.publish()
            }
        }
        mavenRepo
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
        return zip
    }

    def createDir(String name, Closure cl) {
        TestFile root = file(name)
        root.create(cl)
    }

    /**
     * Replaces the given text in the build script with new value, asserting that the change was actually applied (ie the text was present).
     */
    void editBuildFile(String oldText, String newText) {
        def newContent = buildFile.text.replace(oldText, newText)
        assert newContent != buildFile.text
        buildFile.text = newContent
    }

    /**
     * Creates a JAR that is unique to the test. The uniqueness is achieved via a properties file with a value containing the path to the test itself.
     */
    def createJarWithProperties(String path, Map<String, ?> properties = [source: 1]) {
        def props = new Properties()
        def sw = new StringWriter()
        props.putAll(properties.collectEntries { k, v -> [k, String.valueOf(v)] })
        props.setProperty(path, testDirectory.path)
        props.store(sw, null)
        file(path).delete()
        createZip(path) {
            file("data.properties") << sw.toString()
        }
    }

    void outputContains(String string) {
        assertHasResult()
        result.assertOutputContains(string.trim())
    }

    static String jcenterRepository() {
        RepoScriptBlockUtil.jcenterRepository()
    }

    static String mavenCentralRepository() {
        RepoScriptBlockUtil.mavenCentralRepository()
    }

    static String googleRepository() {
        RepoScriptBlockUtil.googleRepository()
    }
}
