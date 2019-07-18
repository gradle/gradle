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

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.workers.WorkerParameters;
import org.gradle.workers.WorkerSpec;

import javax.inject.Inject;

public class DefaultWorkerSpec<T extends WorkerParameters> extends DefaultBaseWorkerSpec implements WorkerSpec<T> {
    private final T parameters;
    private final ConfigurableFileCollection classpath;

    @Inject
    public DefaultWorkerSpec(JavaForkOptionsFactory forkOptionsFactory, ObjectFactory objectFactory, T parameters) {
        super(forkOptionsFactory);

        // We pass a value here so that the instantiator doesn't choke on a null value.  It's tricky to
        // isolate/serialize a NONE parameter and there is no point to it since you can't do anything with
        // it anyways, so we just set it to null here.
        if (parameters == WorkerParameters.NONE) {
            this.parameters = null;
        } else {
            this.parameters = parameters;
        }

        this.classpath = objectFactory.fileCollection();
    }

    @Override
    public T getParameters() {
        return parameters;
    }

    @Override
    public void parameters(Action<T> action) {
        action.execute(parameters);
    }

    @Override
    public ConfigurableFileCollection getClasspath() {
        return classpath;
    }
}
