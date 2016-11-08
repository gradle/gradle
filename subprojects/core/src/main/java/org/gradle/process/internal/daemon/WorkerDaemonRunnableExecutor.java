/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.daemon;

import org.gradle.api.Transformer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.util.CollectionUtils;

public class WorkerDaemonRunnableExecutor extends AbstractWorkerDaemonExecutor<Runnable> {

    WorkerDaemonRunnableExecutor(WorkerDaemonFactory workerDaemonFactory, FileResolver fileResolver, Class<? extends Runnable> implementationClass) {
        super(workerDaemonFactory, fileResolver, implementationClass);
    }

    @Override
    public void execute() {
        final ParamSpec spec = new ParamSpec(getParams());
        final WrappedDaemonRunnable daemonRunnable = new WrappedDaemonRunnable(getImplementationClass());
        Iterable<Class<?>> paramTypes = CollectionUtils.collect(getParams(), new Transformer<Class<?>, Object>() {
            @Override
            public Class<?> transform(Object o) {
                return o.getClass();
            }
        });
        final DaemonForkOptions daemonForkOptions = toDaemonOptions(getImplementationClass(), paramTypes, getForkOptions(), getClasspath(), getSharedPackages());

        WorkerDaemon daemon = getWorkerDaemonFactory().getDaemon(getForkOptions().getWorkingDir(), daemonForkOptions);
        daemon.execute(daemonRunnable, spec);
    }

    private static class ParamSpec implements WorkSpec {
        final Object[] params;

        ParamSpec(Object[] params) {
            this.params = params;
        }

        public Object[] getParams() {
            return params;
        }
    }

    private static class WrappedDaemonRunnable implements WorkerDaemonAction<ParamSpec> {
        private final Class<? extends Runnable> runnableClass;

        WrappedDaemonRunnable(Class<? extends Runnable> runnableClass) {
            this.runnableClass = runnableClass;
        }

        @Override
        public WorkerDaemonResult execute(ParamSpec spec) {
            try {
                Runnable runnable = DirectInstantiator.instantiate(runnableClass, spec.getParams());
                runnable.run();
                return new WorkerDaemonResult(true, null);
            } catch (Throwable t) {
                return new WorkerDaemonResult(true, t);
            }
        }
    }
}
