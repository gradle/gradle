/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.DefaultWorkResult;

import java.io.Serializable;

/**
 * Encapsulates the logic to execute a work item for an {@link AbstractDaemonCompiler}.
 */
public interface CompilerWorkerExecutor {
    /**
     * Executes a compiler specified by the {@link CompilerParameters}
     */
    DefaultWorkResult execute(CompilerParameters parameters, DaemonForkOptions daemonForkOptions);

    abstract class CompilerParameters implements WorkParameters, Serializable {
        private final String compilerClassName;
        private final Object[] compilerInstanceParameters;

        public CompilerParameters(String compilerClassName, Object[] compilerInstanceParameters) {
            this.compilerClassName = compilerClassName;
            this.compilerInstanceParameters = compilerInstanceParameters;
        }

        public String getCompilerClassName() {
            return compilerClassName;
        }

        public Object[] getCompilerInstanceParameters() {
            return compilerInstanceParameters;
        }

        abstract public CompileSpec getCompileSpec();
    }

}
