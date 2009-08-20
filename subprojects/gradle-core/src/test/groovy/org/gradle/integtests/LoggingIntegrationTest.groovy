/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.integtests

import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.junit.runner.RunWith
import org.junit.Test

/**
 * @author Hans Dockter
 */
// todo To make this test stronger, we should check against the output of a file appender. Right now Gradle does not provided this easily but eventually will.
@RunWith(DistributionIntegrationTestRunner.class)
class LoggingIntegrationTest {
    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void loggingSamples() {
        File loggingDir = new File(dist.samplesDir, 'logging')
        List quietMessages = [
                'An info log message which is always logged.',
                'A message which is logged at QUIET level',
                'A task message which is logged at QUIET level',
                'quietProject2Out',
                'quietProject2ScriptClassPathOut'
        ]
        List errorMessages = [
                'An error log message.'
        ]
        List warningMessages = [
                'A warning log message.'
        ]
        List lifecycleMessages = [
                'A lifecycle info log message.',
                'An info message logged from Ant',
                'A task message which is logged at LIFECYCLE level'
        ]
        List infoMessages = [
                'An info log message.',
                'A message which is logged at INFO level',
                'A task message which is logged at INFO level',
                'An info log message logged using SLF4j',
                'An info log message logged using JCL',
                'An info log message logged using Log4j',
                'An info log message logged using JUL',
                'infoProject2ScriptClassPathOut'
        ]
        List debugMessages = [
                'A debug log message.'
        ]
        List traceMessages = [
                'A trace log message.'
        ]
        List allOuts = [errorMessages, quietMessages, warningMessages, lifecycleMessages, infoMessages, debugMessages, traceMessages]

        checkOutput(executer.inDirectory(loggingDir).withTasks('log').withArguments('-q').run(), allOuts, 1)
        checkOutput(executer.withArguments().run(), allOuts, 3)
        checkOutput(executer.withArguments('-i').run(), allOuts, 4)
        checkOutput(executer.withArguments('-d').run(), allOuts, 5)
    }

    static void checkOutput(ExecutionResult result, List allOuts, int includedIndex) {
        allOuts.eachWithIndex {List outList, i ->
            checkOuts(i <= includedIndex, i == 0 ? result.error : result.output, outList)
        }
    }

    static void checkOuts(boolean shouldContain, String result, List outs) {
        outs.each {String expectedOut ->
            def matcher = containsString(expectedOut)
            if (!shouldContain) {
                matcher = not(matcher)
            }
            assertThat(result, matcher)
        }
    }
}
