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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

class RetainStacktraceForInheritedTestMethodsTest extends AbstractIntegrationSpec {
    @Rule
    final TestResources resources = new TestResources(testDirectoryProvider)

    def "retainsStackTraceForInheritedTestMethods"() {
        given:
        executer.withRepositoryMirrors()
        executer.withStackTraceChecksDisabled()

        when:
        runAndFail "test"

        then:
        outputContains("Base.java:6")
        // TestThatInherits > alwaysFails() FAILED
        //     org.opentest4j.AssertionFailedError: Oops, base failed again
        //         at org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:39)
        //         at org.junit.jupiter.api.Assertions.fail(Assertions.java:109)
        //         at Base.alwaysFails(Base.java:6) <- this
        //         at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        //         at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
        //         at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        //         at java.base/java.lang.reflect.Method.invoke(Method.java:566)
        //         at org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:675)
        //         at org.junit.jupiter.engine.execution.MethodInvocation.proceed(MethodInvocation.java:60)
        //         at org.junit.jupiter.engine.execution.InvocationInterceptorChain$ValidatingInvocation.proceed(InvocationInterceptorChain.java:125)
        //         at org.junit.jupiter.engine.extension.TimeoutExtension.intercept(TimeoutExtension.java:132)
        //         at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestableMethod(TimeoutExtension.java:124)
        //         at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestMethod(TimeoutExtension.java:74)
        //         at org.junit.jupiter.engine.execution.ExecutableInvoker$ReflectiveInterceptorCall.lambda$ofVoidMethod$0(ExecutableInvoker.java:115)
    }
}
