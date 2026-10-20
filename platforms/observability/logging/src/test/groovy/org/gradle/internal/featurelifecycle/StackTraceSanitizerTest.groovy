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

package org.gradle.internal.featurelifecycle

import spock.lang.Specification
import spock.lang.Subject

import static SimulatedSingleMessageLogger.DIRECT_CALL
import static SimulatedSingleMessageLogger.INDIRECT_CALL
import static SimulatedSingleMessageLogger.INDIRECT_CALL_2

@Subject(StackTraceSanitizer)
class StackTraceSanitizerTest extends Specification {

    def "stack is evaluated correctly for #callLocationClass.simpleName and #expectedSummary. #expectedMethod. #usage."() {
        given:
        def stack = new StackTraceSanitizer(usage.calledFrom).transform(usage.exception.stackTrace)

        expect:
        !stack.empty
        usage.summary == expectedSummary

        def stackTraceRoot = stack[0]
        stackTraceRoot.className == callLocationClass.name
        stackTraceRoot.methodName == expectedMethod

        where:
        callLocationClass           | expectedSummary | expectedMethod | usage
        SimulatedJavaCallLocation   | DIRECT_CALL     | 'create'       | SimulatedJavaCallLocation.create()
        SimulatedJavaCallLocation   | INDIRECT_CALL   | 'indirectly'   | SimulatedJavaCallLocation.indirectly()
        SimulatedJavaCallLocation   | INDIRECT_CALL_2 | 'indirectly2'  | SimulatedJavaCallLocation.indirectly2()
        SimulatedGroovyCallLocation | DIRECT_CALL     | 'create'       | SimulatedGroovyCallLocation.create()
        SimulatedGroovyCallLocation | INDIRECT_CALL   | 'indirectly'   | SimulatedGroovyCallLocation.indirectly()
        SimulatedGroovyCallLocation | INDIRECT_CALL_2 | 'indirectly2'  | SimulatedGroovyCallLocation.indirectly2()
    }

}
