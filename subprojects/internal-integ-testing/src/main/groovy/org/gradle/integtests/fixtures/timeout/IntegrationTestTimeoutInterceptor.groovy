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

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.spockframework.runtime.SpockAssertionError
import org.spockframework.runtime.SpockTimeoutError
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.extension.MethodInvocation
import org.spockframework.runtime.extension.builtin.TimeoutInterceptor
import org.spockframework.runtime.model.MethodInfo

import java.util.concurrent.TimeUnit

@CompileStatic
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
            String threadDumpFile = getThreadDump()
            throw new SpockAssertionError("Timeout, see thread dumps at $threadDumpFile", e)
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

    static String getThreadDump() {
        try {
            return new JavaProcessStackTracesMonitor(new File(".")).printAllStackTracesByJstack()
        } catch (Throwable e) {
            def stream = new ByteArrayOutputStream()
            e.printStackTrace(new PrintStream(stream))
            return "Error in attempt to fetch stacktraces: ${stream.toString()}"
        }
    }
}
