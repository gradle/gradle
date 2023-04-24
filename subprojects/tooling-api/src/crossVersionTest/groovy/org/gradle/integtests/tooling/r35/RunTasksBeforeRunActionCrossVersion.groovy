/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r35


import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.UnsupportedVersionException

import java.util.regex.Pattern

@ToolingApiVersion('>=3.5')
@TargetGradleVersion('>=3.5')
class RunTasksBeforeRunActionCrossVersion extends ToolingApiSpecification {
    def setup() {
        buildFile << """
            task hello {
                doLast {
                    println "hello"
                }
            }

            task bye(dependsOn: hello) {
                doLast {
                    println "bye"
                }
            }
        """
    }

    def "can run tasks"() {
        when:
        def stdOut = new ByteArrayOutputStream()
        withConnection {
            it.action(new SimpleAction())
                .forTasks("hello", "bye")
                .setStandardOutput(stdOut)
                .run()
        }

        then:
        assert stdOut.toString().contains("hello")
        assert stdOut.toString().contains("bye")
    }

    def "tasks are run before action is executed"() {
        when:
        def stdOut = new ByteArrayOutputStream()
        def result = withConnection {
            connection -> connection.action(new SimpleAction()).forTasks("bye").setStandardOutput(stdOut).run()
        }

        then:
        Pattern regex = Pattern.compile(".*hello.*bye.*starting action.*", Pattern.DOTALL)
        assert stdOut.toString().matches(regex)
        assert result == "Action result"
    }

    def "does not run any tasks when none specified and #description"() {
        file('build.gradle') << """
            $configuration

            gradle.taskGraph.whenReady {
                throw new RuntimeException()
            }
        """

        when:
        withConnection {
            it.action(new SimpleAction()).run()
        }

        then:
        noExceptionThrown()
        assertHasConfigureSuccessfulLogging()

        where:
        description                                        | configuration
        "build logic does not define any additional tasks" | ""
        "build logic defines default tasks"                | "defaultTasks = ['broken']"
        "build logic injects tasks into start param"       | "gradle.startParameter.taskNames = ['broken']"
    }

    // older versions do not run any tasks
    @ToolingApiVersion('>4.7')
    @TargetGradleVersion('>=4.7')
    def "empty array of task names means run help task"() {
        file('build.gradle') << """
        """

        when:
        withConnection {
            it.action(new SimpleAction())
                .forTasks()
                .run()
        }

        then:
        assertHasBuildSuccessfulLogging()
        result.assertTasksExecuted(":help")
    }

    // older versions do not run any tasks
    @ToolingApiVersion('>4.7')
    @TargetGradleVersion('>=4.7')
    def "empty array of task names means run default tasks when they are defined"() {
        file('build.gradle') << """
            defaultTasks = ["thing"]

            task thing { }
        """

        when:
        withConnection {
            it.action(new SimpleAction())
                .forTasks()
                .run()
        }

        then:
        assertHasBuildSuccessfulLogging()
        result.assertTasksExecuted(":thing")
    }

    // older versions do not run any tasks
    @ToolingApiVersion('>4.7')
    @TargetGradleVersion('>=4.7')
    def "empty list of task names means run help task"() {
        file('build.gradle') << """
        """

        when:
        withConnection {
            it.action(new SimpleAction())
                .forTasks([])
                .run()
        }

        then:
        assertHasBuildSuccessfulLogging()
        result.assertTasksExecuted(":help")
    }

    // older versions do not run any tasks
    @ToolingApiVersion('>4.7')
    @TargetGradleVersion('>=4.7')
    def "empty list of task names means run default tasks when they are defined"() {
        file('build.gradle') << """
            defaultTasks = ["thing"]

            task thing { }
        """

        when:
        withConnection {
            it.action(new SimpleAction())
                .forTasks([])
                .run()
        }

        then:
        assertHasBuildSuccessfulLogging()
        result.assertTasksExecuted(":thing")
    }

    // older versions do not run any tasks
    @ToolingApiVersion('>4.7')
    @TargetGradleVersion('>=4.7')
    def "empty array of task names means run tasks injected by build logic"() {
        file('build.gradle') << """
            gradle.startParameter.taskNames = ["thing"]

            task thing { }
        """

        when:
        withConnection {
            it.action(new SimpleAction())
                .forTasks()
                .run()
        }

        then:
        assertHasBuildSuccessfulLogging()
        result.assertTasksExecuted(":thing")
    }

    // older versions do not run any tasks
    @ToolingApiVersion('>4.7')
    @TargetGradleVersion('>=4.7')
    def "empty list of task names means run tasks injected by build logic"() {
        file('build.gradle') << """
            gradle.startParameter.taskNames = ["thing"]

            task thing { }
        """

        when:
        withConnection {
            it.action(new SimpleAction())
                .forTasks([])
                .run()
        }

        then:
        assertHasBuildSuccessfulLogging()
        result.assertTasksExecuted(":thing")
    }

    @TargetGradleVersion(">=2.6 <3.5")
    def "BuildExecuter.forTasks() should fail when it is not supported by target"() {
        when:
        withConnection {
            it.action(new SimpleAction())
                .forTasks("hello")
                .run()
        }

        then:
        UnsupportedVersionException e = thrown()
        assert e.message == "The version of Gradle you are using (${targetDist.version.version}) does not support the forTasks() method on BuildActionExecuter. Support for this is available in Gradle 3.5 and all later versions."
    }

    @TargetGradleVersion(">=2.6 <3.5")
    def "BuildExecuter.forTasks() notifies failure to handler when it is not supported by target"() {
        def handler = Mock(ResultHandler)
        def version = targetDist.version.version

        when:
        withConnection {
            it.action(new SimpleAction())
                .forTasks("hello")
                .run(handler)
        }

        then:
        0 * handler.onComplete(_)
        1 * handler.onFailure(_) >> { args ->
            GradleConnectionException failure = args[0]
            assert failure instanceof UnsupportedVersionException
            assert failure.message == "The version of Gradle you are using (${version}) does not support the forTasks() method on BuildActionExecuter. Support for this is available in Gradle 3.5 and all later versions."
        }
    }
}
