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

import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.workers.WorkAction;

import javax.inject.Inject;

public class DefaultWorkerServer implements WorkerProtocol {
    private final ServiceRegistry parent;

    @Inject
    public DefaultWorkerServer(ServiceRegistry parent) {
        this.parent = parent;
    }

    @Override
    public DefaultWorkResult execute(ActionExecutionSpec spec) {
        try {
            Class<? extends WorkAction> implementationClass = Cast.uncheckedCast(spec.getImplementationClass());
            DefaultServiceRegistry serviceRegistry = new DefaultServiceRegistry(parent);
            Instantiator instantiator = parent.get(InstantiatorFactory.class).inject(serviceRegistry);
            if (spec.getParameters() != null) {
                serviceRegistry.add(spec.getParameters().getClass(), Cast.uncheckedCast(spec.getParameters()));
            }
            serviceRegistry.add(Instantiator.class, instantiator);
            WorkAction execution = instantiator.newInstance(implementationClass);
            execution.execute();
            if (execution instanceof ProvidesWorkResult) {
                return ((ProvidesWorkResult) execution).getWorkResult();
            } else {
                return DefaultWorkResult.SUCCESS;
            }
        } catch (Throwable t) {
            return new DefaultWorkResult(true, t);
        }
    }

    @Override
    public String toString() {
        return "DefaultWorkerServer{}";
    }
}
