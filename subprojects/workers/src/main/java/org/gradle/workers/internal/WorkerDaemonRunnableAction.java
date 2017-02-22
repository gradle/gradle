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

import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;

public class WorkerDaemonRunnableAction implements WorkerDaemonAction<ParamSpec> {
    private final String description;
    private final Class<? extends Runnable> runnableClass;

    WorkerDaemonRunnableAction(String description, Class<? extends Runnable> runnableClass) {
        this.description = description;
        this.runnableClass = runnableClass;
    }

    @Override
    public DefaultWorkResult execute(ParamSpec spec) {
        try {
            Runnable runnable = DirectInstantiator.instantiate(runnableClass, (Object[])spec.getParams());
            runnable.run();
            return new DefaultWorkResult(true, null);
        } catch (ObjectInstantiationException e) {
            return new DefaultWorkResult(true, e.getCause());
        } catch (Throwable t) {
            return new DefaultWorkResult(true, t);
        }
    }

    @Override
    public String getDescription() {
        return description;
    }
}
