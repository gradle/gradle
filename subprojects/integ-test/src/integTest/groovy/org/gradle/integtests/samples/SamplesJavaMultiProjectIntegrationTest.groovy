/*
 * Copyright 2010 the original author or authors.
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



package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT
import static org.hamcrest.CoreMatchers.containsString

@Requires(KOTLIN_SCRIPT)
@LeaksFileHandles
class SamplesJavaMultiProjectIntegrationTest extends AbstractIntegrationSpec {
    static final String SHARED_NAME = 'shared'
    static final String API_NAME = 'api'
    static final String WEBAPP_NAME = 'webservice'
    static final String SERVICES_NAME = 'services'
    static final String WEBAPP_PATH = "$SERVICES_NAME/$WEBAPP_NAME" as String

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'java/multiproject')

    def setup() {
        // java/multiproject sample contains buildSrc, which needs global init script to make mirror work
        executer.withGlobalRepositoryMirrors()
    }

    @Unroll
    def "multi project Java project sample with #dsl dsl"() {
        // Build and test projects
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir).withTasks('build').run()

        assertBuildSrcBuilt(dslDir)
        assertEverythingBuilt(dslDir)

        where:
        dsl << ['groovy', 'kotlin']
    }

    private void assertBuildSrcBuilt(TestFile dslDir) {
        TestFile buildSrcDir = dslDir.file('buildSrc')

        buildSrcDir.file('build/libs/buildSrc.jar').assertIsFile()
        def result = new DefaultTestExecutionResult(buildSrcDir)
        result.assertTestClassesExecuted('org.gradle.buildsrc.BuildSrcClassTest')
    }

    private void assertEverythingBuilt(TestFile dslDir) {
        String packagePrefix = 'build/classes/java/main/org/gradle'
        String testPackagePrefix = 'build/classes/java/test/org/gradle'
        String resPackagePrefix = 'build/resources/main/org/gradle'
        String testResPackagePrefix = 'build/resources/test/org/gradle'

        // Check classes and resources
        assertExists(dslDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        assertExists(dslDir, SHARED_NAME, resPackagePrefix, SHARED_NAME, 'main.properties')

        // Check test classes and resources
        assertExists(dslDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'PersonTest.class')
        assertExists(dslDir, SHARED_NAME, testResPackagePrefix, SHARED_NAME, 'test.properties')
        assertExists(dslDir, API_NAME, packagePrefix, API_NAME, 'PersonList.class')
        assertExists(dslDir, WEBAPP_PATH, packagePrefix, WEBAPP_NAME, 'TestTest.class')

        // Check test results and report
        def result = new DefaultTestExecutionResult(dslDir.file(SHARED_NAME))
        result.assertTestClassesExecuted('org.gradle.shared.PersonTest')

        result = new DefaultTestExecutionResult(dslDir.file(WEBAPP_PATH))
        result.assertTestClassesExecuted('org.gradle.webservice.TestTestTest')

        // Check contents of shared jar
        TestFile tmpDir = file("$SHARED_NAME-1.0.jar")
        dslDir.file(SHARED_NAME, "build/libs/$SHARED_NAME-1.0.jar").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/shared/Person.class',
                // package-info.java only gets compiled into class if it contains at least one annotation
                // 'org/gradle/shared/package-info.class',
                'org/gradle/shared/main.properties'
        )

        // Check contents of API jar
        tmpDir = file("$API_NAME-1.0.jar")
        dslDir.file(API_NAME, "build/libs/$API_NAME-1.0.jar").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/api/PersonList.class',
                'org/gradle/apiImpl/Impl.class')

        // Check contents of API jar
        tmpDir = file("$API_NAME-spi-1.0.jar")
        dslDir.file(API_NAME, "build/libs/$API_NAME-spi-1.0.jar").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/api/PersonList.class')

        // Check contents of War
        tmpDir = file("$WEBAPP_NAME-2.5.war")
        dslDir.file(WEBAPP_PATH, "build/libs/$WEBAPP_NAME-2.5.war").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'WEB-INF/classes/org/gradle/webservice/TestTest.class',
                "WEB-INF/lib/$SHARED_NAME-1.0.jar".toString(),
                "WEB-INF/lib/$API_NAME-1.0.jar".toString(),
                "WEB-INF/lib/$API_NAME-spi-1.0.jar".toString(),
                'WEB-INF/lib/commons-collections-3.2.2.jar',
                'WEB-INF/lib/commons-io-2.6.jar',
                'WEB-INF/lib/commons-lang3-3.7.jar'
        )

        // Check contents of dist zip
        tmpDir = file("$API_NAME-1.0.zip")
        dslDir.file(API_NAME, "build/distributions/$API_NAME-1.0.zip").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'README.txt',
                "libs/$API_NAME-spi-1.0.jar".toString(),
                "libs/$SHARED_NAME-1.0.jar".toString(),
                'libs/commons-io-2.6.jar',
                'libs/commons-lang3-3.7.jar'
        )
    }

    @Unroll
    def "multi project javadoc with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir).withTasks('clean', 'javadoc').run()
        dslDir.file(SHARED_NAME, 'build/docs/javadoc/index.html').assertIsFile()
        dslDir.file(SHARED_NAME, 'build/docs/javadoc/index.html').assertContents(containsString("shared 1.0 API"))
        dslDir.file(SHARED_NAME, 'build/docs/javadoc/org/gradle/shared/Person.html').assertIsFile()
        dslDir.file(SHARED_NAME, 'build/docs/javadoc/org/gradle/shared/package-summary.html').assertContents(containsString("These are the shared classes."))
        dslDir.file(API_NAME, 'build/docs/javadoc/index.html').assertIsFile()
        dslDir.file(API_NAME, 'build/docs/javadoc/org/gradle/api/PersonList.html').assertIsFile()
        dslDir.file(API_NAME, 'build/docs/javadoc/org/gradle/api/package-summary.html').assertContents(containsString("These are the API classes"))
        dslDir.file(WEBAPP_PATH, 'build/docs/javadoc/index.html').assertIsFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    def "multi project partial build with #dsl dsl"() {
        String packagePrefix = 'build/classes/java/main/org/gradle'
        String testPackagePrefix = 'build/classes/java/test/org/gradle'

        TestFile dslDir = sample.dir.file(dsl)
        // Partial build using current directory
        if (dsl == 'groovy') {
            executer.inDirectory(dslDir.file("$SERVICES_NAME/$WEBAPP_NAME")).withTasks('buildNeeded').run()
            checkPartialWebAppBuild(packagePrefix, dslDir, testPackagePrefix)

            // check resources
            assertExists(dslDir, SHARED_NAME, 'build/resources/main/org/gradle', SHARED_NAME, 'main.properties')
            assertExists(dslDir, SHARED_NAME, 'build/resources/test/org/gradle', SHARED_NAME, 'test.properties')
        }

        // Partial build using task path
        executer.inDirectory(dslDir).withTasks('clean', "$SHARED_NAME:classes".toString()).run()
        assertExists(dslDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        assertDoesNotExist(dslDir, false, API_NAME, packagePrefix, API_NAME, 'PersonList.class')

        where:
        dsl << ['groovy', 'kotlin']
    }


    @Unroll
    def "buildDependents with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir).withTasks('clean', "$SHARED_NAME:buildDependents".toString()).run()
        assertEverythingBuilt(dslDir)

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    def "clean with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir).withTasks('classes').run()

        def projects = [SHARED_NAME, API_NAME, WEBAPP_PATH]
        projects.each { dslDir.file("$it/build").assertExists() }

        executer.inDirectory(dslDir).withTasks('clean').run()
        projects.each { dslDir.file("$it/build").assertDoesNotExist() }

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    def "no rebuild of project dependencies with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir).withTasks(":$API_NAME:classes").run()
        TestFile sharedJar = dslDir.file("shared/build/libs/shared-1.0.jar")
        TestFile.Snapshot snapshot = sharedJar.snapshot()
        executer.inDirectory(dslDir).withTasks(":$API_NAME:clean", ":$API_NAME:classes").withArguments("-a").run()
        sharedJar.assertHasNotChangedSince(snapshot)

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    def "should not use cache for project dependencies with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir).withTasks(":$API_NAME:checkProjectDependency").run()

        where:
        dsl << ['groovy', 'kotlin']
    }

    private static def checkPartialWebAppBuild(String packagePrefix, TestFile javaprojectDir, String testPackagePrefix) {
        assertExists(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        assertExists(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'PersonTest.class')
        assertExists(javaprojectDir, "$SERVICES_NAME/$WEBAPP_NAME" as String, packagePrefix, WEBAPP_NAME, 'TestTest.class')
        assertExists(javaprojectDir, "$SERVICES_NAME/$WEBAPP_NAME" as String, 'build', 'libs', 'webservice-2.5.war')
    }

    private static void assertExists(TestFile baseDir, Object... path) {
        baseDir.file(path).assertExists()
    }

    static void assertDoesNotExist(TestFile baseDir, boolean shouldExists, Object... path) {
        baseDir.file(path).assertDoesNotExist()
    }
}
