/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.fixtures.ScriptDslFixture
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.initialization.ParallelismBuildOptions.ParallelOption
import static org.gradle.initialization.StartParameterBuildOptions.BuildCacheOption
import static org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not

abstract class AbstractInitIntegrationSpec extends AbstractIntegrationSpec {
    TestFile containerDir
    TestFile targetDir
    TestFile subprojectDir

    abstract String subprojectName()

    def setup() {
        file("settings.gradle") << """
            // This is here to prevent Gradle searching up to find the build's settings.gradle
        """
        initializeIntoTestDir()
        executer.withRepositoryMirrors()
    }

    void initializeIntoTestDir() {
        containerDir = testDirectory
        targetDir = containerDir.createDir("some-thing")
        subprojectDir = subprojectName() ? targetDir.file(subprojectName()) : targetDir
        executer.beforeExecute {
            executer.inDirectory(targetDir)
            executer.ignoreMissingSettingsFile()
        }
    }

    @Override
    void useTestDirectoryThatIsNotEmbeddedInAnotherBuild() {
        super.useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        initializeIntoTestDir()
        assertNoDefinedBuild(targetDir)
    }

    protected void assertTestPassed(String className, String name) {
        def result = new DefaultTestExecutionResult(subprojectDir)
        result.assertTestClassesExecuted(className)
        result.testClass(className).assertTestPassed(name)
    }

    protected void assertFunctionalTestPassed(String className, String name) {
        def result = new DefaultTestExecutionResult(subprojectDir, 'build', '', '', 'functionalTest')
        result.assertTestClassesExecuted(className)
        result.testClass(className).assertTestPassed(name)
    }

    protected void assertWrapperGenerated() {
        targetDir.file("gradlew").assertIsFile()
        targetDir.file("gradlew.bat").assertIsFile()
        targetDir.file("gradle/wrapper/gradle-wrapper.jar").assertIsFile()
        targetDir.file("gradle/wrapper/gradle-wrapper.properties").assertIsFile()
    }

    protected void commonFilesGenerated(BuildInitDsl scriptDsl, dslFixture = dslFixtureFor(scriptDsl)) {
        dslFixture.assertGradleFilesGenerated()
        targetDir.file(".gitignore").assertIsFile()
        targetDir.file(".gitattributes").assertIsFile()
        mavenCentralRepositoryDeclared(scriptDsl)

        gradlePropertiesGenerated {
            assertConfigurationCacheEnabled()
        }
    }

    protected void commonJvmFilesGenerated(BuildInitDsl scriptDsl) {
        commonFilesGenerated(scriptDsl)
        subprojectDir.file("src/main/resources").assertIsDir()
        subprojectDir.file("src/test/resources").assertIsDir()
    }

    protected void gradlePropertiesGenerated(@DelegatesTo(GradlePropertiesAsserts) Closure<?> assertions) {
        gradlePropertiesGeneratedIn(targetDir, assertions)
    }

    protected void gradlePropertiesGeneratedIn(TestFile settingsDir, @DelegatesTo(GradlePropertiesAsserts) Closure<?> assertions) {
        def propsFile = settingsDir.file("gradle.properties")
        propsFile.assertIsFile()

        def props = new Properties()
        try (def propsData = propsFile.newInputStream()) {
            props.load(propsData)
        }

        assertions.setDelegate(new GradlePropertiesAsserts(props))
        assertions.call()
    }

    protected ScriptDslFixture dslFixtureFor(BuildInitDsl dsl) {
        ScriptDslFixture.of(dsl, targetDir, subprojectName())
    }

    protected ScriptDslFixture rootProjectDslFixtureFor(BuildInitDsl dsl) {
        ScriptDslFixture.of(dsl, targetDir, null)
    }

    protected TestFile pom() {
        targetDir.file("pom.xml") << """
      <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>
        <groupId>util</groupId>
        <artifactId>util</artifactId>
        <version>2.5</version>
        <packaging>jar</packaging>
      </project>"""
    }

    protected ExecutionResult runInitWith(BuildInitDsl dsl, String... initOptions) {
        def tasks = ['init', '--dsl', dsl.id]
        tasks.addAll(initOptions)
        run tasks
    }

    protected ExecutionResult initFailsWith(BuildInitDsl dsl, String... initOptions) {
        def tasks = ['init', '--dsl', dsl.id]
        tasks.addAll(initOptions)
        fails(*tasks)
    }

    private void mavenCentralRepositoryDeclared(BuildInitDsl scriptDsl) {
        def scriptFile = subprojectDir.file(scriptDsl.fileNameFor("build"))
        def scriptText = scriptFile.exists() ? scriptFile.text : ""
        if (scriptText.contains("repositories")) {
            assertThat(scriptText, containsString("mavenCentral()"))
            assertThat(scriptText, containsString("Use Maven Central for resolving dependencies."))
            assertThat(scriptText, not(containsString("jcenter()")))
            assertThat(scriptText, not(containsString("Use JCenter for resolving dependencies.")))
        }
    }

    protected static class GradlePropertiesAsserts {
        private final Properties properties

        private GradlePropertiesAsserts(Properties properties) {
            this.properties = properties
        }

        void assertParallelEnabled() {
            assertEnabled(ParallelOption.GRADLE_PROPERTY)
        }

        void assertCachingEnabled() {
            assertEnabled(BuildCacheOption.GRADLE_PROPERTY)
        }

        void assertConfigurationCacheEnabled() {
            assertEnabled(ConfigurationCacheOption.PROPERTY_NAME)
        }

        private void assertEnabled(String property) {
            assert Boolean.valueOf(properties.getProperty(property))
        }
    }
}
