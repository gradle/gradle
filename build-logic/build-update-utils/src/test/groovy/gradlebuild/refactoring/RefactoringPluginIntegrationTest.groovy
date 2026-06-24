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

package gradlebuild.refactoring

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Exercises the `analyzeClassDependencies` task as configured by the `gradlebuild.refactoring` plugin:
 * the build only applies the plugin and runs the task via its options, so the source/classpath wiring
 * is provided by the plugin rather than by the test.
 */
class RefactoringPluginIntegrationTest extends Specification {

    @TempDir
    File projectDir

    File outputFile

    def setup() {
        outputFile = new File(projectDir, "build/analysis.json")

        file("settings.gradle") << """
            rootProject.name = "analyze-under-test"
        """

        // The plugin wires `groovySourceDirectories` from the main source set's GroovySourceDirectorySet,
        // so the `groovy` plugin (which also brings `java`) must be applied.
        file("build.gradle") << """
            plugins {
                id("groovy")
                id("gradlebuild.refactoring")
            }
        """

        // A project class (Foo) that references another project class (Bar),
        // so the analysis must emit a project-kind dependency edge Foo -> Bar.
        javaSource("com/example/Foo.java") << """
            package com.example;

            public class Foo {
                private Bar bar;

                public Bar getBar() {
                    return bar;
                }
            }
        """
        javaSource("com/example/Bar.java") << """
            package com.example;

            public class Bar {
            }
        """
    }

    def "produces the expected dependency analysis json"() {
        when:
        def result = run()

        then:
        result.task(":analyzeClassDependencies").outcome.name() == "SUCCESS"
        assertExpectedJson()
    }

    def "is compatible with the configuration cache"() {
        when:
        def result = run("--configuration-cache")

        then:
        result.task(":analyzeClassDependencies").outcome.name() == "SUCCESS"
        assertExpectedJson()
    }

    private void assertExpectedJson() {
        assert outputFile.exists()
        def json = new JsonSlurper().parse(outputFile)

        assert json.projectPath == ":"
        assert json.roots == ["com.example.Foo"]
        assert json.subprojectDependencies == []

        def classNames = json.projectClasses.collect { it.fqcn }
        assert classNames == ["com.example.Bar", "com.example.Foo"]

        def foo = json.projectClasses.find { it.fqcn == "com.example.Foo" }
        assert foo.sourceFile == "src/main/java/com/example/Foo.java"
        def fooToBar = foo.dependencies.find { it.fqcn == "com.example.Bar" }
        assert fooToBar != null
        assert fooToBar.kind == "project"
        // project-kind dependencies omit the `subproject` field entirely
        assert !fooToBar.containsKey("subproject")

        def bar = json.projectClasses.find { it.fqcn == "com.example.Bar" }
        assert bar.sourceFile == "src/main/java/com/example/Bar.java"
        // Bar does not reference Foo, so there must be no Bar -> Foo edge
        assert bar.dependencies.every { it.fqcn != "com.example.Foo" }
    }

    private BuildResult run(String... extraArgs) {
        def args = [
            "analyzeClassDependencies",
            "--class", "com.example.Foo",
            "--output", outputFile.absolutePath,
            "-s"
        ] + (extraArgs as List)
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(args)
            .build()
    }

    private File file(String path) {
        def target = new File(projectDir, path)
        target.parentFile.mkdirs()
        target
    }

    private File javaSource(String path) {
        file("src/main/java/$path")
    }
}
