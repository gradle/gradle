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

import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.util.concurrent.Callable;

public class DefaultWorkerServer implements WorkerProtocol {
    private final Instantiator instantiator;

    @Inject
    public DefaultWorkerServer(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public DefaultWorkResult execute(ActionExecutionSpec spec) {
        try {
            Class<?> implementationClass = spec.getImplementationClass();
            Object action = instantiator.newInstance(implementationClass, spec.getParams(implementationClass.getClassLoader()));
            if (action instanceof Runnable) {
                ((Runnable) action).run();
                return new DefaultWorkResult(true, null);
            } else if (action instanceof Callable) {
                Object result = ((Callable) action).call();
                if (result instanceof DefaultWorkResult) {
                    return (DefaultWorkResult) result;
                } else if (result instanceof WorkResult) {
                    return new DefaultWorkResult(((WorkResult) result).getDidWork(), null);
                } else {
                    throw new IllegalArgumentException("Worker actions must return a WorkResult.");
                }
            } else {
                throw new IllegalArgumentException("Worker actions must either implement Runnable or Callable<WorkResult>.");
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
