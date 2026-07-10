/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification

class ForkOptionsTest extends Specification {
    static final List PROPS = ['executable', 'memoryInitialSize', 'memoryMaximumSize', 'tempDir']

    ForkOptions forkOptions = TestUtil.newInstance(ForkOptions)

    def 'initial values of forkOptions'() {
        expect:
        forkOptions.executable == null
        forkOptions.javaHome == null
        forkOptions.memoryInitialSize == null
        forkOptions.memoryMaximumSize == null
        forkOptions.tempDir == null
        forkOptions.jvmArgs == []
    }

    @Issue("https://github.com/gradle/gradle/issues/32606")
    def "getAllJvmArgs() returns only String"() {
        given:
        def commandLineArgumentProvider = new CommandLineArgumentProvider() {
            @Override
            Iterable<String> asArguments() {
                return ["${'make this a GString'}"]
            }
        }
        forkOptions.jvmArgumentProviders.add(commandLineArgumentProvider)

        expect:
        commandLineArgumentProvider.asArguments().iterator().next() instanceof GString
        forkOptions.allJvmArgs.size() == 1
        forkOptions.allJvmArgs[0] instanceof String
    }
}
