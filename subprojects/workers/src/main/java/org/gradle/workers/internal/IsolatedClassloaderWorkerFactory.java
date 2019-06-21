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

package org.gradle.workers.internal;

import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.workers.IsolationMode;

public class IsolatedClassloaderWorkerFactory implements WorkerFactory {
    private final BuildOperationExecutor buildOperationExecutor;
    private final ServiceRegistry serviceRegistry;
    private final ClassLoaderRegistry classLoaderRegistry;

    public IsolatedClassloaderWorkerFactory(BuildOperationExecutor buildOperationExecutor, ServiceRegistry parent, ClassLoaderRegistry classLoaderRegistry) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.serviceRegistry = parent;
        this.classLoaderRegistry = classLoaderRegistry;
    }

    @Override
    public BuildOperationAwareWorker getWorker(final DaemonForkOptions forkOptions) {
        return new AbstractWorker(buildOperationExecutor) {
            @Override
            public DefaultWorkResult execute(ActionExecutionSpec spec, BuildOperationRef parentBuildOperation) {
                return executeWrappedInBuildOperation(spec, parentBuildOperation, new Work() {
                    @Override
                    public DefaultWorkResult execute(ActionExecutionSpec spec) {
                        ClassLoader workerInfrastructureClassloader = classLoaderRegistry.getPluginsClassLoader();
                        return new IsolatedClassloaderWorker(forkOptions.getClassLoaderStructure(), workerInfrastructureClassloader, serviceRegistry).execute(spec);
                    }
                });
            }
        };
    }

    @Override
    public IsolationMode getIsolationMode() {
        return IsolationMode.CLASSLOADER;
    }
}
