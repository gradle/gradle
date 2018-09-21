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

package org.gradle.integtests.resolve

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec



class ConfigurationDeadlockIntegrationTest extends AbstractIntegrationSpec {

    @NotYetImplemented
    def "deadlock during task execution when other synchronization is in place"() {
        settingsFile << """
            include ":a", ":b"
        """

        buildFile << """
            import java.util.concurrent.CountDownLatch
            import java.util.concurrent.TimeUnit

            def countDown = new CountDownLatch(2)

            project(':a') {
                configurations { 
                    foo
                }
                
                task foo {
                    doFirst {
                        countDown.countDown()
                        println "Waiting for bar..."
                        assert countDown.await(10, TimeUnit.SECONDS)
                    }
                }
            }
            
            project(':b') {
                task wait {
                    doFirst {
                        sleep 1000
                    }
                }
                
                task bar {
                    dependsOn wait  
                    doFirst {
                        // Causes deadlock because project(':a') is holding his mutation lock
                        project(':a').configurations.foo.files
                        
                        // We don't get here until the task in project(':a') times out and releases the lock
                        countDown.countDown()
                        println "Waiting for foo..."
                        assert countDown.await(10, TimeUnit.SECONDS)
                    } 
                }
            }
        """

        expect:
        executer.withArgument("--parallel")
        succeeds("foo", "bar")
    }
}
