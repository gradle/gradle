/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.test.fixtures.file.LeaksFileHandles

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN

@LeaksFileHandles
class KotlinLibraryInitIntegrationTest extends AbstractJvmLibraryInitIntegrationSpec {

    public static final String SAMPLE_LIBRARY_CLASS = "some/thing/Library.kt"
    public static final String SAMPLE_LIBRARY_TEST_CLASS = "some/thing/LibraryTest.kt"

    def "defaults to kotlin build scripts"() {
        when:
        run ('init', '--type', 'kotlin-library')

        then:
        dslFixtureFor(KOTLIN).assertGradleFilesGenerated()
    }

    def "creates sample source if no source present with #scriptDsl build scripts"() {
        def dslFixture = dslFixtureFor(scriptDsl)

        when:
        run('init', '--type', 'kotlin-library', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/kotlin").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        subprojectDir.file("src/test/kotlin").assertHasDescendants(SAMPLE_LIBRARY_TEST_CLASS)

        and:
        commonJvmFilesGenerated(scriptDsl)
        dslFixture.assertDoesNotUseTestSuites()

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
        run ('init', '--type', 'kotlin-library', '--dsl', scriptDsl.id, '--incubating')

        then:
        subprojectDir.file("src/main/kotlin").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        subprojectDir.file("src/test/kotlin").assertHasDescendants(SAMPLE_LIBRARY_TEST_CLASS)

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
        run ('init', '--type', 'kotlin-library', '--dsl', scriptDsl.id, '--incubating')

        then:
        gradlePropertiesGenerated()

        when:
        succeeds('test')

        then:
        assertTestPassed("some.thing.LibraryTest", "someLibraryMethodReturnsTrue")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates sample source with package and #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'kotlin-library', '--package', 'my.lib', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/kotlin").assertHasDescendants("my/lib/Library.kt")
        subprojectDir.file("src/test/kotlin").assertHasDescendants("my/lib/LibraryTest.kt")

        and:
        commonJvmFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("my.lib.LibraryTest", "someLibraryMethodReturnsTrue")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "source generation is skipped when kotlin sources detected with #scriptDsl build scripts"() {
        setup:
        subprojectDir.file("src/main/kotlin/org/acme/SampleMain.kt") << """
            package org.acme

            class SampleMain {
            }
    """
        subprojectDir.file("src/test/kotlin/org/acme/SampleMainTest.kt") << """
                    package org.acme

                    class SampleMainTest {
                    }
            """
        when:
        run('init', '--type', 'kotlin-library', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/kotlin").assertHasDescendants("org/acme/SampleMain.kt")
        subprojectDir.file("src/test/kotlin").assertHasDescendants("org/acme/SampleMainTest.kt")
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        when:
        run("build")

        then:
        executed(":lib:test")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }
}
