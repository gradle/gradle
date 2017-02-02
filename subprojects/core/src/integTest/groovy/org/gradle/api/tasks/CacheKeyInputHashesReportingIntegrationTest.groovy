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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.LocalBuildCacheFixture

class CacheKeyInputHashesReportingIntegrationTest extends AbstractIntegrationSpec implements LocalBuildCacheFixture {

    def "input hashes can be collected"() {
        buildFile << '''
            plugins {
                id 'java'
            }
            
            import org.gradle.api.internal.tasks.execution.BuildCacheKeyInputs
            import org.gradle.api.internal.tasks.execution.TaskOutputCachingListener
            
            class TaskHashesListener implements TaskOutputCachingListener {
                void inputsCollected(Task task, BuildCacheKey key, BuildCacheKeyInputs hashes) {
                    println """Inputs collected for ${task}: Input hashes - ${hashes.inputHashes.collect { propertyName, hash ->
                        "${propertyName}:${hash}" }.join(',')}""" 
                }
            }
            
            gradle.addListener(new TaskHashesListener())
        '''.stripIndent()

        file('src/main/java/Some.java') << """
            public class Some {}
        """.stripIndent()

        when:
        succeeds ':jar'

        then:
        def cacheableTasks = [':compileJava', ':jar']
        nonSkippedTasks.containsAll(cacheableTasks)
        cacheableTasks.each {
            assert output.contains("Inputs collected for task '${it}'")
        }

        when:
        succeeds ':jar'

        then:
        skippedTasks.containsAll(cacheableTasks)
        cacheableTasks.each {
            assert output.contains("Inputs collected for task '${it}'")
        }
    }
}
