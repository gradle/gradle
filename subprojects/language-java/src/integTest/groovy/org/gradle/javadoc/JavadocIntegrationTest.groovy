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
package org.gradle.javadoc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Unroll

class JavadocIntegrationTest extends AbstractIntegrationSpec {
    @Rule TestResources testResources = new TestResources(temporaryFolder)

    @Issue("GRADLE-1563")
    @Requires(TestPrecondition.JDK8_OR_EARLIER)  // JDK 9 requires an @Deprecated annotation that breaks this same test on Java 7 on Windows.
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
    @Requires(TestPrecondition.JDK9_OR_EARLIER)
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

        when: run("javadoc", "-i")
        then:
        file("build/docs/javadoc/Foo.html").text.contains("""Hey Joe!""")
    }

    @Unroll
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
        option << ['header', 'footer']
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

    @Requires([TestPrecondition.NOT_WINDOWS, TestPrecondition.JDK8_OR_EARLIER])  // JDK 9 Breaks multiline -header arguments.
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

        when: run("javadoc", "-i")
        then:
        file("build/docs/javadoc/Foo.html").text.contains("""Hey
Joe!""")
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

    @ToBeFixedForInstantExecution
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
        executedAndNotSkipped( ":javadoc")

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
        executer.expectDeprecationWarning() // Error output triggers are "deprecated" warning check
        fails("javadoc") // we're using unsupported options to verify that we do the right thing

        file("build/tmp/javadoc/javadoc.options").assertContents(containsNormalizedString("""-addMultilineStringsOption 'a'
-addMultilineStringsOption 'b'
-addMultilineStringsOption 'c'"""))

        file("build/tmp/javadoc/javadoc.options").assertContents(containsNormalizedString("""-addStringsOption 'a b c'"""))

        file("build/tmp/javadoc/javadoc.options").assertContents(containsNormalizedString("""-addMultilineMultiValueOption 
'a' 
-addMultilineMultiValueOption 
'b' 'c' """))
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

    private TestFile writeSourceFile() {
        file("src/main/java/Foo.java") << "public class Foo {}"
    }
}
