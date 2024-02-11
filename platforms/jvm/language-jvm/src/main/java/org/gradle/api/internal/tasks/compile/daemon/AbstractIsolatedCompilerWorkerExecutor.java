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

import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Cast;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.workers.WorkAction;
import org.gradle.workers.internal.ActionExecutionSpecFactory;
import org.gradle.workers.internal.BuildOperationAwareWorker;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.DefaultWorkResult;
import org.gradle.workers.internal.IsolatedClassLoaderWorkerRequirement;
import org.gradle.workers.internal.ProvidesWorkResult;
import org.gradle.workers.internal.WorkerFactory;

import javax.inject.Inject;

/**
 * Base implementation of {@link CompilerWorkerExecutor} which handles submitting a compile work item to execute.
 * Inheritors need to provide an appropriate isolated worker requirement depending on what isolation mode is being used.
 */
abstract public class AbstractIsolatedCompilerWorkerExecutor implements CompilerWorkerExecutor {
    private final WorkerFactory workerFactory;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;

    public AbstractIsolatedCompilerWorkerExecutor(WorkerFactory workerFactory, ActionExecutionSpecFactory actionExecutionSpecFactory) {
        this.workerFactory = workerFactory;
        this.actionExecutionSpecFactory = actionExecutionSpecFactory;
    }

    abstract IsolatedClassLoaderWorkerRequirement getIsolatedWorkerRequirement(DaemonForkOptions daemonForkOptions);

    @Override
    public DefaultWorkResult execute(CompilerParameters parameters, DaemonForkOptions daemonForkOptions) {
        IsolatedClassLoaderWorkerRequirement workerRequirement = getIsolatedWorkerRequirement(daemonForkOptions);
        BuildOperationAwareWorker worker = workerFactory.getWorker(workerRequirement);

        return worker.execute(actionExecutionSpecFactory.newIsolatedSpec("compiler daemon", CompilerWorkAction.class, parameters, workerRequirement, true));
    }

    public static class CompilerWorkAction implements WorkAction<CompilerParameters>, ProvidesWorkResult {
        private DefaultWorkResult workResult;
        private final CompilerParameters parameters;
        private final Instantiator instantiator;

        @Inject
        public CompilerWorkAction(CompilerParameters parameters, Instantiator instantiator) {
            this.parameters = parameters;
            this.instantiator = instantiator;
        }

        @Override
        public CompilerParameters getParameters() {
            return parameters;
        }

        @Override
        public void execute() {
            Class<? extends Compiler<?>> compilerClass = Cast.uncheckedCast(ClassLoaderUtils.classFromContextLoader(getParameters().getCompilerClassName()));
            Compiler<?> compiler = instantiator.newInstance(compilerClass, getParameters().getCompilerInstanceParameters());
            setWorkResult(compiler.execute(Cast.uncheckedCast(getParameters().getCompileSpec())));
        }

        private void setWorkResult(WorkResult workResult) {
            if (workResult instanceof DefaultWorkResult) {
                this.workResult = (DefaultWorkResult) workResult;
            } else {
                this.workResult = new DefaultWorkResult(workResult.getDidWork(), null);
            }
        }

        @Override
        public DefaultWorkResult getWorkResult() {
            return workResult;
        }
    }
}
