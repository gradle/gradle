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

class ScalaLibraryInitIntegrationTest extends AbstractJvmLibraryInitIntegrationSpec {

    public static final String SAMPLE_LIBRARY_CLASS = "some/thing/Library.scala"
    public static final String SAMPLE_LIBRARY_TEST_CLASS = "some/thing/LibrarySuite.scala"

    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'scala-library', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/scala").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        subprojectDir.file("src/test/scala").assertHasDescendants(SAMPLE_LIBRARY_TEST_CLASS)

        and:
        commonJvmFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("some.thing.LibrarySuite", "someLibraryMethod is always true")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates sample source with package and #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'scala-library', '--package', 'my.lib', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/scala").assertHasDescendants("my/lib/Library.scala")
        subprojectDir.file("src/test/scala").assertHasDescendants("my/lib/LibrarySuite.scala")

        and:
        commonJvmFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("my.lib.LibrarySuite", "someLibraryMethod is always true")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates build using test suites with #scriptDsl build scripts when using --incubating"() {
        when:
        run('init', '--type', 'scala-library', '--package', 'my.lib', '--dsl', scriptDsl.id, '--incubating')

        then:
        subprojectDir.file("src/main/scala").assertHasDescendants("my/lib/Library.scala")
        subprojectDir.file("src/test/scala").assertHasDescendants("my/lib/LibrarySuite.scala")

        and:
        commonJvmFilesGenerated(scriptDsl)
        dslFixtureFor(scriptDsl).assertHasTestSuite("test")

        when:
        run("build")

        then:
        assertTestPassed("my.lib.LibrarySuite", "someLibraryMethod is always true")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates with gradle.properties when using #scriptDsl build scripts with --incubating"() {
        when:
        run('init', '--type', 'scala-library', '--package', 'my.lib', '--dsl', scriptDsl.id, '--incubating')

        then:
        gradlePropertiesGenerated()

        when:
        run("build")

        then:
        assertTestPassed("my.lib.LibrarySuite", "someLibraryMethod is always true")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "source generation is skipped when scala sources detected with #scriptDsl build scripts"() {
        setup:
        subprojectDir.file("src/main/scala/org/acme/SampleMain.scala") << """
            package org.acme;

            class SampleMain{
            }
    """
        subprojectDir.file("src/test/scala/org/acme/SampleMainTest.scala") << """
                    package org.acme;

                    class SampleMainTest{

                        @org.junit.Test
                        def sampleTest : Unit = { }
                    }
            """

        when:
        run('init', '--type', 'scala-library', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/scala").assertHasDescendants("org/acme/SampleMain.scala")
        subprojectDir.file("src/test/scala").assertHasDescendants("org/acme/SampleMainTest.scala")
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        when:
        run("build")

        then:
        executed(":lib:test")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }
}
