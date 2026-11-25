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

package org.gradle.api.tasks.javadoc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavadocLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile """
            apply plugin: 'java'
        """
        file("src/main/java/org/example/Main.java") << """
            package org.example;
            /** Main class. */
            public class Main {}
        """
    }

    def "a build logic class creates the options through the object factory and copies them"() {
        file("buildSrc/src/main/java/org/example/OptionsTask.java") << """
            package org.example;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.file.RegularFileProperty;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.tasks.Nested;
            import org.gradle.api.tasks.OutputFile;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.external.javadoc.StandardJavadocDocletOptions;

            import javax.inject.Inject;
            import java.io.IOException;
            import java.nio.file.Files;

            public abstract class OptionsTask extends DefaultTask {
                private final StandardJavadocDocletOptions options;

                @Inject
                public OptionsTask(ObjectFactory objects) {
                    options = objects.newInstance(StandardJavadocDocletOptions.class);
                }

                @Inject
                protected abstract ObjectFactory getObjects();

                @Nested
                public StandardJavadocDocletOptions getOptions() {
                    return options;
                }

                @OutputFile
                public abstract RegularFileProperty getOutputFile();

                @TaskAction
                public void run() throws IOException {
                    StandardJavadocDocletOptions copy = getObjects().newInstance(StandardJavadocDocletOptions.class).copy(options);
                    copy.windowTitle("changed by the copy");
                    Files.write(getOutputFile().get().getAsFile().toPath(), (options.getWindowTitle() + "," + copy.getLinks()).getBytes());
                }
            }
        """
        buildFile """
            def scriptOptions = objects.newInstance(StandardJavadocDocletOptions)
            println("default outputLevel: " + scriptOptions.outputLevel)
            scriptOptions.outputLevel = null
            println("after null: " + scriptOptions.outputLevel)

            tasks.register("optionsTask", org.example.OptionsTask) {
                options.windowTitle = providers.gradleProperty("title").get()
                options.links("a", "b")
                outputFile = layout.buildDirectory.file("options.txt")
            }
        """

        when:
        succeeds("optionsTask", "-Ptitle=one")

        then:
        outputContains("default outputLevel: QUIET")
        outputContains("after null: null")
        result.assertTaskExecuted(":optionsTask")
        file("build/options.txt").text == "one,[a, b]"

        when:
        succeeds("optionsTask", "-Ptitle=one")

        then:
        result.assertTaskSkipped(":optionsTask")

        when:
        succeeds("optionsTask", "-Ptitle=two")

        then:
        result.assertTaskExecuted(":optionsTask")
        file("build/options.txt").text == "two,[a, b]"
    }

    def "outputLevel of the task options is QUIET by default and null after setting null"() {
        buildFile """
            def options = tasks.javadoc.options
            println("default: " + options.outputLevel)
            options.outputLevel = null
            println("after null: " + options.outputLevel)
            println("lazy present: " + options.lazyProperties.outputLevel.present)
        """

        when:
        succeeds("javadoc")

        then:
        outputContains("default: QUIET")
        outputContains("after null: null")
        outputContains("lazy present: false")
        !file("build/tmp/javadoc/javadoc.options").text.contains("-quiet")
    }

    def "fluent chains on the task options are visible through the lazy properties"() {
        buildFile """
            tasks.javadoc.options.windowTitle("x").links("a", "b")
            tasks.javadoc.options.author(true)
            def lazy = tasks.javadoc.options.lazyProperties
            println("lazy: " + [lazy.windowTitle.get(), lazy.links.get(), lazy.author.get()])
        """

        when:
        succeeds("help")

        then:
        outputContains("lazy: [x, [a, b], true]")
    }

    def "options bound to a mapped task output run that task first and reach the options file"() {
        buildFile """
            abstract class Producer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = "from producer"
                }
            }
            def producer = tasks.register("producer", Producer) {
                outputFile = layout.buildDirectory.file("title.txt")
            }
            tasks.javadoc {
                options.lazyProperties.windowTitle = producer.flatMap { it.outputFile }.map { it.asFile.text }
                options.lazyProperties.tags.add(producer.flatMap { it.outputFile }.map { "producer:a:" + it.asFile.text })
                options.jFlags("-Dorg.example.flag=1")
            }
        """

        when:
        succeeds("javadoc", "--info")

        then:
        result.assertTaskOrder(":producer", ":javadoc")
        def optionsFile = file("build/tmp/javadoc/javadoc.options").text
        optionsFile.contains("-windowtitle 'from producer'")
        optionsFile.contains("-tag 'producer:a:from producer'")
        outputContains("-J-Dorg.example.flag=1")
    }

    def "options changed at execution time through the lazy properties or the eager setter reach the options file"() {
        buildFile """
            tasks.javadoc {
                options.windowTitle = "configured"
                doFirst {
                    options.lazyProperties.windowTitle = "from lazy"
                    options.header = "from eager"
                }
            }
        """

        when:
        succeeds("javadoc")

        then:
        def optionsFile = file("build/tmp/javadoc/javadoc.options").text
        optionsFile.contains("-windowtitle 'from lazy'")
        optionsFile.contains("-header 'from eager'")
    }

    def "task properties keep their defaults and convention mappings"() {
        buildFile """
            tasks.register("plainJavadoc", Javadoc)
            def plain = tasks.plainJavadoc
            println("plain classpath: " + (plain.classpath == null ? "null" : plain.classpath.files))
            println("plain failOnError: " + plain.failOnError)
            println("mapped title: " + tasks.javadoc.title)
            tasks.javadoc.title = "explicit"
            println("explicit title: " + tasks.javadoc.title)
            tasks.javadoc.title = null
            println("null title: " + tasks.javadoc.title)
        """

        when:
        succeeds("help")

        then:
        outputContains("plain classpath: []")
        outputContains("plain failOnError: true")
        outputContains("mapped title: ${testDirectory.name} API")
        outputContains("explicit title: explicit")
        outputContains("null title: null")
    }

    def "the lazy properties accessor adds no input property"() {
        buildFile """
            tasks.javadoc {
                doLast {
                    println("inputs: " + inputs.properties.keySet().sort())
                }
            }
        """

        when:
        succeeds("javadoc")

        then:
        outputContains("inputs: [executable, failOnError, javadocTool, javadocTool.metadata, javadocTool.metadata.languageVersion, javadocTool.metadata.taskInputs, javadocTool.metadata.taskInputs.implementation, javadocTool.metadata.taskInputs.languageVersion, javadocTool.metadata.taskInputs.vendor, modularity, modularity.inferModulePath, options, options.JFlags, options.author, options.bottom, options.breakIterator, options.charSet, options.docEncoding, options.docFilesSubDirs, options.docTitle, options.doclet, options.encoding, options.excludeDocFilesSubDir, options.extraOptions, options.footer, options.groups, options.header, options.keyWords, options.linkSource, options.links, options.linksOffline, options.locale, options.memberLevel, options.noComment, options.noDeprecated, options.noDeprecatedList, options.noHelp, options.noIndex, options.noNavBar, options.noQualifiers, options.noSince, options.noTimestamp, options.noTree, options.overview, options.serialWarn, options.source, options.splitIndex, options.taglets, options.tags, options.use, options.version, options.windowTitle, title]")
    }
}
