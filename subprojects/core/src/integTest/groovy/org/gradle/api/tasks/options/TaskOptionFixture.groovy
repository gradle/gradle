/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.tasks.options

final class TaskOptionFixture {

    private TaskOptionFixture() {}

    static String taskWithMultipleOptions() {
        """
            import org.gradle.api.tasks.options.Option
        
            class SomeTask extends DefaultTask {
                private boolean first
                private String second
                private TestEnum third
        
                @Option(option = "first", description = "configures 'first' field")
                void setFirst(boolean first) {
                    this.first = first
                }
        
                @Option(option = "second", description = "configures 'second' field")
                void setSecond(String second) {
                    this.second = second
                }
        
                //more stress
                void setSecond(Object second) {
                    this.second = second.toString()
                }
        
                @Option(option = "third", description = "configures 'third' field")
                void setThird(TestEnum blubb) {
                    this.third = blubb
                }
        
                @TaskAction
                void renderFields() {
                    println "first=" + first + ",second=" + second + ",third=" + third
                }

                enum TestEnum {
                    valid1, valid2, valid3
                }
            }
        """
    }
}
