/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry

class BuildCachingFuncTest extends AbstractGeneralPluginFuncTest {

    def "test task is still cacheable"() {
        given:
        successfulTest()
        buildFile << """
            test.retry.maxRetries = 1
        """

        when:
        succeeds('--build-cache', 'test')

        and:
        succeeds('--build-cache', 'clean', 'test')

        then:
        result.assertTaskSkipped(':test')
    }

    def "maxRetries and maxFailures are not treated as inputs"() {
        given:
        successfulTest()
        buildFile << """
            test.retry.maxRetries = 1
        """

        when:
        succeeds('--build-cache', 'test')

        and:
        buildFile << """
            test.retry {
                maxRetries = 2
                maxFailures = 2
            }
        """
        succeeds('--build-cache', 'clean', 'test')

        then:
        result.assertTaskSkipped(':test')
    }

    def "failOnPassedAfterRetry is input"() {
        given:
        flakyTest()
        buildFile << """
            test.retry.maxRetries = 1
        """

        when:
        succeeds('--build-cache', 'test')

        and:
        buildFile << """
            test.retry {
                failOnPassedAfterRetry = true
            }
        """
        fails('--build-cache', 'clean', 'test')

        then:
        true
    }

    def "removing plugin invalidates cached result"() {
        given:
        flakyTest()
        buildFile << """
            test.retry.maxRetries = 1
        """

        when:
        succeeds('--build-cache', 'test')

        and:
        buildFile.text = baseBuildScriptWithoutPlugin()
        fails('--build-cache', 'clean', 'test')

        then:
        true
    }
}
