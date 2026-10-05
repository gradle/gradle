/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.docs

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

class GradleSnippetsTestingPluginTest extends Specification {
    @TempDir
    File projectDir

    File snippets
    File installed

    def setup() {
        snippets = new File(projectDir, "src/snippets")
        installed = new File(projectDir, "build/working/samples/testing")

        new File(projectDir, "settings.gradle") << "rootProject.name = 'docs'\n"
        new File(projectDir, "build.gradle") << """
            plugins {
                id 'gradlebuild.docs-snippets-testing'
            }
        """.stripIndent()

        // A snippet for both DSLs, with shared files, its own tests, but no sanity check
        file("tasks/hello/kotlin/build.gradle.kts") << 'tasks.register("hello")\n'
        file("tasks/hello/groovy/build.gradle") << "tasks.register('hello')\n"
        file("tasks/hello/common/gradle.properties") << "org.gradle.caching=true\n"
        file("tasks/hello/tests/hello.sample.conf") << "executable: gradle\nargs: hello\n"
        file("tasks/hello/tests/hello.out") << "BUILD SUCCESSFUL\n"
        file("tasks/hello/tests-groovy/groovyOnly.sample.conf") << "executable: gradle\nargs: hello\n"
        // Never installed
        file("tasks/hello/groovy/README") << "readme\n"
        file("tasks/hello/groovy/build/leftover.txt") << "stale\n"
        file("tasks/hello/groovy/.gradle/leftover.txt") << "stale\n"
        file("tasks/hello/kotlin/gradle/wrapper/gradle-wrapper.properties") << "distributionUrl=stale\n"

        // A Kotlin-only snippet with its own sanity check, under a camelCase directory
        file("java/multiProject/kotlin/settings.gradle.kts") << 'include("app")\n'
        file("java/multiProject/tests-common/sanityCheck.sample.conf") << "executable: gradle\nargs: help\n"

        // An acronym in the directory name, which is split as one word
        file("reference/constraintsFromBOM/kotlin/build.gradle.kts") << "\n"

        // A snippet named after an untested top-level directory, which is still a snippet
        file("optimizing/integration-tests/kotlin/build.gradle.kts") << "\n"

        // Directories that are not snippets
        file("unused/old/kotlin/build.gradle.kts") << "\n"
        file("integration-tests/groovy/build.gradle") << "\n"
        file("notes/readme.txt") << "not a snippet\n"
    }

    def "installs every snippet once per DSL"() {
        when:
        run("installSnippetsForTest")

        then:
        installed.list().sort() == [
            "snippet-java-multi-project",
            "snippet-optimizing-integration-tests",
            "snippet-reference-constraints-from-bom",
            "snippet-tasks-hello",
        ]
        new File(installed, "snippet-tasks-hello").list().sort() == ["groovy", "kotlin"]
        new File(installed, "snippet-java-multi-project").list() as List == ["kotlin"]
    }

    def "installs the DSL files, shared files, tests and wrapper scripts"() {
        when:
        run("installSnippetsForTest")

        then:
        def kotlin = new File(installed, "snippet-tasks-hello/kotlin")
        new File(kotlin, "build.gradle.kts").text == 'tasks.register("hello")\n'
        !new File(kotlin, "build.gradle").exists()
        new File(kotlin, "gradle.properties").exists()
        new File(kotlin, "hello.sample.conf").exists()
        new File(kotlin, "hello.out").exists()
        !new File(kotlin, "groovyOnly.sample.conf").exists()
        new File(kotlin, "gradlew").exists()
        new File(kotlin, "gradlew.bat").exists()

        def groovy = new File(installed, "snippet-tasks-hello/groovy")
        new File(groovy, "build.gradle").exists()
        !new File(groovy, "build.gradle.kts").exists()
        new File(groovy, "groovyOnly.sample.conf").exists()
    }

    def "leaves out READMEs, build outputs and wrapper files of the snippet"() {
        when:
        run("installSnippetsForTest")

        then:
        def groovy = new File(installed, "snippet-tasks-hello/groovy")
        !new File(groovy, "README").exists()
        !new File(groovy, "build").exists()
        !new File(groovy, ".gradle").exists()
        !new File(installed, "snippet-tasks-hello/kotlin/gradle/wrapper").exists()
    }

    def "adds a generated sanity check only to snippets without their own"() {
        when:
        run("installSnippetsForTest")

        then:
        ["kotlin", "groovy"].each { dsl ->
            assert new File(installed, "snippet-tasks-hello/$dsl/sanityCheck.sample.conf").text.contains("args: tasks -q")
        }
        new File(installed, "snippet-java-multi-project/kotlin/sanityCheck.sample.conf").text == "executable: gradle\nargs: help\n"
    }

    def "checkSamples and check run the snippet tests"() {
        when:
        def checkSamples = run("checkSamples", "--dry-run")
        def check = run("check", "--dry-run")

        then:
        checkSamples.output.contains(":installSnippetsForTest SKIPPED")
        checkSamples.output.contains(":docsTest SKIPPED")
        check.output.contains(":docsTest SKIPPED")
    }

    def "reuses the configuration cache and is up to date when nothing changed"() {
        when:
        run("installSnippetsForTest", "--configuration-cache")
        def second = run("installSnippetsForTest", "--configuration-cache")

        then:
        second.output.contains("Reusing configuration cache.")
        second.task(":installSnippetsForTest").outcome == TaskOutcome.UP_TO_DATE
    }

    def "picks up a new snippet"() {
        given:
        run("installSnippetsForTest", "--configuration-cache")

        when:
        file("tasks/goodbye/kotlin/build.gradle.kts") << 'tasks.register("goodbye")\n'
        def result = run("installSnippetsForTest", "--configuration-cache")

        then:
        !result.output.contains("Reusing configuration cache.")
        new File(installed, "snippet-tasks-goodbye/kotlin/build.gradle.kts").exists()
    }

    private File file(String path) {
        def file = new File(snippets, path)
        file.parentFile.mkdirs()
        return file
    }

    private BuildResult run(String... args) {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(args)
            .forwardOutput()
            .build()
    }
}
