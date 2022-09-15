/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.cli

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskOptionsSpec extends AbstractIntegrationSpec {
    def "can use a built-in option"() {
        when:
        buildScript """
            abstract class MyTask extends DefaultTask {
                @Option(option="profile", description="foobar")
                void setProfile(String value) {
                    System.out.println("profile=" + value)
                }
            }

            tasks.register('mytask', MyTask.class)
        """

        then:
        succeeds "mytask", "--profile"
    }

    def "can use -- to specify a task option with same name as a built-in option"() {
        when:
        buildScript """
            abstract class MyTask extends DefaultTask {
                @Option(option="profile", description="foobar")
                @Optional
                @Input
                abstract Property<String> getProfile()

                @TaskAction
                void run() {
                    if (getProfile().isPresent()) {
                        logger.lifecycle("profile=" + getProfile().get())
                    }
                }
            }

            tasks.register('mytask', MyTask.class)
        """

        then:
        succeeds "--", "mytask", "--profile", "myvalue"
        output.contains "profile=myvalue"
    }

    def "task options apply to most recent task"() {
        when:
        buildScript """
            abstract class MyTaskA extends DefaultTask {
                @Option(option="profile", description="foobar")
                @Optional
                @Input
                abstract Property<String> getProfile()

                @TaskAction
                void run() {
                    if (getProfile().isPresent()) {
                        logger.lifecycle("profile=" + getProfile().get())
                    }
                }
            }

            abstract class MyTaskB extends DefaultTask {
                @Option(option="profile", description="foobar")
                @Optional
                @Input
                abstract Property<String> getProfile()

                @TaskAction
                void run() {
                    if (getProfile().isPresent()) {
                        logger.lifecycle("profile=" + getProfile().get())
                    }
                }
            }

            tasks.register('mytaskA', MyTaskA.class)
            tasks.register('mytaskB', MyTaskB.class)
        """

        then:
        succeeds "--", "mytaskA", "--profile", "myvalueA", "mytaskB", "--profile", "myvalueB"
        output.contains "Aprofile=myvalueA"
        output.contains "Bprofile=myvalueB"
    }
}
