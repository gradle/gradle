/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.util.internal.ToBeImplemented

class ConfigurationCacheExecutionTimeProblemsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @ToBeImplemented
    def "cacheable task fails on execution time problem and is not up-to-date afterwards"() {
        def configurationCache = new ConfigurationCacheFixture(this)

        javaFile "src/main/java/Main.java", """public class Main {}"""

        buildFile """
            plugins { id("java") }

            version = "foo-version"
            tasks.compileJava { // using a cacheable task
                doLast {
                    println("version:" + project.version.toString())
                }
            }
        """

        when:
        configurationCacheFails "compileJava"

        then:
        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            problem "Build file 'build.gradle': line 7: invocation of 'Task.project' at execution time is unsupported."
        }

        // TODO: It is wrong to allow users observe stub project data, we should fail the task instead
        and:
        outputContains("version:unspecified")

        when:
        configurationCacheRun "compileJava"
        // TODO: Should be:
//        configurationCacheFails "compileJava"

        then:
        configurationCache.assertStateStored()
        // TODO: Should be:
//        configurationCache.assertStateStoredAndDiscarded {
//            hasStoreFailure = false
//            problem "Build file 'build.gradle': line 7: invocation of 'Task.project' at execution time is unsupported."
//        }

        // TODO: Must not be up-to-date
        and:
        outputContains("> Task :compileJava UP-TO-DATE")
    }

}
