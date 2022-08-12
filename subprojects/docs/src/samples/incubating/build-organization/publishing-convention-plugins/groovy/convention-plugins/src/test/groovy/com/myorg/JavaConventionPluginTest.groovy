package com.myorg

import org.gradle.testkit.runner.TaskOutcome

class JavaConventionPluginTest extends PluginTest {

    def setup() {
        buildFile << """
            plugins {
                id 'com.myorg.java-conventions'
            }
        """
    }

    def "fails on checkstyle error"() {
        given:
        new File(testProjectDir, 'src/main/java/com/myorg').mkdirs()
        new File(testProjectDir, 'src/main/java/com/myorg/Foo.java') << """
            package com.myorg;

            import java.util.*;

            class Foo {
                void bar() {
                }
            }
        """

        when:
        def result = runTaskWithFailure('build')

        then:
        result.task(":checkstyleMain").outcome == TaskOutcome.FAILED
        result.output.contains('Checkstyle rule violations were found.')
        result.output.contains('Checkstyle violations by severity: [error:1]')
    }

    def "fails on checkstyle warning"() {
        given:
        new File(testProjectDir, 'src/main/java/com/myorg').mkdirs()
        new File(testProjectDir, 'src/main/java/com/myorg/Foo.java') << """
            package com.myorg;

            class Foo {
                final static public String FOO = "BAR";

                void bar() {
                }
            }
        """

        when:
        def result = runTaskWithFailure('build')

        then:
        result.task(":checkstyleMain").outcome == TaskOutcome.FAILED
        result.output.contains('Checkstyle rule violations were found.')
        result.output.contains('Checkstyle violations by severity: [warning:1]')
    }

    def "fails on spotbugs error"() {
        given:
        new File(testProjectDir, 'src/main/java/com/myorg').mkdirs()
        new File(testProjectDir, 'src/main/java/com/myorg/Foo.java') << """
            package com.myorg;

            class Foo {
                void bar() {
                    String s = null;
                    s.hashCode();
                }
            }
        """

        when:
        def result = runTaskWithFailure('build')

        then:
        result.task(":spotbugsMain").outcome == TaskOutcome.FAILED
    }

    def "warns on deprecated API usage"() {
        given:
        new File(testProjectDir, 'src/main/java/com/myorg').mkdirs()
        new File(testProjectDir, 'src/main/java/com/myorg/Foo.java') << """
            package com.myorg;

            public class Foo {
                @Deprecated
                public void deprecatedMethod() {}
            }
        """

        new File(testProjectDir, 'src/main/java/com/myorg/Bar.java') << """
            package com.myorg;

            public class Bar {
                public void bar() {
                    new Foo().deprecatedMethod();
                }
            }
        """

        when:
        def result = runTask('build')

        then:
        result.task(":build").outcome == TaskOutcome.SUCCESS
        result.output.contains('warning: [deprecation] deprecatedMethod()')
    }
}
