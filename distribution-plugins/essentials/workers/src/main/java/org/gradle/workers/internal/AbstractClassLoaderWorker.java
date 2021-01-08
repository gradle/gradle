/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.internal.worker.RequestHandler;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import javax.annotation.Nullable;
import java.util.Collections;

import static org.gradle.internal.classloader.ClassLoaderUtils.executeInClassloader;

public abstract class AbstractClassLoaderWorker implements RequestHandler<TransportableActionExecutionSpec, DefaultWorkResult> {
    private final Worker worker;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;

    public AbstractClassLoaderWorker(ServiceRegistry workServices, ActionExecutionSpecFactory actionExecutionSpecFactory, InstantiatorFactory instantiatorFactory) {
        this.actionExecutionSpecFactory = actionExecutionSpecFactory;
        this.worker = new DefaultWorkerServer(workServices, instantiatorFactory, new IsolationScheme<>(Cast.uncheckedCast(WorkAction.class), WorkParameters.class, WorkParameters.None.class), Collections.emptyList());
    }

    public DefaultWorkResult executeInClassLoader(TransportableActionExecutionSpec spec, ClassLoader workerClassLoader) {
        return executeInClassloader(workerClassLoader, new Factory<DefaultWorkResult>() {
            @Nullable
            @Override
            public DefaultWorkResult create() {
                // Deserialize the class and parameters in the workerClassLoader (the context classloader)
                SimpleActionExecutionSpec<?> effectiveSpec = actionExecutionSpecFactory.newSimpleSpec(spec);
                return worker.execute(effectiveSpec);
            }
        });
    }
}
