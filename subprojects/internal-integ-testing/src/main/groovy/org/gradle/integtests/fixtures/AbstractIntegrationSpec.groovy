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

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Config
import org.gradle.api.Action
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheBuildOperationsFixture
import org.gradle.integtests.fixtures.executer.ArtifactBuilder
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleBackedArtifactBuilder
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.InProcessGradleExecuter
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
import org.gradle.util.internal.VersionNumber
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher
import org.intellij.lang.annotations.Language
import org.junit.Assume
import org.junit.Rule
import spock.lang.Specification

import java.nio.file.Files
import java.util.regex.Pattern

import static org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout.DEFAULT_TIMEOUT_SECONDS
import static org.gradle.test.fixtures.dsl.GradleDsl.GROOVY
import static org.gradle.util.Matchers.matchesRegexp
import static org.gradle.util.Matchers.normalizedLineSeparators

/**
 * Spockified version of AbstractIntegrationTest.
 *
 * Plan is to bring features over as needed.
 */
@CleanupTestDirectory
@SuppressWarnings("IntegrationTestFixtures")
@IntegrationTestTimeout(DEFAULT_TIMEOUT_SECONDS)
abstract class AbstractIntegrationSpec extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    private TestFile testDirOverride = null

    protected DocumentationRegistry documentationRegistry = new DocumentationRegistry()

    GradleDistribution distribution = new UnderDevelopmentGradleDistribution(getBuildContext())
    private GradleExecuter executor
    private boolean ignoreCleanupAssertions

    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    GradleExecuter getExecuter() {
        if (executor == null) {
            executor = createExecuter()
            if (ignoreCleanupAssertions) {
                executor.ignoreCleanupAssertions()
            }
        }
        return executor
    }

    BuildTestFixture buildTestFixture = new BuildTestFixture(temporaryFolder)

    IntegrationTestBuildContext getBuildContext() {
        return IntegrationTestBuildContext.INSTANCE
    }

    M2Installation m2 = new M2Installation(temporaryFolder)

    private ExecutionResult currentResult
    private ExecutionFailure currentFailure
    public final MavenFileRepository mavenRepo = new MavenFileRepository(temporaryFolder.testDirectory.file("maven-repo"))
    public final IvyFileRepository ivyRepo = new IvyFileRepository(temporaryFolder.testDirectory.file("ivy-repo"))

    protected int maxHttpRetries = 1
    protected Integer maxUploadAttempts

    @Lazy private isAtLeastGroovy4 = VersionNumber.parse(GroovySystem.version).major >= 4

    def setup() {
        // Verify that the previous test (or fixtures) has cleaned up state correctly
        m2.assertNoLeftoverState()

        m2.isolateMavenLocalRepo(executer)
        executer.beforeExecute {
            withArgument("-Dorg.gradle.internal.repository.max.tentatives=$maxHttpRetries")
            if (maxUploadAttempts != null) {
                withArgument("-Dorg.gradle.internal.network.retry.max.attempts=$maxUploadAttempts")
            }
        }
    }

    def cleanup() {
        executer.cleanup()
        m2.cleanupState()

        // Verify that the test (or fixtures) has cleaned up state correctly
        m2.assertNoLeftoverState()
    }

    private void recreateExecuter() {
        if (executor != null) {
            executor.cleanup()
        }
        executor = null
    }

    GradleExecuter createExecuter() {
        new GradleContextualExecuter(distribution, temporaryFolder, getBuildContext())
    }

    /**
     * Some integration tests need to run git commands in test directory,
     * but distributed-test-remote-executor has no .git directory so we init a "dummy .git dir".
     */
    void initGitDir() {
        Git.init().setDirectory(testDirectory).call().withCloseable { Git git ->
            // Clear config hierarchy to avoid global configuration loaded from user home
            for (Config config = git.repository.config; config != null; config = config.getBaseConfig()) {
                //noinspection GroovyAccessibility
                config.clear()
            }
            testDirectory.file('initial-commit').createNewFile()
            git.add().addFilepattern("initial-commit").call()
            git.commit().setMessage("Initial commit").call()
        }
    }

    /**
     * Want syntax highlighting inside of IntelliJ? Consider using {@link AbstractIntegrationSpec#buildFile(String)}
     */
    TestFile getBuildFile() {
        testDirectory.file(getDefaultBuildFileName())
    }

    String getTestJunitCoordinates() {
        return "junit:junit:4.13"
    }

    void buildFile(@GroovyBuildScriptLanguage String script) {
        groovyFile(buildFile, script)
    }

    void settingsFile(@GroovyBuildScriptLanguage String script) {
        groovyFile(settingsFile, script)
    }

    /**
     * Provides best-effort groovy script syntax highlighting.
     * The highlighting is imperfect since {@link GroovyBuildScriptLanguage} uses stub methods to create a simulated script target environment.
     */
    void groovyFile(TestFile targetBuildFile, @GroovyBuildScriptLanguage String script) {
        targetBuildFile << script
    }

    String groovyScript(@GroovyBuildScriptLanguage String script) {
        script
    }

    TestFile getBuildKotlinFile() {
        testDirectory.file(defaultBuildKotlinFileName)
    }

    protected String getDefaultBuildFileName() {
        'build.gradle'
    }

    protected String getDefaultBuildKotlinFileName() {
        'build.gradle.kts'
    }

    /**
     * Sets (replacing) the contents of the build.gradle file.
     *
     * To append, use #buildFile(String).
     */
    protected TestFile buildScript(@GroovyBuildScriptLanguage String script) {
        buildFile.text = script
        buildFile
    }

    /**
     * Sets (replacing) the contents of the settings.gradle file.
     *
     * To append, use #settingsFile(String)
     */
    protected TestFile settingsScript(@GroovyBuildScriptLanguage String script) {
        settingsFile.text = script
        settingsFile
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

    def singleProjectBuild(String projectName, @DelegatesTo(value = BuildTestFile, strategy = Closure.DELEGATE_FIRST) Closure cl = {}) {
        buildTestFixture.singleProjectBuild(projectName, cl)
    }

    def multiProjectBuild(String projectName, List<String> subprojects, @DelegatesTo(value = BuildTestFile, strategy = Closure.DELEGATE_FIRST) Closure cl = {}) {
        multiProjectBuild(projectName, subprojects, CompiledLanguage.JAVA, cl)
    }

    def multiProjectBuild(String projectName, List<String> subprojects, CompiledLanguage language, @DelegatesTo(value = BuildTestFile, strategy = Closure.DELEGATE_FIRST) Closure cl = {}) {
        buildTestFixture.multiProjectBuild(projectName, subprojects, language, cl)
    }

    protected TestNameTestDirectoryProvider getTestDirectoryProvider() {
        temporaryFolder
    }

    protected ConfigurationCacheBuildOperationsFixture newConfigurationCacheFixture() {
        return new ConfigurationCacheBuildOperationsFixture(new BuildOperationsFixture(executer, temporaryFolder))
    }

    TestFile getTestDirectory() {
        if (testDirOverride != null) {
            return testDirOverride
        }
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

    TestFile groovyTestSourceFile(@Language("groovy") String source) {
        file("src/test/groovy/Test.groovy") << source
    }

    TestFile javaTestSourceFile(@Language("java") String source) {
        file("src/test/java/Test.java") << source
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

    /**
     * This is expensive as it creates a complete copy of the distribution inside the test directory.
     * Only use this for testing custom modifications of a distribution.
     */
    protected GradleExecuter requireIsolatedGradleDistribution() {
        def isolatedGradleHomeDir = getTestDirectory().file("gradle-home")
        getBuildContext().gradleHomeDir.copyTo(isolatedGradleHomeDir)
        distribution = new UnderDevelopmentGradleDistribution(getBuildContext(), isolatedGradleHomeDir)
        recreateExecuter()
        executer.requireIsolatedDaemons() //otherwise we might connect to a running daemon from the original installation location
        executer
    }

    /**
     * Configure the test directory so that there is no settings.gradle(.kts) anywhere in its hierarchy.
     * By default, tests will run under the `build` directory and so a settings.gradle will be present. Most tests do not care but some are sensitive to this.
     */
    void useTestDirectoryThatIsNotEmbeddedInAnotherBuild() {
        // Cannot use Files.createTempDirectory(String) as other tests mess with the static state used by that method
        // so that it creates directories under the root directory of the Gradle build.
        // However, in this case the test requires a directory that is not located under any Gradle build. So use
        // the 'java.io.tmpdir' system property directly
        TestFile tmpDir = new TestFile(System.getProperty("java.io.tmpdir"))
        def undefinedBuildDirectory = Files.createTempDirectory(tmpDir.toPath(), "gradle").toFile()
        testDirOverride = new TestFile(undefinedBuildDirectory)
        assertNoDefinedBuild(testDirectory)
        executer.beforeExecute {
            executer.inDirectory(testDirectory)
            executer.ignoreMissingSettingsFile()
        }
    }

    void assertNoDefinedBuild(TestFile testDirectory) {
        def file = findBuildDefinition(testDirectory)
        if (file != null) {
            throw new AssertionError("""Found unexpected build definition $file visible to test directory $testDirectory
tmpdir is currently ${System.getProperty("java.io.tmpdir")}""")
        }
    }

    private TestFile findBuildDefinition(TestFile testDirectory) {
        def buildFile = testDirectory.file(".gradle")
        if (buildFile.exists()) {
            return buildFile
        }
        def currentDirectory = testDirectory
        for (; ;) {
            def settingsFile = currentDirectory.file(settingsFileName)
            if (settingsFile.exists()) {
                return settingsFile
            }
            settingsFile = currentDirectory.file(settingsKotlinFileName)
            if (settingsFile.exists()) {
                return settingsFile
            }
            currentDirectory = currentDirectory.parentFile
            if (currentDirectory == null) {
                break
            }
        }
    }

    AbstractIntegrationSpec withBuildCache() {
        executer.withBuildCacheEnabled()
        this
    }

    AbstractIntegrationSpec withBuildCacheNg() {
        executer.withBuildCacheNgEnabled()
        this
    }

    /**
     * Synonym for succeeds()
     */
    protected ExecutionResult run(String... tasks) {
        succeeds(*tasks)
    }

    protected ExecutionResult run(List<String> tasks) {
        succeeds(tasks.toArray(new String[tasks.size()]))
    }

    protected GradleExecuter args(String... args) {
        executer.withArguments(args)
    }

    protected GradleExecuter withDebugLogging() {
        executer.withArgument("-d")
    }

    protected ExecutionResult succeeds(String... tasks) {
        result = executer.withTasks(*tasks).run()
        return result
    }

    ExecutionResult getResult() {
        if (currentResult == null) {
            throw new IllegalStateException("No build result is available yet.")
        }
        return currentResult
    }

    void setResult(ExecutionResult result) {
        currentFailure = null
        currentResult = result
    }

    ExecutionFailure getFailure() {
        if (currentFailure == null) {
            throw new IllegalStateException("No build failure result is available yet.")
        }
        return currentFailure
    }

    boolean isFailed() {
        return currentFailure != null
    }

    void setFailure(ExecutionFailure failure) {
        currentResult = failure
        currentFailure = failure
    }

    protected ExecutionFailure runAndFail(String... tasks) {
        fails(*tasks)
    }

    protected ExecutionFailure fails(String... tasks) {
        failure = executer.withTasks(*tasks).runWithFailure()
        assert !buildOperations.problems().empty
        return failure
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

    protected void failureHasCause(Pattern pattern) {
        failure.assertThatCause(matchesRegexp(pattern))
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
        def executer = new InProcessGradleExecuter(distribution, temporaryFolder)
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

    protected configureRepositoryCredentials(String username, String password, String repositoryName = "maven") {
        // configuration property prefix - the identity - is determined from the repository name
        // https://docs.gradle.org/current/userguide/userguide_single.html#sec:handling_credentials
        propertiesFile << """
        ${repositoryName}Username=${username}
        ${repositoryName}Password=${password}
        """
    }

    protected configureRepositoryKeys(String accessKey, String secretKey, String repositoryName) {
        // configuration property prefix - the identity - is determined from the repository name
        // https://docs.gradle.org/current/userguide/userguide_single.html#sec:handling_credentials
        propertiesFile << """
        ${repositoryName}AccessKey=${accessKey}
        ${repositoryName}SecretKey=${secretKey}
        """
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

    TestFile createDir(String name, @DelegatesTo(value = TestWorkspaceBuilder.class, strategy = Closure.DELEGATE_FIRST) Closure cl = {}) {
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

    void postBuildOutputDoesNotContain(String string) {
        assertHasResult()
        result.assertNotPostBuildOutput(string.trim())
    }

    void outputDoesNotContain(String string) {
        assertHasResult()
        result.assertNotOutput(string.trim())
    }

    static String mavenCentralRepository(GradleDsl dsl = GROOVY) {
        RepoScriptBlockUtil.mavenCentralRepository(dsl)
    }

    static String googleRepository() {
        RepoScriptBlockUtil.googleRepository()
    }

    /**
     * Called by {@link ToBeFixedForConfigurationCacheExtension} when a test fails as expected so no further checks are applied.
     */
    void ignoreCleanupAssertions() {
        this.ignoreCleanupAssertions = true
        if (executor != null) {
            executor.ignoreCleanupAssertions()
        }
    }

    /**
     * Called by {@link org.gradle.integtests.fixtures.extensions.AbstractMultiTestInterceptor} when the test class is reused
     */
    void resetExecuter() {
        this.ignoreCleanupAssertions = false
        recreateExecuter()
    }

    void assumeGroovy3() {
        Assume.assumeFalse('Requires Groovy 3', isAtLeastGroovy4)
    }

    void assumeGroovy4() {
        Assume.assumeTrue('Requires Groovy 4', isAtLeastGroovy4)
    }

    /**
     * Generates a `repositories` block pointing to the standard maven repo fixture.
     *
     * This is often required for running with configuration cache enabled, as
     * configuration cache eagerly resolves dependencies when storing the classpath.
     */
    protected String mavenTestRepository(GradleDsl dsl = GROOVY) {
        """
        repositories {
            ${RepoScriptBlockUtil.repositoryDefinition(dsl, "maven", mavenRepo.rootDir.name, mavenRepo.uri.toString())}
        }
        """
    }

    /**
     * Generates a `repositories` block pointing to the standard Ivy repo fixture.
     *
     * This is often required for running with configuration cache enabled, as
     * configuration cache eagerly resolves dependencies when storing the classpath.
     */
    protected String ivyTestRepository(GradleDsl dsl = GROOVY) {
        """
        repositories {
            ${RepoScriptBlockUtil.repositoryDefinition(dsl, "ivy", ivyRepo.rootDir.name, ivyRepo.uri.toString())}
        }
        """
    }

    protected String emptyJavaClasspath() {
        """
            tasks.compileJava {
                // Avoid resolving the classpath when caching the configuration
                classpath = files()
            }
        """
    }
}
