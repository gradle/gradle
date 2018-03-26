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

package org.gradle.internal.logging.console.taskgrouping


abstract class AbstractLoggingHooksFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest{
    def setup() {
        buildFile << """
            class CollectingListener implements StandardOutputListener {
                def result = new StringBuilder()
                
                String toString() {
                    return result.toString()
                }
                
                void onOutput(CharSequence output) {
                    result.append(output)
                }
            }
        """
    }

    def "listener added to task receives only the output generated while the task is running"() {
        buildFile << """
            def o = new CollectingListener()
            def e = new CollectingListener()
            def none = new CollectingListener()
            task log {
                doLast {
                    logging.addStandardOutputListener(o)
                    logging.addStandardErrorListener(e)
                    System.out.println "output" 
                    System.err.println "error" 
                }
            }
            task other {
                dependsOn log
                doLast {
                    System.out.println "other" 
                    System.err.println "other" 
                }
            }
            
            gradle.buildFinished {
                log.logging.addStandardOutputListener(none)
                log.logging.addStandardErrorListener(none)
                println "finished"
                assert o.toString().readLines() == [":log", "output"]
                assert e.toString().readLines() == ["error"]
                assert none.toString().readLines() == []
            }
        """

        expect:
        succeeds("log", "other")
    }
}
