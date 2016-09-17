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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DeprecationHandlingIntegrationTest extends AbstractIntegrationSpec {
    public static final String PLUGIN_DEPRECATION_MESSAGE = 'The DeprecatedPlugin plugin has been deprecated'

    def setup() {
        file('buildSrc/src/main/java/DeprecatedTask.java') << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.util.DeprecationLogger;

            public class DeprecatedTask extends DefaultTask {
                @TaskAction
                void causeDeprecationWarning() {
                    DeprecationLogger.nagUserOfReplacedTask("deprecated", "foobar");
                    System.out.println("DeprecatedTask.causeDeprecationWarning() executed.");
                }

                public static void someFeature() {
                    DeprecationLogger.nagUserOfDiscontinuedMethod("someFeature()");
                    System.out.println("DeprecatedTask.someFeature() executed.");
                }

                void otherFeature() {
                    DeprecationLogger.nagUserOfDiscontinuedMethod("otherFeature()", "Relax. This is just a test.");
                    System.out.println("DeprecatedTask.otherFeature() executed.");
                }

            }
        """.stripIndent()
        file('buildSrc/src/main/java/DeprecatedPlugin.java') << """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.util.DeprecationLogger;

            public class DeprecatedPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    DeprecationLogger.nagUserOfPluginReplacedWithExternalOne("DeprecatedPlugin", "Foobar");
                    project.getTasks().create("deprecated", DeprecatedTask.class);
                }
            }
        """.stripIndent()
    }

    def 'DeprecatedPlugin and DeprecatedTask - without full stacktrace.'() {
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
        executer.withFullDeprecationStackTraceDisabled()
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        run('deprecated', 'broken')

        then:
        output.contains('build.gradle:2)')
        output.contains('build.gradle:4)')
        output.contains('build.gradle:9)')
        !output.contains('(Native Method)')

        and:
        output.count(PLUGIN_DEPRECATION_MESSAGE) == 1
        output.count('The someFeature() method has been deprecated') == 1
        output.count('The otherFeature() method has been deprecated') == 1
        output.count('The deprecated task has been deprecated') == 1

        and:
        output.count('\tat') == 3
    }

    def 'DeprecatedPlugin and DeprecatedTask - with full stacktrace.'() {
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
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        executer.expectDeprecationWarning()
        run('deprecated', 'broken')

        then:
        output.contains('build.gradle:2)')
        output.contains('build.gradle:4)')
        output.contains('build.gradle:9)')

        and:
        output.count(PLUGIN_DEPRECATION_MESSAGE) == 1
        output.count('The someFeature() method has been deprecated') == 1
        output.count('The otherFeature() method has been deprecated') == 1
        output.count('The deprecated task has been deprecated') == 1

        and:
        output.count('\tat') > 3
    }

    def 'DeprecatedPlugin from init script - without full stacktrace.'() {
        given:
        def initScript = file("init.gradle") << """
            allprojects {
                DeprecationLogger.nagUserOfPluginReplacedWithExternalOne("DeprecatedPlugin", "Foobar") // line 2
            }
        """.stripIndent()

        when:
        executer.withFullDeprecationStackTraceDisabled()
        executer.expectDeprecationWarning()
        executer.usingInitScript(initScript)
        run '-s'

        then:
        output.contains('init.gradle:3)')

        output.count(PLUGIN_DEPRECATION_MESSAGE) == 1

        output.count('\tat') == 1
    }

    def 'DeprecatedPlugin from applied script - without full stacktrace.'() {
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
        executer.withFullDeprecationStackTraceDisabled()
        executer.expectDeprecationWarning()
        run()

        then:
        output.contains('project.gradle:2)')

        output.count(PLUGIN_DEPRECATION_MESSAGE) == 1

        output.count('\tat') == 1
    }

    def 'DeprecatedPlugin from applied script - with full stacktrace.'() {
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
        executer.expectDeprecationWarning()
        run()

        then:
        output.contains('project.gradle:2)')
        output.contains('build.gradle:2)')

        output.count(PLUGIN_DEPRECATION_MESSAGE) == 1

        output.count('\tat') > 1
    }
}
