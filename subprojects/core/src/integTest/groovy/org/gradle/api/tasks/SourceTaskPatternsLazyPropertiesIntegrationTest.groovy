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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Simulates a work layer that binds and updates the patterns of a {@link SourceTask} through
 * {@code lazyProperties.sourcePatterns} only.
 */
class SourceTaskPatternsLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src/one.a").text = "1"
        file("src/two.a").text = "2"
        file("src/three.b").text = "3"
        file("src/skip.b").text = "4"
        buildFile """
            import org.gradle.api.tasks.util.FilePatterns

            abstract class ListSources extends SourceTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    def names = source.files.collect { it.name }.sort()
                    println("sources: " + names)
                    outputFile.get().asFile.text = names.join(",")
                }
            }

            def list = tasks.register("list", ListSources) {
                source("src")
                outputFile = layout.buildDirectory.file("list.txt")
            }
        """
    }

    def "eager patterns are stored in the lazy property"() {
        buildFile """
            list.configure {
                include("**/*.a", "**/*.b")
                exclude("**/skip.b")
                println("includes: " + includes)
                println("excludes: " + excludes)
                println("lazy includes: " + lazyProperties.sourcePatterns.get().includes)
                println("lazy excludes: " + lazyProperties.sourcePatterns.get().excludes)
            }
        """

        when:
        succeeds("list")

        then:
        outputContains("includes: [**/*.a, **/*.b]")
        outputContains("excludes: [**/skip.b]")
        outputContains("lazy includes: [**/*.a, **/*.b]")
        outputContains("lazy excludes: [**/skip.b]")
        outputContains("sources: [one.a, three.b, two.a]")
    }

    def "binding replaces the patterns and specs set eagerly"() {
        buildFile """
            list.configure {
                include("**/*.a")
                exclude { it.name == "skip.b" }
                lazyProperties.sourcePatterns.set(FilePatterns.empty().include("**/*.b"))
                println("includes: " + includes)
                println("excludes: " + excludes)
                println("exclude specs: " + patternSet.excludeSpecs.size())
            }
        """

        when:
        succeeds("list")

        then:
        outputContains("includes: [**/*.b]")
        outputContains("excludes: []")
        outputContains("exclude specs: 0")
        outputContains("sources: [skip.b, three.b]")
    }

    def "an eager exclude after a lazy binding adds to it without evaluating the binding"() {
        buildFile """
            list.configure {
                lazyProperties.sourcePatterns.set(providers.provider {
                    println("evaluating bound patterns")
                    FilePatterns.empty().include("**/*.b")
                })
                exclude("**/skip.b")
                include("**/one.a")
            }
            gradle.taskGraph.whenReady { println("task graph ready") }
        """

        when:
        succeeds("list")

        then:
        output.indexOf("evaluating bound patterns") > output.indexOf("task graph ready")
        outputContains("sources: [one.a, three.b]")
    }

    def "a value can be updated with replace, whether it is plain or bound lazily"() {
        buildFile """
            list.configure {
                include("**/*.a")
                lazyProperties.sourcePatterns.replace { it.map { p -> p.exclude("**/two.a") } }
                println("plain: includes " + includes + " excludes " + excludes)
            }
            tasks.register("lazy", ListSources) {
                source("src")
                outputFile = layout.buildDirectory.file("lazy.txt")
                lazyProperties.sourcePatterns.set(providers.provider {
                    println("evaluating bound patterns")
                    FilePatterns.empty().include("**/*.b")
                })
                lazyProperties.sourcePatterns.replace { it.map { p -> p.exclude("**/skip.b") } }
            }
            gradle.taskGraph.whenReady { println("task graph ready") }
        """

        when:
        succeeds("list", "lazy")

        then:
        outputContains("plain: includes [**/*.a] excludes [**/two.a]")
        outputContains("sources: [one.a]")
        outputContains("sources: [three.b]")
        output.indexOf("evaluating bound patterns") > output.indexOf("task graph ready")
    }

    def "patterns bound to the output of another task schedule that task and apply when the task runs"() {
        buildFile """
            abstract class WritePattern extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = "**/*.b"
                }
            }
            def producer = tasks.register("producer", WritePattern) {
                outputFile = layout.buildDirectory.file("pattern.txt")
            }
            list.configure {
                exclude("**/skip.b")
                lazyProperties.sourcePatterns.set(producer.flatMap { it.outputFile }.map { f ->
                    FilePatterns.empty().include(f.asFile.text.trim())
                })
                exclude("**/three.b")
                include("**/one.a")
            }
        """

        when:
        succeeds("list")

        then:
        executedAndNotSkipped(":producer", ":list")
        outputContains("sources: [one.a, skip.b]")

        when:
        file("build").deleteDir()
        succeeds("list")

        then:
        executedAndNotSkipped(":producer", ":list")
        outputContains("sources: [one.a, skip.b]")
    }

    def "changing the bound patterns makes the task out of date"() {
        buildFile """
            list.configure {
                lazyProperties.sourcePatterns.set(providers.gradleProperty("pattern").map { FilePatterns.empty().include(it) })
            }
        """

        when:
        succeeds("list", "-Ppattern=**/*.a")

        then:
        executedAndNotSkipped(":list")
        outputContains("sources: [one.a, two.a]")

        when:
        succeeds("list", "-Ppattern=**/*.a")

        then:
        skipped(":list")

        when:
        succeeds("list", "-Ppattern=**/*.b")

        then:
        executedAndNotSkipped(":list")
        outputContains("sources: [skip.b, three.b]")
    }

    def "an eager change keeps the content of the convention"() {
        buildFile """
            list.configure {
                lazyProperties.sourcePatterns.convention(FilePatterns.empty().include("**/*.a"))
                exclude("**/two.a")
                println("includes: " + includes + " excludes: " + excludes)
            }
            tasks.register("lazy", ListSources) {
                source("src")
                outputFile = layout.buildDirectory.file("lazy.txt")
                lazyProperties.sourcePatterns.convention(providers.provider {
                    println("evaluating convention")
                    FilePatterns.empty().include("**/*.b")
                })
                exclude("**/skip.b")
            }
            gradle.taskGraph.whenReady { println("task graph ready") }
        """

        when:
        succeeds("list", "lazy")

        then:
        outputContains("includes: [**/*.a] excludes: [**/two.a]")
        outputContains("sources: [one.a]")
        outputContains("sources: [three.b]")
        output.indexOf("evaluating convention") > output.indexOf("task graph ready")
    }

    def "the getters return live views"() {
        buildFile """
            list.configure {
                includes.add("**/*.a")
                includes << "**/*.b"
                println("contains: " + includes.contains("**/*.b"))
                println("added again: " + includes.add("**/*.b"))
                println("removed: " + includes.remove("**/*.b"))
                println("iterated: " + includes.collect { it })
                println("equals plain set: " + (includes == ["**/*.a"] as Set) + " " + (["**/*.a"] as Set).equals(includes))
                excludes = ["**/two.a", "**/three.b"]
                excludes.remove("**/three.b")
                println("excludes: " + excludes)
                excludes.clear()
                excludes.add("**/one.a")
                println("lazy: " + lazyProperties.sourcePatterns.get().includes + " " + lazyProperties.sourcePatterns.get().excludes)
            }
        """

        when:
        succeeds("list")

        then:
        outputContains("contains: true")
        outputContains("added again: false")
        outputContains("removed: true")
        outputContains("iterated: [**/*.a]")
        outputContains("equals plain set: true true")
        outputContains("excludes: [**/two.a]")
        outputContains("lazy: [**/*.a] [**/one.a]")
        outputContains("sources: [two.a]")
    }

    def "a view of a lazily bound value changes it without evaluating it"() {
        buildFile """
            list.configure {
                lazyProperties.sourcePatterns.set(providers.provider {
                    println("evaluating bound patterns")
                    FilePatterns.empty().include("**/*.b")
                })
                println("added: " + excludes.add("**/skip.b"))
                includes << "**/one.a"
            }
            gradle.taskGraph.whenReady { println("task graph ready") }
        """

        when:
        succeeds("list")

        then:
        outputContains("added: true")
        output.indexOf("evaluating bound patterns") > output.indexOf("task graph ready")
        outputContains("sources: [one.a, three.b]")
    }

    def "an exclude in doFirst applies to the source the action reads"() {
        buildFile """
            list.configure {
                include("**/*.a")
                doFirst { exclude("**/two.a") }
            }
        """

        when:
        succeeds("list")

        then:
        outputContains("sources: [one.a]")
    }

    def "a subclass can use the pattern set as a PatternSet"() {
        buildFile """
            abstract class PatternSetUser extends ListSources {
                @Internal
                abstract DirectoryProperty getSrcDir()

                @Inject
                abstract ObjectFactory getObjects()

                @TaskAction
                void usePatternSet() {
                    PatternSet patterns = (PatternSet) getPatternSet()
                    def matched = []
                    objects.fileTree().from(srcDir).visit { e -> if (!e.directory && patterns.asSpec.isSatisfiedBy(e)) { matched << e.name } }
                    println("spec matches: " + matched.sort())
                    println("copy: " + new PatternSet().copyFrom(patterns).includes)
                    println("equals copy: " + patterns.equals(new PatternSet().copyFrom(patterns)))
                    println("empty: " + patterns.isEmpty())
                }
            }
            tasks.register("user", PatternSetUser) {
                source("src")
                srcDir = layout.projectDirectory.dir("src")
                outputFile = layout.buildDirectory.file("user.txt")
                lazyProperties.sourcePatterns.set(providers.provider { FilePatterns.empty().include("**/*.b") })
                exclude("**/skip.b")
            }
            tasks.register("copyInto", ListSources) {
                source("src")
                outputFile = layout.buildDirectory.file("copyInto.txt")
                lazyProperties.sourcePatterns.set(providers.provider { FilePatterns.empty().include("**/*.b") })
                patternSet.copyFrom(new PatternSet().include("**/one.a"))
                println("copied into: " + lazyProperties.sourcePatterns.get().includes)
            }
        """

        when:
        succeeds("user", "copyInto")

        then:
        outputContains("spec matches: [three.b]")
        outputContains("copy: [**/*.b]")
        outputContains("equals copy: true")
        outputContains("empty: false")
        outputContains("copied into: [**/one.a]")
        outputContains("sources: [one.a]")
    }

    def "specs set eagerly survive the configuration cache"() {
        buildFile """
            list.configure {
                exclude { it.name.startsWith("t") }
                lazyProperties.sourcePatterns.replace { it.map { p -> p.exclude { e -> e.name == "skip.b" } } }
            }
        """

        when:
        succeeds("list")

        then:
        outputContains("sources: [one.a]")

        when:
        file("build").deleteDir()
        succeeds("list")

        then:
        outputContains("sources: [one.a]")
    }
}
