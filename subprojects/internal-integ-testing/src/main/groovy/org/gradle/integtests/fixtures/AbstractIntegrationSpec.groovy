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
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.file.TestWorkspaceBuilder
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.maven.M2Installation
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenLocalRepository
import spock.lang.Specification
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher
import org.junit.Rule

import static org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout.DEFAULT_TIMEOUT_SECONDS
import static org.gradle.test.fixtures.dsl.GradleDsl.GROOVY
import static org.gradle.util.Matchers.normalizedLineSeparators

/**
 * Spockified version of AbstractIntegrationTest.
 *
 * Plan is to bring features over as needed.
 */
@CleanupTestDirectory
@SuppressWarnings("IntegrationTestFixtures")
@IntegrationTestTimeout(DEFAULT_TIMEOUT_SECONDS)
class AbstractIntegrationSpec extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    GradleDistribution distribution = new UnderDevelopmentGradleDistribution(getBuildContext())
    GradleExecuter executer = createExecuter()

    BuildTestFixture buildTestFixture = new BuildTestFixture(temporaryFolder)

    IntegrationTestBuildContext getBuildContext() {
        return IntegrationTestBuildContext.INSTANCE
    }

    M2Installation m2 = new M2Installation(temporaryFolder)

    ExecutionResult result
    ExecutionFailure failure
    private MavenFileRepository mavenRepo
    private IvyFileRepository ivyRepo

    protected int maxHttpRetries = 1
    protected Integer maxUploadAttempts

    def setup() {
        m2.isolateMavenLocalRepo(executer)
        executer.beforeExecute {
            executer.withArgument("-Dorg.gradle.internal.repository.max.tentatives=$maxHttpRetries")
            if (maxUploadAttempts != null) {
                executer.withArgument("-Dorg.gradle.internal.network.retry.max.attempts=$maxUploadAttempts")
            }
        }
    }

    def cleanup() {
        executer.cleanup()
    }

    GradleContextualExecuter createExecuter() {
        new GradleContextualExecuter(distribution, temporaryFolder, getBuildContext())
    }

    TestFile getBuildFile() {
        testDirectory.file(getDefaultBuildFileName())
    }

    TestFile getBuildKotlinFile() {
        testDirectory.file(getDefaultBuildKotlinFileName())
    }

    protected String getDefaultBuildFileName() {
        'build.gradle'
    }

    protected String getDefaultBuildKotlinFileName() {
        'build.gradle.kts'
    }

    protected TestFile buildScript(String script) {
        buildFile.text = script
        buildFile
    }

    protected TestFile getSettingsFile() {
        testDirectory.file(settingsFileName)
    }

    protected TestFile getSettingsKotlinFile() {
        testDirectory.file(settingsKotlinFileName)
    }

    protected TestFile getPropertiesFile() {
        testDirectory.file('gradle.properties')
    }

    protected static String getSettingsFileName() {
        return 'settings.gradle'
    }

    protected static String getSettingsKotlinFileName() {
        return 'settings.gradle.kts'
    }

    def singleProjectBuild(String projectName, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        buildTestFixture.singleProjectBuild(projectName, cl)
    }

    def multiProjectBuild(String projectName, List<String> subprojects, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        multiProjectBuild(projectName, subprojects, CompiledLanguage.JAVA, cl)
    }

    def multiProjectBuild(String projectName, List<String> subprojects, CompiledLanguage language, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        buildTestFixture.multiProjectBuild(projectName, subprojects, language, cl)
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
        getTestDirectory().file(path)
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

    TestFile javaGeneratedSourceFile(String fqcn) {
        generatedSourceFile("java", "main", fqcn)
    }

    TestFile groovyGeneratedSourceFile(String fqcn) {
        generatedSourceFile("groovy", "main", fqcn)
    }

    TestFile scalaGeneratedSourceFile(String fqcn) {
        generatedSourceFile("scala", "main", fqcn)
    }

    TestFile generatedSourceFile(String language, String sourceSet, String fqcn) {
        file("build/generated/sources/annotationProcessor/", language, sourceSet, fqcn)
    }

    protected GradleExecuter sample(Sample sample) {
        inDirectory(sample.dir)
    }

    protected GradleExecuter inDirectory(String path) {
        inDirectory(file(path))
    }

    protected GradleExecuter inDirectory(File directory) {
        executer.inDirectory(directory)
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
     * This is expensive as it creates a complete copy of the distribution inside the test directory.
     * Only use this for testing custom modifications of a distribution.
     */
    protected GradleExecuter requireIsolatedGradleDistribution() {
        def isolatedGradleHomeDir = getTestDirectory().file("gradle-home")
        getBuildContext().gradleHomeDir.copyTo(isolatedGradleHomeDir)
        distribution = new UnderDevelopmentGradleDistribution(getBuildContext(), isolatedGradleHomeDir)
        executer = createExecuter()
        executer.requireGradleDistribution()
        executer.requireIsolatedDaemons() //otherwise we might connect to a running daemon from the original installation location
        executer
    }

    AbstractIntegrationSpec withBuildCache() {
        executer.withBuildCacheEnabled()
        this
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

    protected void executedAndNotSkipped(String... tasks) {
        assertHasResult()
        tasks.each {
            result.assertTaskNotSkipped(it)
        }
    }

    protected void noneSkipped() {
        assertHasResult()
        result.assertTasksSkipped()
    }

    protected void allSkipped() {
        assertHasResult()
        result.assertTasksNotSkipped()
    }

    protected void skipped(String... tasks) {
        assertHasResult()
        tasks.each {
            result.assertTaskSkipped(it)
        }
    }

    protected void notExecuted(String... tasks) {
        assertHasResult()
        tasks.each {
            result.assertTaskNotExecuted(it)
        }
    }

    protected void executed(String... tasks) {
        assertHasResult()
        tasks.each {
            result.assertTaskExecuted(it)
        }
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

    AbstractIntegrationSpec withMaxHttpRetryCount(int count) {
        maxHttpRetries = count
        this
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
        zipRoot.deleteDir()
        TestFile zip = file(name)
        zipRoot.create(cl)
        zipRoot.zipTo(zip)
        return zip
    }

    def createDir(String name, @DelegatesTo(value = TestWorkspaceBuilder.class, strategy = Closure.DELEGATE_FIRST) Closure cl) {
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

    void postBuildOutputContains(String string) {
        assertHasResult()
        result.assertHasPostBuildOutput(string.trim())
    }

    void outputDoesNotContain(String string) {
        assertHasResult()
        result.assertNotOutput(string.trim())
    }

    static String jcenterRepository(GradleDsl dsl = GROOVY) {
        RepoScriptBlockUtil.jcenterRepository(dsl)
    }

    static String mavenCentralRepository() {
        RepoScriptBlockUtil.mavenCentralRepository()
    }

    static String googleRepository() {
        RepoScriptBlockUtil.googleRepository()
    }
}
