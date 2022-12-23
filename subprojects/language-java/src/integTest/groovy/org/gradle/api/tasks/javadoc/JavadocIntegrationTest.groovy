/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.TestResources
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.condition.Requires
import org.gradle.test.fixtures.condition.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Issue

import java.nio.file.Paths

class JavadocIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    TestResources testResources = new TestResources(temporaryFolder)

    @Issue("GRADLE-1563")
    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    // JDK 9 requires an @Deprecated annotation that breaks this same test on Java 7 on Windows.
    def handlesTagsAndTaglets() {
        when:
        run("javadoc")

        then:
        def javadoc = testResources.dir.file("build/docs/javadoc/Person.html")
        javadoc.text =~ /(?ms)This is the Person class.*Author.*author value.*Deprecated.*deprecated value.*Custom Tag.*custom tag value/
        // we can't currently control the order between tags and taglets (limitation on our side)
        javadoc.text =~ /(?ms)Custom Taglet.*custom taglet value/
    }

    @Issue(["GRADLE-2520", "https://github.com/gradle/gradle/issues/4993"])
    @Requires(UnitTestPreconditions.Jdk9OrEarlier)
    def canCombineLocalOptionWithOtherOptions() {
        when:
        run("javadoc")

        then:
        def javadoc = testResources.dir.file("build/docs/javadoc/Person.html")
        javadoc.text =~ /(?ms)USED LOCALE=de_DE/
        javadoc.text =~ /(?ms)Serial no. is valid javadoc!/
    }

    def "writes header"() {
        buildFile << """
            apply plugin: "java"
            javadoc.options.header = "<!-- Hey Joe! -->"
        """

        writeSourceFile()

        when:
        run("javadoc", "-i")
        then:
        file("build/docs/javadoc/Foo.html").text.contains("""Hey Joe!""")
    }

    @Issue("gradle/gradle#1090")
    def "can use single quote character in #option"() {
        buildFile << """
            apply plugin: 'java'
            javadoc.options.$option = "'some text'"
        """

        file('src/main/java/Foo.java') << 'public class Foo {}'

        when:
        succeeds 'javadoc'

        then:
        file('build/docs/javadoc/Foo.html').text.contains("'some text'")

        where:
        option << (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_16) ? ['header'] : ['header', 'footer'])
    }

    def "can configure options with an Action"() {
        given:
        buildFile << '''
            apply plugin: "java"
            javadoc.options({ MinimalJavadocOptions options ->
                options.header = 'myHeader'
            } as Action<MinimalJavadocOptions>)
        '''.stripIndent()
        writeSourceFile()

        when:
        run 'javadoc'

        then:
        file('build/docs/javadoc/Foo.html').text.contains('myHeader')
    }

    @Requires(value = [
        UnitTestPreconditions.NotWindows,
        UnitTestPreconditions.Jdk8OrEarlier
    ], reason = "JDK 9 Breaks multiline -header arguments.")
    @Issue("GRADLE-3099")
    def "writes multiline header"() {
        buildFile << """
            apply plugin: "java"
            javadoc.options.header = \"\"\"
                <!-- Hey
Joe! -->
            \"\"\"
        """

        writeSourceFile()

        when:
        run("javadoc", "-i")
        then:
        file("build/docs/javadoc/Foo.html").text.contains("""Hey
Joe!""")
    }

    def "emits deprecation warning if executable specified as relative path"() {
        given:
        def executable = TextUtil.normaliseFileSeparators(Jvm.current().javadocExecutable.toString())

        buildFile << """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
                executable = new File(".").getAbsoluteFile().toPath().relativize(new File("${executable}").toPath()).toString()
            }
        """

        writeSourceFile()

        when:
        executer.expectDocumentedDeprecationWarning("Configuring a Java executable via a relative path. " +
                "This behavior has been deprecated. This will fail with an error in Gradle 9.0. " +
                "Resolving relative file paths might yield unexpected results, there is no single clear location it would make sense to resolve against. " +
                "Configure an absolute path to a Java executable instead. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#no_relative_paths_for_java_executables")
        run("javadoc")

        then:
        file("build/javadoc/Foo.html").exists()
    }

    @Issue("GRADLE-3152")
    def "can use the task without applying java-base plugin"() {
        buildFile << """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
            }
        """

        writeSourceFile()

        when:
        run("javadoc")

        then:
        file("build/javadoc/Foo.html").exists()
    }

    def "changing standard doclet options makes task out-of-date"() {
        buildFile << """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
                options {
                    windowTitle = "Window title"
                }
            }
        """

        writeSourceFile()

        when:
        run "javadoc"
        then:
        executedAndNotSkipped(":javadoc")

        when:
        run "javadoc"
        then:
        skipped(":javadoc")

        when:
        buildFile.text = """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
                options {
                    windowTitle = "Window title changed"
                }
            }
        """
        run "javadoc"

        then:
        executedAndNotSkipped(":javadoc")
    }

    @Issue("https://github.com/gradle/gradle/issues/1456")
    def "can use custom JavadocOptionFileOption type"() {
        buildFile << """
            apply plugin: 'java'
            import org.gradle.external.javadoc.internal.JavadocOptionFileWriterContext;

            class CustomJavadocOptionFileOption implements JavadocOptionFileOption<String> {
                private String value = "foo"

                public String getValue() {
                    return value
                }

                public void setValue(String value) {
                    this.value = value
                }

                public String getOption() {
                    return "exclude"
                }

                public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
                    writerContext.writeOptionHeader(getOption())
                    writerContext.writeValue(value)
                    writerContext.newLine()
                }
            }

            javadoc {
                options {
                    addOption(new CustomJavadocOptionFileOption())
                }
            }
        """
        writeSourceFile()
        expect:
        succeeds("javadoc")
        file("build/tmp/javadoc/javadoc.options").assertContents(containsNormalizedString("-exclude 'foo'"))
    }

    @Issue("https://github.com/gradle/gradle/issues/1484")
    def "can use various multi-value options"() {
        buildFile << """
            apply plugin: 'java'

            javadoc {
                options {
                    addMultilineStringsOption("addMultilineStringsOption").setValue([
                        "a",
                        "b",
                        "c"
                    ])
                    addStringsOption("addStringsOption", " ").setValue([
                        "a",
                        "b",
                        "c"
                    ])
                    addMultilineMultiValueOption("addMultilineMultiValueOption").setValue([
                        [ "a" ],
                        [ "b", "c" ]
                    ])
                }
            }
        """
        writeSourceFile()
        expect:
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
            executer.expectDeprecationWarnings(2)
        } else {
            executer.expectDeprecationWarning() // Error output triggers are "deprecated" warning check
        }
        fails("javadoc") // we're using unsupported options to verify that we do the right thing

        file("build/tmp/javadoc/javadoc.options").assertContents(containsNormalizedString("-addMultilineStringsOption 'a'\n" +
            "-addMultilineStringsOption 'b'\n" +
            "-addMultilineStringsOption 'c'"))

        file("build/tmp/javadoc/javadoc.options").assertContents(containsNormalizedString("""-addStringsOption 'a b c'"""))

        file("build/tmp/javadoc/javadoc.options").assertContents(containsNormalizedString("-addMultilineMultiValueOption \n" +
            "'a' \n" +
            "-addMultilineMultiValueOption \n" +
            "'b' 'c' "))
    }

    @Issue("https://github.com/gradle/gradle/issues/1502")
    def "can pass Jflags to javadoc"() {
        buildFile << """
            apply plugin: 'java'
            javadoc.options.JFlags = ["-Dpublic.api=com.sample.tools.VisibilityPublic"]
        """
        writeSourceFile()
        expect:
        succeeds("javadoc", "--info")
        outputContains("-J-Dpublic.api=com.sample.tools.VisibilityPublic")
    }

    @Issue("https://github.com/gradle/gradle/issues/2235")
    def "can pass offline links"() {
        buildFile << """
            apply plugin: 'java'

            javadoc {
                options {
                    linksOffline 'https://docs.oracle.com/javase/8/docs/api/', 'gradle/javadocs/jdk'
                    linksOffline 'http://javadox.com/org.jetbrains/annotations/15.0/', 'gradle/javadocs/jetbrains-annotations'
                }
            }
        """
        writeSourceFile()
        file('gradle/javadocs/jdk/package-list') << ''
        file('gradle/javadocs/jetbrains-annotations/package-list') << ''

        expect:
        succeeds("javadoc")
    }

    @Issue("https://github.com/gradle/gradle/issues/4841")
    def "adding custom javadoc options makes task out-of-date"() {
        given: "a javadoc task without custom options"
        buildFile << """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
                options {
                    author()
                }
            }
        """

        writeSourceFile()

        when: "running the task the first time, it is executed and succeeds"
        run "javadoc"
        then:
        executedAndNotSkipped(":javadoc")

        when: "running the task the second time after adding a custom option, it is executed and succeeds and NOT marked UP-TO-DATE"
        buildFile.text = """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
                options {
                    author()
                    addBooleanOption 'Xdoclint', true
                }
            }
        """
        run "javadoc"
        then:
        executedAndNotSkipped(":javadoc")
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    @Issue("https://github.com/gradle/gradle/issues/4841")
    def "adding custom javadoc options makes task out-of-date with html5 option"() {
        given: "a javadoc task without custom options"
        buildFile << """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
                options {
                    author()
                }
            }
        """

        writeSourceFile()

        when: "running the task the first time, it is executed and succeeds"
        run "javadoc"
        then:
        executedAndNotSkipped(":javadoc")

        when: "running the task the second time after adding a custom option, it is executed and succeeds and NOT marked UP-TO-DATE"
        buildFile.text = """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
                options {
                    author()
                    addBooleanOption 'html5', true
                }
            }
        """
        run "javadoc"
        then:
        executedAndNotSkipped(":javadoc")
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    @Issue("https://github.com/gradle/gradle/issues/4841")
    def "changing the value of a custom javadoc options makes task out-of-date"() {
        given: "a javadoc task with a custom options"
        buildFile << """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
                options {
                    author()
                    addBooleanOption 'html5', true
                }
            }
        """

        writeSourceFile()

        when: "running the task the first time, it is executed and succeeds"
        run "javadoc"
        then:
        executedAndNotSkipped(":javadoc")

        when: "running the task the second time after changing the custom option, it is executed and succeeds and NOT marked UP-TO-DATE"
        buildFile.text = """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
                options {
                    author()
                    addBooleanOption 'html5', false
                }
            }
        """
        run "javadoc"
        then:
        executedAndNotSkipped(":javadoc")
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    @Issue("https://github.com/gradle/gradle/issues/4841")
    def "changing which custom javadoc options are available makes task out-of-date"() {
        given: "a javadoc task with a custom options"
        buildFile << """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
                options {
                    author()
                    addBooleanOption 'html5', true
                }
            }
        """

        writeSourceFile()

        when: "running the task the first time, it is executed and succeeds"
        run "javadoc"
        then:
        executedAndNotSkipped(":javadoc")

        when: "running the task the second time after changing the custom option, it is executed and succeeds and NOT marked UP-TO-DATE"
        buildFile.text = """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
                options {
                    author()
                    addBooleanOption 'Xdoclint', true
                }
            }
        """
        run "javadoc"
        then:
        executedAndNotSkipped(":javadoc")
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    @Issue("https://github.com/gradle/gradle/issues/4841")
    def "unchanged custom javadoc option does not make task out-of-date"() {
        given: "a javadoc task with a custom options"
        buildFile << """
            task javadoc(type: Javadoc) {
                destinationDir = file("build/javadoc")
                source "src/main/java"
                options {
                    author()
                    addBooleanOption 'html5', true
                }
            }
        """

        writeSourceFile()

        when: "running the task the first time, it is executed and succeeds"
        run "javadoc"
        then:
        executedAndNotSkipped(":javadoc")

        when: "running the task the second time it is UP-TO-DATE"
        run "javadoc"
        then:
        skipped(":javadoc")
    }

    // bootclasspath has been removed in Java 9+
    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    @Issue("https://github.com/gradle/gradle/issues/19817")
    def "shows deprecation if bootclasspath is provided as a path instead of a single file"() {
        def jre = AvailableJavaHomes.getBestJre()
        def bootClasspath = TextUtil.escapeString(jre.absolutePath) + "/lib/rt.jar${File.pathSeparator}someotherpath"
        buildFile << """
            plugins {
                id 'java'
            }
            javadoc {
                options.bootClasspath = [file('$bootClasspath')]
            }
        """
        writeSourceFile()

        when:
        runAndFail "javadoc"
        then:
        failure.assertHasDocumentedCause("Converting files to a classpath string when their paths contain the path separator '${File.pathSeparator}' is not supported." +
            " The path separator is not a valid element of a file path. Problematic paths in 'file collection' are: '${Paths.get(bootClasspath)}'." +
            " Add the individual files to the file collection instead." +
            " Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#file_collection_to_classpath")
    }

    def "can use custom stylesheet file"() {
        buildFile << """
            plugins {
                id 'java'
            }
            javadoc {
                options.stylesheetFile = file('src/docs/custom.css')
            }
        """
        writeSourceFile()
        file("src/docs/custom.css") << """
            /* This is a custom stylesheet */
            h1 {
                color red
            }
        """

        when:
        succeeds "javadoc"
        then:
        file("build/docs/javadoc/custom.css").assertContents(containsNormalizedString("/* This is a custom stylesheet */"))

        when:
        succeeds("javadoc")
        then:
        skipped(":javadoc")

        when:
        file("src/docs/custom.css") << """
            a {
                color blue
            }
        """
        succeeds("javadoc")
        then:
        executed(":javadoc")
    }

    def "can use custom stylesheet file with a different name"() {
        buildFile << """
            plugins {
                id 'java'
            }
            javadoc {
                options.stylesheetFile = file('src/docs/custom.css')
            }
        """
        writeSourceFile()
        file("src/docs/custom.css") << """
            /* This is a custom stylesheet */
            h1 {
                color red
            }
        """

        when:
        succeeds "javadoc"
        then:
        file("build/docs/javadoc/custom.css").assertContents(containsNormalizedString("/* This is a custom stylesheet */"))

        when:
        succeeds("javadoc")
        then:
        skipped(":javadoc")

        when:
        file("src/docs/custom.css").moveToDirectory(file("src/not-docs"))
        buildFile << """
            javadoc {
                options.stylesheetFile = file('src/not-docs/custom.css')
            }
        """
        succeeds("javadoc")
        then:
        skipped(":javadoc")
    }

    def "can exclude a package by source path"() {
        buildFile << """
            plugins {
                id 'java'
            }
            javadoc {
                exclude("pkg/internal/**")
            }
        """
        file("src/main/java/pkg/Foo.java").java """
            package pkg;
            import pkg.internal.*;

            public class Foo implements IFoo {
                /**
                 * {@inheritDoc}
                 */
                public void run() {
                }
            }
        """
        file("src/main/java/pkg/internal/IFoo.java").java """
            package pkg.internal;

            public interface IFoo {
                /**
                 * Runs internal foo
                 */
                void run();
            }
        """
        when:
        succeeds("javadoc")
        then:
        file("build/docs/javadoc/pkg/Foo.html").assertExists()
        file("build/docs/javadoc/pkg/internal/IFoo.html").assertDoesNotExist()
    }

    private TestFile writeSourceFile() {
        file("src/main/java/Foo.java") << "public class Foo {}"
    }
}
