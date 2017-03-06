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
import spock.lang.Unroll

@Unroll
class CacheKeyInputHashesReportingIntegrationTest extends AbstractIntegrationSpec implements LocalBuildCacheFixture {

    def setup() {
        file('src/main/java/Some.java') << """
            public class Some {}
        """.stripIndent()
    }

    def "input hashes are collected for #arguments"() {
        buildFile << '''
            plugins {
                id 'java'
            }
            
            import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey
            import org.gradle.caching.internal.tasks.TaskOutputCachingListener
            
            class TaskHashesListener implements TaskOutputCachingListener {
                void cacheKeyEvaluated(Task task, TaskOutputCachingBuildCacheKey key) {
                    println task
                    assert key.isValid()
                    def hashes = key.inputs
                    println """Inputs collected for ${task}: Input hashes - ${hashes.inputHashes.collect { propertyName, hash ->
                        "${propertyName}:${hash}" }.join(',')}""" 
                }
            }
            
            gradle.addListener(new TaskHashesListener())
        '''.stripIndent()

        when:
        withBuildCache().succeeds ':jar'

        then:
        nonSkippedTasks.containsAll(cacheableTasks)
        cacheableTasks.each {
            assertInputHashesCollected(it)
        }

        when:
        withBuildCache().succeeds(*arguments)

        then:
        skippedTasks.containsAll(expectedSkippedTasks)
        nonSkippedTasks.containsAll(expectedNonSkippedTasks)
        cacheableTasks.each {
            assertInputHashesCollected(it)
        }

        where:
        arguments                   | expectedSkippedTasks     | expectedNonSkippedTasks
        [':clean', ':jar']          | [':compileJava'] | []
        [':jar']                    | [':compileJava'] | []
        [':jar', '--rerun-tasks']   | []                       | [':compileJava', ':jar']
        cacheableTasks = expectedSkippedTasks + expectedNonSkippedTasks

    }

    private void assertInputHashesCollected(String taskPath) {
        assert output.contains("Inputs collected for task '${taskPath}'")
    }
}
