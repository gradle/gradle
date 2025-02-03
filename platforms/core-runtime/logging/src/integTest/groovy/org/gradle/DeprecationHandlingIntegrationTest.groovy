/*
 * Copyright 2016 the original author or authors.
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

package org.gradle

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler
import org.gradle.util.internal.DefaultGradleVersion

class DeprecationHandlingIntegrationTest extends AbstractIntegrationSpec {
    public static final String PLUGIN_DEPRECATION_MESSAGE = 'The DeprecatedPlugin plugin has been deprecated'
    private static final String RUN_WITH_STACKTRACE = "(Run with -D${LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME}=true to print the full stack trace for this deprecation warning.)"

    def setup() {
        file('buildSrc/src/main/java/DeprecatedTask.java') << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.internal.deprecation.DeprecationLogger;

            public class DeprecatedTask extends DefaultTask {
                @TaskAction
                void causeDeprecationWarning() {
                    DeprecationLogger.deprecateTask("thisIsADeprecatedTask").replaceWith("foobar").willBeRemovedInGradle9().undocumented().nagUser();
                    System.out.println("DeprecatedTask.causeDeprecationWarning() executed.");
                }

                public static void someFeature() {
                    DeprecationLogger.deprecateMethod(DeprecatedTask.class, "someFeature()").willBeRemovedInGradle9().undocumented().nagUser();
                    System.out.println("DeprecatedTask.someFeature() executed.");
                }

                void otherFeature() {
                    DeprecationLogger.deprecateMethod(DeprecatedTask.class, "otherFeature()").withAdvice("Relax. This is just a test.").willBeRemovedInGradle9().undocumented().nagUser();
                    System.out.println("DeprecatedTask.otherFeature() executed.");
                }

            }
        """.stripIndent()
        file('buildSrc/src/main/java/DeprecatedPlugin.java') << """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.internal.deprecation.DeprecationLogger;

            public class DeprecatedPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    DeprecationLogger.deprecatePlugin("DeprecatedPlugin").replaceWithExternalPlugin("Foobar").willBeRemovedInGradle9().undocumented().nagUser();
                    project.getTasks().create("thisIsADeprecatedTask", DeprecatedTask.class);
                }
            }
        """.stripIndent()
        file('buildSrc/src/main/resources/META-INF/gradle-plugins/org.acme.deprecated.properties') << """
            implementation-class=DeprecatedPlugin
        """.stripIndent()
    }

    def 'DeprecatedPlugin and DeprecatedTask - #scenario'() {
        given:
        buildFile << """
            apply plugin: DeprecatedPlugin // line 2

            DeprecatedTask.someFeature() // line 4
            DeprecatedTask.someFeature()

            task broken(type: DeprecatedTask) {
                doLast {
                    otherFeature() // line 9
                }
            }
        """.stripIndent()

        when:
        if (fullStacktraceEnabled) {
            executer.withFullDeprecationStackTraceEnabled()
        }
        if (warningsCount > 0) {
            executer.expectDeprecationWarnings(warningsCount)
        }
        executer.withWarningMode(warnings)
        warnings == WarningMode.Fail ? fails('thisIsADeprecatedTask', 'broken') : succeeds('thisIsADeprecatedTask', 'broken')

        then:
        output.contains('build.gradle:2)') == warningsCount > 0
        output.contains('build.gradle:4)') == warningsCount > 0
        output.contains('build.gradle:9)') == warningsCount > 0

        and:
        output.contains(PLUGIN_DEPRECATION_MESSAGE) == warningsCount > 0
        output.contains('The DeprecatedTask.someFeature() method has been deprecated') == warningsCount > 0
        output.contains('The DeprecatedTask.otherFeature() method has been deprecated') == warningsCount > 0
        output.contains('The thisIsADeprecatedTask task has been deprecated') == warningsCount > 0

        and:
        output.contains(LoggingDeprecatedFeatureHandler.WARNING_SUMMARY) == warningsSummary
        output.contains("You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.") == warningsSummary
        output.contains(documentationRegistry.getDocumentationRecommendationFor("on this", "command_line_interface", "sec:command_line_warnings")) == warningsSummary

        and: "system stack frames are filtered"
        !output.contains('jdk.internal.')
        !output.contains('sun.') || output.contains('sun.run')
        !output.contains('org.codehaus.groovy.')
        !output.contains('org.gradle.internal.metaobject.')
        !output.contains('org.gradle.kotlin.dsl.execution.')

        and:
        assertFullStacktraceResult(fullStacktraceEnabled, warningsCount)

        and:
        if (warnings == WarningMode.Fail) {
            failure.assertHasDescription("Deprecated Gradle features were used in this build, making it incompatible with ${DefaultGradleVersion.current().nextMajorVersion}")
        }

        where:
        scenario                                        | warnings            | warningsCount | warningsSummary | fullStacktraceEnabled
        'without stacktrace and --warning-mode=all'     | WarningMode.All     | 5             | false           | false
        'with stacktrace and --warning-mode=all'        | WarningMode.All     | 5             | false           | true
        'without stacktrace and --warning-mode=no'      | WarningMode.None    | 0             | false           | false
        'with stacktrace and --warning-mode=no'         | WarningMode.None    | 0             | false           | true
        'without stacktrace and --warning-mode=summary' | WarningMode.Summary | 0             | true            | false
        'with stacktrace and --warning-mode=summary'    | WarningMode.Summary | 0             | true            | true
        'without stacktrace and --warning-mode=fail'    | WarningMode.Fail    | 5             | false           | false
        'with stacktrace and --warning-mode=fail'       | WarningMode.Fail    | 5             | false           | true
    }

    def 'build error and deprecation failure combined'() {
        given:
        buildFile << """
            apply plugin: DeprecatedPlugin // line 2

            task broken() {
                doLast {
                    throw new IllegalStateException("Can't do that")
                }
            }
        """.stripIndent()

        when:
        executer.expectDeprecationWarning("The DeprecatedPlugin plugin has been deprecated. This is scheduled to be removed in Gradle 9.0. Consider using the Foobar plugin instead.")
        executer.withWarningMode(WarningMode.Fail)

        then:
        fails('broken')
        output.contains('build.gradle:2)')
        failure.assertHasCause("Can't do that")
        failure.assertHasDescription('Deprecated Gradle features were used in this build')
    }

    def 'DeprecatedPlugin from init script - without full stacktrace.'() {
        given:
        def initScript = file("init.gradle") << """
            allprojects {
                org.gradle.internal.deprecation.DeprecationLogger.deprecatePlugin("DeprecatedPlugin").replaceWithExternalPlugin("Foobar").willBeRemovedInGradle9().undocumented().nagUser() // line 2
            }
        """.stripIndent()

        when:
        executer.expectDeprecationWarnings(1)
        executer.usingInitScript(initScript)
        run '-s'

        then:
        output.contains("Initialization script '${initScript}': line 3")
        output.contains('init.gradle:3)')

        output.count(PLUGIN_DEPRECATION_MESSAGE) == 1

        output.count('\tat') == 1
        output.count(RUN_WITH_STACKTRACE) == 1
    }

    def 'DeprecatedPlugin from applied script - #scenario'() {
        given:
        file("project.gradle") << """
            apply plugin:  DeprecatedPlugin // line 2
        """.stripIndent()

        buildFile << """
            allprojects {
                apply from: 'project.gradle' // line 2
            }
        """.stripIndent()

        when:
        if (withFullStacktrace) {
            executer.withFullDeprecationStackTraceEnabled()
        }
        executer.expectDeprecationWarning()
        run()

        then:
        output.contains('project.gradle:2)')
        output.contains('build.gradle:2)') == withFullStacktrace
        output.count(PLUGIN_DEPRECATION_MESSAGE) == 1

        withFullStacktrace ? (output.count('\tat') > 1) : (output.count('\tat') == 1)
        withFullStacktrace == !output.contains(RUN_WITH_STACKTRACE)

        where:
        scenario                  | withFullStacktrace
        'without full stacktrace' | false
        'with full stacktrace'    | true
    }

    def 'DeprecatedPlugin from applied kotlin script - #scenario'() {
        given:
        file("project.gradle.kts") << """
           apply(plugin = "org.acme.deprecated") // line 2
        """.stripIndent()

        buildFile << """
            allprojects {
                apply from: 'project.gradle.kts' // line 3
            }
        """.stripIndent()

        when:
        if (withFullStacktrace) {
            executer.withFullDeprecationStackTraceEnabled()
        }
        executer.expectDeprecationWarning()
        run()

        then:
        output.contains('project.gradle.kts:2)')
        output.contains('build.gradle:3)') == withFullStacktrace
        output.contains('build.gradle:2)') == withFullStacktrace
        output.count(PLUGIN_DEPRECATION_MESSAGE) == 1

        withFullStacktrace ? (output.count('\tat') > 1) : (output.count('\tat') == 1)
        withFullStacktrace == !output.contains(RUN_WITH_STACKTRACE)

        where:
        scenario                  | withFullStacktrace
        'without full stacktrace' | false
        'with full stacktrace'    | true
    }

    def "reports line numbers for deprecations in builds scripts for buildSrc and included builds"() {
        settingsFile << """
            includeBuild("included")
        """
        buildFile << """
            task broken {
                doLast {
                    ${deprecatedMethodUsage()}
                }
            }
        """
        file("buildSrc/build.gradle") << """
            task broken {
                doLast {
                    ${deprecatedMethodUsage()}
                }
            }
        """
        file("included/build.gradle") << """
            task broken {
                doLast {
                    ${deprecatedMethodUsage()}
                }
            }
        """

        expect:
        2.times {
            executer.expectDeprecationWarning("The Task.someFeature() method has been deprecated. This is scheduled to be removed in Gradle 9.0.")
            executer.expectDeprecationWarning("The Task.someFeature() method has been deprecated. This is scheduled to be removed in Gradle 9.0.")
            executer.expectDeprecationWarningWithPattern(/The Task\.\w+\(\) method has been deprecated\. This is scheduled to be removed in Gradle 9\.0\./)
            run("broken", "buildSrc:broken", "included:broken")

            outputContains("Build file '${file("included/build.gradle")}': line 5")
            outputContains("Build file '${file("buildSrc/build.gradle")}': line 5")
            outputContains("Build file '${buildFile}': line 5")
        }
    }

    def "prints correct deprecation trace property value to use to display stack traces for deprecation warnings"() {
        disableProblemsApiCheck()

        given:
        buildFile << """
            def myConf = project.configurations.resolvable("myConf")
            def myDetached = project.configurations.detachedConfiguration()
            myDetached.extendsFrom(myConf.get())
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Calling extendsFrom on configuration ':detachedConfiguration1' has been deprecated. This will fail with an error in Gradle 9.0. Detached configurations should not extend other configurations, this was extending: 'myConf'. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#detached_configurations_cannot_extend")
        succeeds "tasks"

        and: "stack trace suggestion is printed"
        outputContains("\t(Run with -Dorg.gradle.deprecation.trace=true to print the full stack trace for this deprecation warning.)")

        and: "we can verify the stack trace is not printed"
        String notExpected = """run(${buildFile.absolutePath}:4)
\tat org.gradle.groovy.scripts.internal.DefaultScriptRunnerFactory\$ScriptRunnerImpl.run(DefaultScriptRunnerFactory.java"""
        result.assertNotOutput(notExpected)
    }

    def "does not print stack traces for deprecation warnings if --stacktrace is set"() {
        disableProblemsApiCheck()

        given:
        buildFile << """
            def myConf = project.configurations.resolvable("myConf")
            def myDetached = project.configurations.detachedConfiguration()
            myDetached.extendsFrom(myConf.get())
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Calling extendsFrom on configuration ':detachedConfiguration1' has been deprecated. This will fail with an error in Gradle 9.0. Detached configurations should not extend other configurations, this was extending: 'myConf'. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#detached_configurations_cannot_extend")
        succeeds "tasks", "--stacktrace"

        and: "stack trace suggestion is printed"
        outputContains("\t(Run with -Dorg.gradle.deprecation.trace=true to print the full stack trace for this deprecation warning.)")

        and: "we can verify the stack trace is not printed"
        String notExpected = """run($buildFile.absolutePath}:4)
\tat org.gradle.groovy.scripts.internal.DefaultScriptRunnerFactory\$ScriptRunnerImpl.run(DefaultScriptRunnerFactory.java"""
        result.assertNotOutput(notExpected)
    }

    def "prints stack traces for deprecation warnings if deprecation trace property value is set"() {
        disableProblemsApiCheck()

        given:
        buildFile << """
            def myConf = project.configurations.resolvable("myConf")
            def myDetached = project.configurations.detachedConfiguration()
            myDetached.extendsFrom(myConf.get())
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Calling extendsFrom on configuration ':detachedConfiguration1' has been deprecated. This will fail with an error in Gradle 9.0. Detached configurations should not extend other configurations, this was extending: 'myConf'. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#detached_configurations_cannot_extend")
        succeeds "tasks", "-Dorg.gradle.deprecation.trace=true"

        and: "stack trace suggestion is not printed"
        result.assertNotOutput("\t(Run with -Dorg.gradle.deprecation.trace=true to print the full stack trace for this deprecation warning.)")

        and: "we can verify the part of the stack trace that should be unchanged is printed"
        String expected = """run(${buildFile.absolutePath}:4)
\tat org.gradle.groovy.scripts.internal.DefaultScriptRunnerFactory\$ScriptRunnerImpl.run(DefaultScriptRunnerFactory.java"""
        outputContains(expected)
    }

    String deprecatedMethodUsage() {
        return """
            ${DeprecationLogger.name}.deprecateMethod(Task.class, "someFeature()").willBeRemovedInGradle9().undocumented().nagUser();
        """
    }

    void assertFullStacktraceResult(boolean fullStacktraceEnabled, int warningsCount) {
        if (warningsCount == 0) {
            assert output.count('\tat') == 0 && output.count(RUN_WITH_STACKTRACE) == 0
        } else if (fullStacktraceEnabled) {
            assert output.count('\tat') > 4 && output.count(RUN_WITH_STACKTRACE) == 0
        } else {
            assert output.count('\tat') == 4 && output.count(RUN_WITH_STACKTRACE) == 4
        }
    }
}
