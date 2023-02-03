/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.c

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.AbstractNativeLanguageIncrementalCompileWithDiscoveredInputsIntegrationTest
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.IncrementalHelloWorldApp
import spock.lang.Issue

class CLanguageIncrementalCompileIntegrationTest extends AbstractNativeLanguageIncrementalCompileWithDiscoveredInputsIntegrationTest {
    @Override
    IncrementalHelloWorldApp getHelloWorldApp() {
        return new CHelloWorldApp()
    }

    @Issue("GRADLE-3109")
    @ToBeFixedForConfigurationCache
    def "recompiles source file that includes header file on first line"() {
        given:
        sourceFile << """#include "${otherHeaderFile.name}"
"""
        and:
        outputs.snapshot { run "mainExecutable" }

        when:
        otherHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFile sourceFile
    }
}
