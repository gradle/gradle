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

import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.workers.WorkAction;

import javax.inject.Inject;
import java.util.concurrent.Callable;

import static org.gradle.internal.classloader.ClassLoaderUtils.classFromContextLoader;

/**
 * This is used to bridge between the "old" worker api with untyped parameters and the typed
 * parameter api.  It allows us to maintain backwards compatibility at the api layer, but use
 * only typed parameters under the covers.  This can be removed once the old api is retired.
 */
public class AdapterWorkAction implements WorkAction<AdapterWorkParameters>, ProvidesWorkResult {
    private final AdapterWorkParameters parameters;
    private final Instantiator instantiator;
    private DefaultWorkResult workResult;

    @Inject
    public AdapterWorkAction(AdapterWorkParameters parameters, Instantiator instantiator) {
        this.parameters = parameters;
        this.instantiator = instantiator;
    }

    @Override
    public AdapterWorkParameters getParameters() {
        return parameters;
    }

    @Override
    public void execute() {
        AdapterWorkParameters parameters = getParameters();
        String implementationClassName = parameters.getImplementationClassName();
        Class<?> actionClass = classFromContextLoader(implementationClassName);

        Object action = instantiator.newInstance(actionClass, parameters.getParams());
        if (action instanceof Runnable) {
            ((Runnable) action).run();
            workResult = DefaultWorkResult.SUCCESS;
        } else if (action instanceof Callable) {
            Object result;
            try {
                result = ((Callable<?>) action).call();
                if (result instanceof DefaultWorkResult) {
                    workResult = (DefaultWorkResult) result;
                } else if (result instanceof WorkResult) {
                    workResult = new DefaultWorkResult(((WorkResult) result).getDidWork(), null);
                } else {
                    throw new IllegalArgumentException("Worker actions must return a WorkResult.");
                }
            } catch (Exception e) {
                workResult = new DefaultWorkResult(true, e);
            }
        } else {
            throw new IllegalArgumentException("Worker actions must either implement Runnable or Callable<WorkResult>.");
        }
    }

    @Override
    public DefaultWorkResult getWorkResult() {
        return workResult;
    }
}
