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
package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.process.internal.WorkerProcessContext;

import java.io.Serializable;

public class DefaultCompilerDaemon implements Action<WorkerProcessContext>, CompilerDaemon, Serializable {
    public void execute(WorkerProcessContext context) {
        context.getServerConnection().addIncoming(CompilerDaemon.class, this);
    }

    public CompileResult execute(Compiler compiler) {
        try {
            WorkResult result = compiler.execute();
            return new CompileResult(result.getDidWork(), null);
        } catch (Throwable t) {
            return new CompileResult(true, t);
        }
    }
}
