/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.UncheckedException;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerParameters;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.DefaultWorkResult;

public abstract class AbstractDaemonCompiler<T> implements Compiler<T> {
    private final CompilerWorkerExecutor compilerWorkerExecutor;

    public AbstractDaemonCompiler(CompilerWorkerExecutor compilerWorkerExecutor) {
        this.compilerWorkerExecutor = compilerWorkerExecutor;
    }

    @Override
    public WorkResult execute(T spec) {
        DefaultWorkResult result = compilerWorkerExecutor.execute(getCompilerParameters(spec), toDaemonForkOptions(spec));
        if (result.isSuccess()) {
            return result;
        } else {
            throw UncheckedException.throwAsUncheckedException(result.getException());
        }
    }

    protected abstract DaemonForkOptions toDaemonForkOptions(T spec);

    protected abstract CompilerParameters getCompilerParameters(T spec);

}
