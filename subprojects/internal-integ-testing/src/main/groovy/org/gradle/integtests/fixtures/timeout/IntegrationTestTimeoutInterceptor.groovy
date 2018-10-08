/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.timeout

import org.gradle.api.Action
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.InProcessGradleExecuter
import org.spockframework.runtime.SpockAssertionError
import org.spockframework.runtime.SpockTimeoutError
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.extension.MethodInvocation
import org.spockframework.runtime.extension.builtin.TimeoutInterceptor
import org.spockframework.runtime.model.MethodInfo

import java.util.concurrent.TimeUnit

class IntegrationTestTimeoutInterceptor extends TimeoutInterceptor {
    IntegrationTestTimeoutInterceptor(IntegrationTestTimeout timeout) {
        super(new TimeoutAdapter(timeout))
    }

    IntegrationTestTimeoutInterceptor(int timeoutSeconds) {
        super(new TimeoutAdapter(timeoutSeconds, TimeUnit.SECONDS))
    }

    @Override
    void intercept(final IMethodInvocation invocation) throws Throwable {
        try {
            super.intercept(invocation)
        } catch (SpockTimeoutError e) {
            String allThreadStackTraces = getAllStackTraces(invocation)
            throw new SpockAssertionError(allThreadStackTraces, e)
        } catch (Throwable t) {
            throw t
        }
    }

    void intercept(Action<Void> action) {
        MethodInfo methodInfo = new MethodInfo()
        methodInfo.setName('MockMethod')
        intercept(new MethodInvocation(null, null, null, null, null, methodInfo, null) {
            void proceed() throws Throwable {
                action.execute(null)
            }
        })
    }

    static boolean isInProcessExecuter(AbstractIntegrationSpec spec) {
        return spec.executer.gradleExecuter.class == InProcessGradleExecuter
    }

    static String getAllStackTraces(IMethodInvocation invocation) {
        try {
            Object instance = invocation.getInstance()
            if (instance instanceof AbstractIntegrationSpec && isInProcessExecuter(instance)) {
                return getAllStackTracesInCurrentJVM()
            } else {
                return JavaProcessStackTracesMonitor.getAllStackTracesByJstack()
            }
        } catch (Throwable e) {
            def stream = new ByteArrayOutputStream()
            e.printStackTrace(new PrintStream(stream))
            return "Error in attempt to fetch  stacktraces: ${stream.toString()}"
        }
    }

    static String getAllStackTracesInCurrentJVM() {
        Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces()
        StringBuilder sb = new StringBuilder()
        sb.append("Threads in current JVM:\n")
        sb.append("--------------------------\n")
        allStackTraces.each { Thread thread, StackTraceElement[] stackTraces ->
            sb.append("Thread ${thread.getId()}: ${thread.getName()}\n")
            sb.append("--------------------------\n")
            stackTraces.each {
                sb.append("${it}\n")
            }
            sb.append("--------------------------\n")
        }

        return sb.toString()
    }
}
