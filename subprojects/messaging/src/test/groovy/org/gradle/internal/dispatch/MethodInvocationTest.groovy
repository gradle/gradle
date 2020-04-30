/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.dispatch

import spock.lang.Specification

import static org.gradle.util.Matchers.strictlyEqual
import static org.hamcrest.MatcherAssert.assertThat

class MethodInvocationTest extends Specification {

    def "equality"() {
        def invocation = new MethodInvocation(String.class.getMethod("length"), ["param"] as Object[])
        def equalInvocation = new MethodInvocation(String.class.getMethod("length"), ["param"] as Object[])
        def differentMethod = new MethodInvocation(String.class.getMethod("getBytes"), ["param"] as Object[])
        def differentArgs = new MethodInvocation(String.class.getMethod("length"), ["a", "b"] as Object[])
        def nullArgs = new MethodInvocation(String.class.getMethod("length"), null)

        expect:
        assertThat(invocation, strictlyEqual(equalInvocation))
        invocation != differentMethod
        invocation != differentArgs
        invocation != nullArgs
        nullArgs != invocation
    }

    def "string representation"() {
        expect:
        new MethodInvocation(String.class.getMethod("length"), ["1", "2"] as Object[]).toString() == "[MethodInvocation method: length(1, 2)]"
        new MethodInvocation(String.class.getMethod("length"), null).toString() == "[MethodInvocation method: length()]"
    }
}
