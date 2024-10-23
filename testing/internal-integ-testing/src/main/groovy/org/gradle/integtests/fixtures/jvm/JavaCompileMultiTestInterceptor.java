/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.jvm;

import org.gradle.integtests.fixtures.extensions.AbstractMultiTestInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;

public class JavaCompileMultiTestInterceptor extends AbstractMultiTestInterceptor {

    public static Compiler compiler;

    enum Compiler {
        IN_PROCESS_JDK_COMPILER,
        WORKER_JDK_COMPILER,
        WORKER_COMMAND_LINE_COMPILER
    }

    JavaCompileMultiTestInterceptor(Class<?> target) {
        super(target);
    }

    @Override
    protected void createExecutions() {
        add(new JavaCompilerExecution(Compiler.IN_PROCESS_JDK_COMPILER));
        add(new JavaCompilerExecution(Compiler.WORKER_JDK_COMPILER));
        add(new JavaCompilerExecution(Compiler.WORKER_COMMAND_LINE_COMPILER));
    }

    private static class JavaCompilerExecution extends AbstractMultiTestInterceptor.Execution {
        private final Compiler compiler;

        JavaCompilerExecution(Compiler compiler) {
            this.compiler = compiler;
        }

        @Override
        protected String getDisplayName() {
            return compiler.name();
        }

        @Override
        public String toString() {
            return getDisplayName();
        }

        @Override
        protected void before(IMethodInvocation invocation) {
            JavaCompileMultiTestInterceptor.compiler = this.compiler;
        }
    }
}
