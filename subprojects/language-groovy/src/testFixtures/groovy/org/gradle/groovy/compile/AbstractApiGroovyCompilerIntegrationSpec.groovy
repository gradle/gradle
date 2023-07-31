/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.groovy.compile

import org.gradle.integtests.fixtures.DefaultTestExecutionResult

abstract class AbstractApiGroovyCompilerIntegrationSpec extends AbstractGroovyCompilerIntegrationSpec {
    def canEnableAndDisableIntegerOptimization() {
        if (versionLowerThan('1.8')) {
            return
        }
        // Integer optimizations don't seem to have an effect in Groovy 4 anymore
        if (versionNumber.major >= 4) {
            return
        }

        when:
        run("sanityCheck")

        then:
        noExceptionThrown()
    }

    def canEnableAndDisableAllOptimizations() {
        if (versionLowerThan('1.8')) {
            return
        }

        when:
        run("sanityCheck")

        then:
        noExceptionThrown()
    }

    def canUseCustomFileExtensions() {
        if (versionLowerThan('1.7')) {
            return
        }

        when:
        run("test")

        then:
        noExceptionThrown()
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("Person")
        result.testClass("Person").assertTestPassed("testMe")
    }
}
