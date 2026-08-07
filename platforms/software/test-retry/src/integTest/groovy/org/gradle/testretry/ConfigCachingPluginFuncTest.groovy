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

class ConfigCachingPluginFuncTest extends AbstractGeneralPluginFuncTest {

    def "compatible with configuration cache when tests pass"() {
        when:
        buildFile << """
            test.retry.maxRetries = 1
        """

        successfulTest()

        then:
        executer.withStackTraceChecksDisabled()
        succeeds('test', '--configuration-cache')

        and:
        output.count('PASSED') == 1

        when:
        executer.withStackTraceChecksDisabled()
        succeeds('test', '--configuration-cache')

        then:
        output.contains('Reusing configuration cache.')
    }

    def "compatible with configuration cache when failed tests are retried"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        flakyTest()

        when:
        executer.withStackTraceChecksDisabled()
        succeeds('test', '--configuration-cache')

        then:
        with(output) {
            assert it.count('PASSED') == 1
            assert it.count('FAILED') == 1
        }

        when:
        executer.withStackTraceChecksDisabled()
        succeeds('test', '--configuration-cache')

        then:
        output.contains('Reusing configuration cache.')
    }

    def "compatible with configuration cache when Develocity plugin is also present"() {
        given:
        buildFile
            << DslExtensionType.DISTRIBUTION.getSnippet()
            << DslExtensionType.DEVELOCITY.getSnippet()

        failedTest()

        when:
        executer.withStackTraceChecksDisabled()
        fails('test', '--info', '--configuration-cache')

        then:
        !output.contains('Reusing configuration cache.')
        output.contains('handled by the Develocity plugin')

        when:
        executer.withStackTraceChecksDisabled()
        fails('test', '--info', '--configuration-cache')

        then:
        output.contains('Reusing configuration cache.')
        output.contains('handled by the Develocity plugin')
    }
}
