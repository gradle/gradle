/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.launcher.cli

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.isConfigCache() || GradleContextualExecuter.isParallel() }) // --parallel or configuration-cache options unavailable
class TaskOptionsSpec extends AbstractIntegrationSpec {

    def defineTaskWithProfileOption() {
        return """
                abstract class MyTask extends DefaultTask {
                    @Option(option="profile", description="foobar")
                    @Optional
                    @Input
                    abstract Property<String> getProfile()

                    @TaskAction
                    void run() {
                        if (getProfile().isPresent()) {
                            logger.lifecycle(getName() + "profile=" + getProfile().get())
                        }
                    }
                }
                """
    }

    def "will prioritize built-in option over a task option with a conflicting name"() {
        when:
        buildScript """
            ${defineTaskWithProfileOption()}

            tasks.register('mytask', MyTask.class)
        """

        then:
        succeeds "mytask", "--profile"
        output.contains "See the profiling report at"
        !output.contains("profile=myvalue")
    }

    def "can use -- to specify a task option with same name as a built-in option"() {
        when:
        buildScript """
            ${defineTaskWithProfileOption()}

            tasks.register('mytask', MyTask.class)
        """

        then:
        succeeds "--", "mytask", "--profile", "myvalue"
        output.contains "profile=myvalue"
        !output.contains("See the profiling report at")
    }

    def "task options apply to most recent task"() {
        when:
        buildScript """
            ${defineTaskWithProfileOption()}

            tasks.register('mytaskA', MyTask.class)
            tasks.register('mytaskB', MyTask.class)
        """

        then:
        succeeds "--", "mytaskA", "--profile", "myvalueA", "mytaskB", "--profile", "myvalueB"
        output.contains "profile=myvalueA"
        output.contains "profile=myvalueB"
    }

    def "task options apply to most recent task -- first task only"() {
        when:
        buildScript """
            ${defineTaskWithProfileOption()}

            tasks.register('mytaskA', MyTask.class)
            tasks.register('mytaskB', MyTask.class)
        """

        then:
        succeeds "--", "mytaskA", "mytaskB", "--profile", "myvalueB"
        output.contains "profile=myvalueB"
    }

    def "task options apply to most recent task -- second task only"() {
        when:
        buildScript """
            ${defineTaskWithProfileOption()}

            tasks.register('mytaskA', MyTask.class)
            tasks.register('mytaskB', MyTask.class)
        """

        then:
        succeeds "--", "mytaskA", "--profile", "myvalueA", "mytaskB"
        output.contains "profile=myvalueA"
    }

    def "runs built-in and task options when both are supplied"() {
        when:
        buildScript """
            ${defineTaskWithProfileOption()}

            tasks.register('mytask', MyTask.class)
        """

        then:
        succeeds "--profile", "--", "mytask", "--profile", "myvalue"
        output.contains "profile=myvalue"
        output.contains "See the profiling report at"
    }

    def "passes task options associated with a task originating from buildSrc"() {
        when:
        file('buildSrc/src/main/java/MyTask.java') << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.options.Option;
            import org.gradle.api.tasks.Optional;
            import org.gradle.api.tasks.Input;

            public abstract class MyTask extends DefaultTask {
                @Option(option="profile", description="foobar")
                @Optional
                @Input
                abstract Property<String> getProfile();

                @TaskAction
                void run() {
                    if (getProfile().isPresent()) {
                        System.out.println(getName() + "profile=" + getProfile().get());
                    }
                }
            }
        """
        buildScript """
            import MyTask
            tasks.register('mytask', MyTask.class)
        """

        then:
        succeeds "--", "mytask", "--profile", "myvalue"
        output.contains "profile=myvalue"
    }

    def "passes task options associated with a task originating from an included build"() {
        when:
        file('settings.gradle') << """
            rootProject.name = 'root-project'

            includeBuild 'included-build'
        """
        file('included-build/build.gradle') << """
            ${defineTaskWithProfileOption()}

            tasks.register('mytask', MyTask.class)
        """
        buildScript """
            // no content needed
        """

        then:
        succeeds "--", ":included-build:mytask", "--profile", "myvalue"
        output.contains "profile=myvalue"
    }

}
