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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaCompileForkOptionsLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile """
            include("processor")
        """
        buildFile """
            apply plugin: 'java'

            dependencies {
                annotationProcessor(project(":processor"))
            }

            abstract class Producer extends DefaultTask {
                @Input
                abstract Property<String> getContent()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = content.get()
                }
            }
        """
        file("processor/build.gradle") << "apply plugin: 'java'"
        // Writes the system property 'marker' of the compiler process to a resource, so a test can see the JVM arguments of the forked compiler
        file("processor/src/main/java/marker/MarkerProcessor.java") << """
            package marker;

            import javax.annotation.processing.AbstractProcessor;
            import javax.annotation.processing.RoundEnvironment;
            import javax.annotation.processing.SupportedAnnotationTypes;
            import javax.lang.model.SourceVersion;
            import javax.lang.model.element.TypeElement;
            import javax.tools.StandardLocation;
            import java.io.IOException;
            import java.io.Writer;
            import java.util.Set;

            @SupportedAnnotationTypes("*")
            public class MarkerProcessor extends AbstractProcessor {
                private boolean written;

                @Override
                public SourceVersion getSupportedSourceVersion() {
                    return SourceVersion.latestSupported();
                }

                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                    if (!written) {
                        written = true;
                        try (Writer writer = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "marker.txt").openWriter()) {
                            writer.write(String.valueOf(System.getProperty("marker")));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return false;
                }
            }
        """
        file("processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor") << "marker.MarkerProcessor"
        file("src/main/java/Main.java") << """
            public class Main {
                int next(int value) {
                    int result = value + 1;
                    return result;
                }
            }
        """
    }

    def "eager getters and setters behave as before"() {
        buildFile """
            def fork = tasks.compileJava.options.forkOptions
            def debug = tasks.compileJava.options.debugOptions
            println("defaults: " + [fork.executable, fork.tempDir, fork.javaHome, fork.memoryInitialSize, fork.memoryMaximumSize, fork.jvmArgs, debug.debugLevel])

            fork.executable = "custom-javac"
            fork.tempDir = "custom-tmp"
            fork.memoryInitialSize = "64m"
            fork.memoryMaximumSize = "256m"
            debug.debugLevel = "source,lines"
            println("values: " + [fork.executable, fork.tempDir, fork.memoryInitialSize, fork.memoryMaximumSize, debug.debugLevel])

            fork.javaHome = new File("relative", "jdk")
            println("javaHome as set: " + (fork.javaHome == new File("relative", "jdk")) + ", absolute: " + fork.javaHome.absolute)

            fork.jvmArgs = ["-Da=1", "", "  ", null, "-Db=2"]
            println("filtered: " + fork.jvmArgs)
            fork.jvmArgs.add("-Dc=3")
            fork.jvmArgs << "-Dd=4"
            println("live: " + fork.jvmArgs)
            println("all: " + fork.allJvmArgs)
            fork.jvmArgs = fork.jvmArgs
            println("set to itself: " + fork.jvmArgs)

            fork.jvmArgs = null
            fork.executable = null
            fork.javaHome = null
            debug.debugLevel = null
            println("nulls: " + [fork.jvmArgs, fork.executable, fork.javaHome, debug.debugLevel])
        """

        when:
        succeeds("help")

        then:
        outputContains("defaults: [null, null, null, null, null, [], null]")
        outputContains("values: [custom-javac, custom-tmp, 64m, 256m, source,lines]")
        outputContains("javaHome as set: true, absolute: false")
        outputContains("filtered: [-Da=1, -Db=2]")
        outputContains("live: [-Da=1, -Db=2, -Dc=3, -Dd=4]")
        outputContains("all: [-Da=1, -Db=2, -Dc=3, -Dd=4]")
        outputContains("set to itself: [-Da=1, -Db=2, -Dc=3, -Dd=4]")
        outputContains("nulls: [null, null, null, null]")
    }

    def "eager values are visible through the lazy properties"() {
        buildFile """
            def options = tasks.compileJava.options
            options.forkOptions.jvmArgs = ["-Da=1", ""]
            options.forkOptions.executable = "custom-javac"
            options.forkOptions.javaHome = new File("relative")
            options.debugOptions.debugLevel = "lines"
            def lazy = options.lazyProperties
            println("lazy: " + [lazy.forkOptions.jvmArgs.get(), lazy.forkOptions.executable.get(), lazy.forkOptions.javaHome.get() == new File("relative"), lazy.debugOptions.debugLevel.get()])
            options.forkOptions.jvmArgs = null
            println("null jvmArgs present: " + lazy.forkOptions.jvmArgs.present)
        """

        when:
        succeeds("help")

        then:
        outputContains("lazy: [[-Da=1], custom-javac, true, lines]")
        outputContains("null jvmArgs present: false")
    }

    def "lazy values reach the forked compiler and read back through the eager getters"() {
        buildFile """
            tasks.compileJava {
                options.fork = true
                options.lazyProperties.forkOptions.jvmArgs.add("-Dmarker=lazy")
                options.lazyProperties.forkOptions.memoryMaximumSize = "256m"
                options.lazyProperties.debugOptions.debugLevel = "none"
                doLast {
                    println("eager: " + [options.forkOptions.jvmArgs, options.forkOptions.memoryMaximumSize, options.debugOptions.debugLevel])
                }
            }
        """

        when:
        succeeds("compileJava")

        then:
        outputContains("eager: [[-Dmarker=lazy], 256m, none]")
        file("build/classes/java/main/marker.txt").text == "lazy"
        !hasLineNumbers(file("build/classes/java/main/Main.class"))
    }

    def "eager values reach the forked compiler"() {
        buildFile """
            tasks.compileJava {
                options.fork = true
                options.forkOptions.jvmArgs = ["-Dmarker=eager", " "]
                options.forkOptions.memoryMaximumSize = "256m"
                options.debugOptions.debugLevel = "source,lines,vars"
            }
        """

        when:
        succeeds("compileJava")

        then:
        file("build/classes/java/main/marker.txt").text == "eager"
        hasLineNumbers(file("build/classes/java/main/Main.class"))
    }

    def "an eager change after a lazy binding: #description"() {
        buildFile """
            def options = tasks.compileJava.options
            def lazy = options.lazyProperties
            def suffix = "1"
            $configuration
            suffix = "2"
            println("result: " + $read)
            println("lazy: " + $readLazy)
        """

        when:
        succeeds("help")

        then:
        outputContains("result: $expected")
        outputContains("lazy: $expected")

        where:
        description                       | configuration                                                                                                    | read                                    | readLazy                                       | expected
        "jvmArgs add keeps the binding"   | 'lazy.forkOptions.jvmArgs.set(provider { ["-Dm=" + suffix] }); options.forkOptions.jvmArgs.add("-De=eager")'     | 'options.forkOptions.jvmArgs'           | 'lazy.forkOptions.jvmArgs.get()'               | "[-Dm=2, -De=eager]"
        "jvmArgs set replaces it"         | 'lazy.forkOptions.jvmArgs.set(provider { ["-Dm=" + suffix] }); options.forkOptions.jvmArgs = ["-De=eager", ""]' | 'options.forkOptions.jvmArgs'           | 'lazy.forkOptions.jvmArgs.get()'               | "[-De=eager]"
        "jvmArgs null hides it"           | 'lazy.forkOptions.jvmArgs.set(provider { ["-Dm=" + suffix] }); options.forkOptions.jvmArgs = null'              | 'options.forkOptions.jvmArgs'           | 'lazy.forkOptions.jvmArgs.getOrNull()'         | "null"
        "executable set replaces it"      | 'lazy.forkOptions.executable.set(provider { "javac" + suffix }); options.forkOptions.executable = "eager"'       | 'options.forkOptions.executable'        | 'lazy.forkOptions.executable.get()'            | "eager"
        "memoryMaximumSize set replaces"  | 'lazy.forkOptions.memoryMaximumSize.set(provider { suffix + "m" }); options.forkOptions.memoryMaximumSize = "9m"' | 'options.forkOptions.memoryMaximumSize' | 'lazy.forkOptions.memoryMaximumSize.get()'     | "9m"
        "debugLevel null hides it"        | 'lazy.debugOptions.debugLevel.set(provider { "lines" + suffix }); options.debugOptions.debugLevel = null'       | 'options.debugOptions.debugLevel'       | 'lazy.debugOptions.debugLevel.getOrNull()'     | "null"
        "binding without change is live"  | 'lazy.debugOptions.debugLevel.set(provider { "lines" + suffix })'                                                 | 'options.debugOptions.debugLevel'       | 'lazy.debugOptions.debugLevel.get()'           | "lines2"
    }

    def "a lazy binding after an eager change: #description"() {
        buildFile """
            def options = tasks.compileJava.options
            def lazy = options.lazyProperties
            def suffix = "1"
            $configuration
            suffix = "2"
            println("result: " + $read)
        """

        when:
        succeeds("help")

        then:
        outputContains("result: $expected")

        where:
        description                     | configuration                                                                                                 | read                                    | expected
        "jvmArgs set"                   | 'options.forkOptions.jvmArgs = ["-De=eager"]; lazy.forkOptions.jvmArgs.set(provider { ["-Dm=" + suffix] })'   | 'options.forkOptions.jvmArgs'           | "[-Dm=2]"
        "jvmArgs add"                   | 'options.forkOptions.jvmArgs = ["-De=eager"]; lazy.forkOptions.jvmArgs.add(provider { "-Dm=" + suffix })'     | 'options.forkOptions.jvmArgs'           | "[-De=eager, -Dm=2]"
        "jvmArgs after null"            | 'options.forkOptions.jvmArgs = null; lazy.forkOptions.jvmArgs.set(provider { ["-Dm=" + suffix] })'            | 'options.forkOptions.jvmArgs'           | "[-Dm=2]"
        "javaHome"                      | 'options.forkOptions.javaHome = file("eager"); lazy.forkOptions.javaHome.set(provider { new File("lazy") })'  | 'options.forkOptions.javaHome.path'     | "lazy"
        "tempDir"                       | 'options.forkOptions.tempDir = "eager"; lazy.forkOptions.tempDir.set(provider { "tmp" + suffix })'            | 'options.forkOptions.tempDir'           | "tmp2"
        "debugLevel"                    | 'options.debugOptions.debugLevel = "vars"; lazy.debugOptions.debugLevel.set(provider { "lines" + suffix })'  | 'options.debugOptions.debugLevel'       | "lines2"
        "debugLevel convention is kept" | 'lazy.debugOptions.debugLevel.convention("lines"); options.debugOptions.debugLevel = "vars"; lazy.debugOptions.debugLevel.unset()' | 'options.debugOptions.debugLevel' | "lines"
    }

    def "lazy jvmArgs bound to a task output run that task first and reach the forked compiler"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = "-Dmarker=produced"
                outputFile = layout.buildDirectory.file("jvm-arg.txt")
            }
            tasks.compileJava {
                options.fork = true
                options.lazyProperties.forkOptions.jvmArgs.add(producer.flatMap { it.outputFile }.map { it.asFile.text })
            }
        """

        when:
        succeeds("compileJava")

        then:
        result.assertTaskOrder(":producer", ":compileJava")
        file("build/classes/java/main/marker.txt").text == "produced"

        when:
        succeeds("compileJava")

        then:
        result.assertTaskSkipped(":compileJava")
    }

    def "lazy debugLevel bound to a task output runs that task first"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = "none"
                outputFile = layout.buildDirectory.file("debug-level.txt")
            }
            tasks.compileJava {
                options.lazyProperties.debugOptions.debugLevel = producer.flatMap { it.outputFile }.map { it.asFile.text }
            }
        """

        when:
        succeeds("compileJava")

        then:
        result.assertTaskOrder(":producer", ":compileJava")
        !hasLineNumbers(file("build/classes/java/main/Main.class"))
    }

    def "lazy executable bound to a task output runs that task first"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = "custom-javac"
                outputFile = layout.buildDirectory.file("executable.txt")
            }
            tasks.compileJava {
                // Without fork the executable is not used, so the value does not need to be a real compiler
                options.lazyProperties.forkOptions.executable = producer.flatMap { it.outputFile }.map { it.asFile.text }
                doLast {
                    println("executable: " + options.forkOptions.executable)
                }
            }
        """

        when:
        succeeds("compileJava")

        then:
        result.assertTaskOrder(":producer", ":compileJava")
        outputContains("executable: custom-javac")
    }

    def "input names do not change"() {
        buildFile """
            tasks.compileJava {
                doLast {
                    println("inputs: " + inputs.properties.keySet().findAll { it.contains("forkOptions") || it.contains("debugOptions") || it.contains("lazy") }.sort())
                }
            }
        """

        when:
        succeeds("compileJava")

        then:
        outputContains("inputs: [options.debugOptions, options.debugOptions.debugLevel, options.forkOptions, options.forkOptions.executable, options.forkOptions.jvmArgs]")
    }

    def "options created with new store their values and can be serialized"() {
        buildFile """
            def roundTrip(Object value) {
                def bytes = new ByteArrayOutputStream()
                new ObjectOutputStream(bytes).withCloseable { it.writeObject(value) }
                def input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
                    @Override
                    protected Class<?> resolveClass(ObjectStreamClass description) {
                        return Class.forName(description.name, false, BaseForkOptions.classLoader)
                    }
                }
                return input.withCloseable { it.readObject() }
            }

            def base = new BaseForkOptions()
            println("base defaults: " + [base.memoryInitialSize, base.memoryMaximumSize, base.jvmArgs])
            base.memoryMaximumSize = "256m"
            base.jvmArgs = ["-Da=1", "", null]
            base.jvmArgs.add("-Db=2")
            def baseCopy = roundTrip(base)
            println("base copy: " + [baseCopy.memoryMaximumSize, baseCopy.jvmArgs])

            def debug = new DebugOptions()
            debug.debugLevel = "lines"
            println("debug copy: " + roundTrip(debug).debugLevel)

            def fork = objects.newInstance(ForkOptions)
            fork.executable = "custom-javac"
            fork.jvmArgs = ["-Dc=3"]
            println("fork: " + [fork.executable, fork.jvmArgs])
        """

        when:
        succeeds("help")

        then:
        outputContains("base defaults: [null, null, []]")
        outputContains("base copy: [256m, [-Da=1, -Db=2]]")
        outputContains("debug copy: lines")
        outputContains("fork: [custom-javac, [-Dc=3]]")
    }

    def "a task with options of its own uses them as nested inputs"() {
        buildFile """
            abstract class CustomTask extends DefaultTask {
                @Nested
                final DebugOptions debug = new DebugOptions()

                @Nested
                abstract ForkOptions getFork()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = debug.debugLevel + " " + fork.jvmArgs
                }
            }

            tasks.register("custom", CustomTask) {
                debug.debugLevel = providers.gradleProperty("level").get()
                fork.jvmArgs = ["-Da=1"]
                outputFile = layout.buildDirectory.file("custom.txt")
            }
        """

        when:
        succeeds("custom", "-Plevel=lines")

        then:
        file("build/custom.txt").text == "lines [-Da=1]"

        when:
        succeeds("custom", "-Plevel=lines")

        then:
        result.assertTaskSkipped(":custom")

        when:
        succeeds("custom", "-Plevel=vars")

        then:
        result.assertTaskExecuted(":custom")
        file("build/custom.txt").text == "vars [-Da=1]"
    }

    def "groovy compile with fork options still works"() {
        buildFile """
            apply plugin: 'groovy'

            dependencies {
                implementation(localGroovy())
            }

            tasks.compileGroovy {
                groovyOptions.fork = true
                groovyOptions.forkOptions.memoryMaximumSize = "256m"
                groovyOptions.forkOptions.jvmArgs = ["-Dgroovy.marker=1", ""]
                options.forkOptions.memoryMaximumSize = "128m"
                doLast {
                    println("groovy fork: " + [groovyOptions.forkOptions.memoryMaximumSize, groovyOptions.forkOptions.jvmArgs])
                }
            }
        """
        file("src/main/groovy/Hello.groovy") << "class Hello { String greet() { 'hello' } }"

        when:
        succeeds("compileGroovy")

        then:
        outputContains("groovy fork: [256m, [-Dgroovy.marker=1]]")
        file("build/classes/groovy/main/Hello.class").exists()

        when:
        succeeds("compileGroovy")

        then:
        result.assertTaskSkipped(":compileGroovy")
    }

    private static boolean hasLineNumbers(File classFile) {
        return new String(classFile.bytes, "ISO-8859-1").contains("LineNumberTable")
    }
}
