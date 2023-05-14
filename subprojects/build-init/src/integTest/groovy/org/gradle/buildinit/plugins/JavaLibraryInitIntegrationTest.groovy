/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN
import static org.hamcrest.CoreMatchers.allOf

class JavaLibraryInitIntegrationTest extends AbstractJvmLibraryInitIntegrationSpec {

    public static final String SAMPLE_LIBRARY_CLASS = "some/thing/Library.java"
    public static final String SAMPLE_LIBRARY_TEST_CLASS = "some/thing/LibraryTest.java"
    public static final String SAMPLE_SPOCK_LIBRARY_TEST_CLASS = "some/thing/LibraryTest.groovy"

    def "defaults to Kotlin build scripts"() {
        when:
        run ('init', '--type', 'java-library' )

        then:
        dslFixtureFor(KOTLIN).assertGradleFilesGenerated()
    }

    def "creates sample source if no source present with #scriptDsl build scripts"() {
        def dslFixture = dslFixtureFor(scriptDsl)
        when:
        run('init', '--type', 'java-library', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        subprojectDir.file("src/test/java").assertHasDescendants(SAMPLE_LIBRARY_TEST_CLASS)

        and:
        commonJvmFilesGenerated(scriptDsl)
        dslFixture.assertDoesNotUseTestSuites()
        buildFileSeparatesImplementationAndApi(dslFixture)

        when:
        run("build")

        then:
        assertTestPassed("some.thing.LibraryTest", "someLibraryMethodReturnsTrue")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates build using test suites with #scriptDsl build scripts when using --incubating"() {
        def dslFixture = dslFixtureFor(scriptDsl)

        when:
        run ('init', '--type', 'java-library', '--incubating', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        subprojectDir.file("src/test/java").assertHasDescendants(SAMPLE_LIBRARY_TEST_CLASS)

        and:
        commonJvmFilesGenerated(scriptDsl)
        dslFixture.assertHasTestSuite('test')

        when:
        run('test')
        then:
        assertTestPassed("some.thing.LibraryTest", "someLibraryMethodReturnsTrue")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates with gradle.properties when using #scriptDsl build scripts with --incubating"() {
        when:
        run ('init', '--type', 'java-library', '--incubating', '--dsl', scriptDsl.id)

        then:
        gradlePropertiesGenerated()

        when:
        succeeds('test')

        then:
        assertTestPassed("some.thing.LibraryTest", "someLibraryMethodReturnsTrue")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates sample source using spock instead of junit with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-library', '--test-framework', 'spock', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        !subprojectDir.file("src/test/java").exists()
        subprojectDir.file("src/test/groovy").assertHasDescendants(SAMPLE_SPOCK_LIBRARY_TEST_CLASS)

        and:
        commonJvmFilesGenerated(scriptDsl)
        def dslFixture = dslFixtureFor(scriptDsl)
        buildFileSeparatesImplementationAndApi(dslFixture, 'org.spockframework')

        when:
        run("build")

        then:
        assertTestPassed("some.thing.LibraryTest", "someLibraryMethod returns true")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates sample source using testng instead of junit with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-library', '--test-framework', 'testng', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        subprojectDir.file("src/test/java").assertHasDescendants(SAMPLE_LIBRARY_TEST_CLASS)

        and:
        commonJvmFilesGenerated(scriptDsl)
        def dslFixture = dslFixtureFor(scriptDsl)
        buildFileSeparatesImplementationAndApi(dslFixture, 'org.testng')

        when:
        run("build")

        then:
        assertTestPassed("some.thing.LibraryTest", "someLibraryMethodReturnsTrue")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates sample source using junit-jupiter instead of junit with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-library', '--test-framework', 'junit-jupiter', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        subprojectDir.file("src/test/java").assertHasDescendants(SAMPLE_LIBRARY_TEST_CLASS)

        and:
        commonJvmFilesGenerated(scriptDsl)
        def dslFixture = dslFixtureFor(scriptDsl)
        buildFileSeparatesImplementationAndApi(dslFixture, 'org.junit.jupiter')

        when:
        run("build")

        then:
        assertTestPassed("some.thing.LibraryTest", "someLibraryMethodReturnsTrue")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates sample source with package and #testFramework and #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-library', '--test-framework', testFramework.id, '--package', 'my.lib', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants("my/lib/Library.java")
        subprojectDir.file("src/test/java").assertHasDescendants("my/lib/LibraryTest.java")

        and:
        commonJvmFilesGenerated(scriptDsl as BuildInitDsl)

        when:
        run("build")

        then:
        assertTestPassed("my.lib.LibraryTest", "someLibraryMethodReturnsTrue")

        where:
        [scriptDsl, testFramework] << [
                ScriptDslFixture.SCRIPT_DSLS,
                [ BuildInitTestFramework.JUNIT, BuildInitTestFramework.TESTNG, BuildInitTestFramework.JUNIT_JUPITER ]
        ].combinations()
    }

    def "creates sample source with package and #testFramework and #scriptDsl build scripts with --incubating"() {
        def dslFixture = dslFixtureFor(scriptDsl)

        when:
        run('init', '--type', 'java-library', '--test-framework', testFramework.id, '--package', 'my.lib', '--dsl', scriptDsl.id, '--incubating')

        then:
        subprojectDir.file("src/main/java").assertHasDescendants("my/lib/Library.java")
        subprojectDir.file("src/test/java").assertHasDescendants("my/lib/LibraryTest.java")

        and:
        commonJvmFilesGenerated(scriptDsl as BuildInitDsl)
        dslFixture.assertHasTestSuite('test')

        when:
        run("build")

        then:
        assertTestPassed("my.lib.LibraryTest", "someLibraryMethodReturnsTrue")

        where:
        [scriptDsl, testFramework] << [
                ScriptDslFixture.SCRIPT_DSLS,
                [ BuildInitTestFramework.JUNIT, BuildInitTestFramework.TESTNG, BuildInitTestFramework.JUNIT_JUPITER ]
        ].combinations()
    }

    // Spock pulls an old version of Groovy that doesn't work with Java 18
    @Requires(UnitTestPreconditions.Jdk17OrEarlier)
    def "creates sample source with package and spock and #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-library', '--test-framework', 'spock', '--package', 'my.lib', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants("my/lib/Library.java")
        subprojectDir.file("src/test/groovy").assertHasDescendants("my/lib/LibraryTest.groovy")

        and:
        commonJvmFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("my.lib.LibraryTest", "someLibraryMethod returns true")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    // Spock pulls an old version of Groovy that doesn't work with Java 18
    @Requires(UnitTestPreconditions.Jdk17OrEarlier)
    def "creates sample source with package and spock and #scriptDsl build scripts with --incubating"() {
        def dslFixture = dslFixtureFor(scriptDsl)

        when:
        run('init', '--type', 'java-library', '--test-framework', 'spock', '--package', 'my.lib', '--dsl', scriptDsl.id, '--incubating')

        then:
        subprojectDir.file("src/main/java").assertHasDescendants("my/lib/Library.java")
        subprojectDir.file("src/test/groovy").assertHasDescendants("my/lib/LibraryTest.groovy")

        and:
        commonJvmFilesGenerated(scriptDsl)
        dslFixture.assertHasTestSuite('test')

        when:
        run("build")

        then:
        assertTestPassed("my.lib.LibraryTest", "someLibraryMethod returns true")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "source generation is skipped when java sources detected with #scriptDsl build scripts"() {
        setup:
        subprojectDir.file("src/main/java/org/acme/SampleMain.java") << """
        package org.acme;

        public class SampleMain {
        }
"""
        subprojectDir.file("src/test/java/org/acme/SampleMainTest.java") << """
                package org.acme;

                public class SampleMainTest {

                    @org.junit.jupiter.api.Test
                    public void sampleTest() { }
                }
        """
        when:
        run('init', '--type', 'java-library', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants("org/acme/SampleMain.java")
        subprojectDir.file("src/test/java").assertHasDescendants("org/acme/SampleMainTest.java")

        and:
        def dslFixture = dslFixtureFor(scriptDsl)
        dslFixture.assertGradleFilesGenerated()
        buildFileSeparatesImplementationAndApi(dslFixture)

        when:
        run("build")

        then:
        executed(":lib:test")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    private static void buildFileSeparatesImplementationAndApi(ScriptDslFixture dslFixture, String testFramework = 'org.junit.jupiter') {
        dslFixture.buildFile.assertContents(
            allOf(
                dslFixture.containsConfigurationDependencyNotation('api', 'org.apache.commons:commons-math3'),
                dslFixture.containsConfigurationDependencyNotation('implementation', 'com.google.guava:guava:'),
                dslFixture.containsConfigurationDependencyNotation('testImplementation', testFramework)))
    }
}
