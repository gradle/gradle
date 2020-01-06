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

import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.ClassLoaderWorkerSpec;
import org.gradle.workers.ProcessWorkerSpec;

import javax.inject.Inject;

public class DefaultProcessWorkerSpec extends DefaultClassLoaderWorkerSpec implements ProcessWorkerSpec, ClassLoaderWorkerSpec {
    protected final JavaForkOptions forkOptions;

    @Inject
    public DefaultProcessWorkerSpec(JavaForkOptions forkOptions, ObjectFactory objectFactory) {
        super(objectFactory);
        this.forkOptions = forkOptions;
        this.forkOptions.setEnvironment(Maps.newHashMap());
    }

    @Override
    public JavaForkOptions getForkOptions() {
        return forkOptions;
    }

    @Override
    public void forkOptions(Action<? super JavaForkOptions> forkOptionsAction) {
        forkOptionsAction.execute(forkOptions);
    }
}
