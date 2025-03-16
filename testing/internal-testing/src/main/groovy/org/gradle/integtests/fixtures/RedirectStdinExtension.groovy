/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.fixtures

import groovy.transform.CompileStatic
import org.gradle.internal.UncheckedException
import org.gradle.internal.concurrent.CompositeStoppable
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo

@CompileStatic
class RedirectStdinExtension implements IAnnotationDrivenExtension<RedirectStdIn> {

    private static IMethodInterceptor interceptor = new RedirectingInterceptor()

    @Override
    void visitSpecAnnotation(RedirectStdIn annotation, SpecInfo spec) {
        spec.bottomSpec.allFeatures.each {
            it.addIterationInterceptor(interceptor)
        }
    }

    private static class RedirectingInterceptor implements IMethodInterceptor {

        private PipedInputStream emulatedSystemIn
        private OutputStream stdinPipe

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            def oldStdin = System.in
            initPipe()
            System.setIn(emulatedSystemIn)
            try {
                invocation.proceed()
            } finally {
                System.setIn(oldStdin)
                closePipe()
            }
        }

        private void initPipe() {
            if (stdinPipe == null) {
                emulatedSystemIn = new PipedInputStream();
                try {
                    stdinPipe = new PipedOutputStream(emulatedSystemIn);
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }

        private void closePipe() {
            CompositeStoppable.stoppable(stdinPipe, emulatedSystemIn).stop();
            stdinPipe = null;
            emulatedSystemIn = null;
        }
    }
}

