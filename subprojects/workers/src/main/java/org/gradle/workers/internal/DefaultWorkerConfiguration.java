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

import org.gradle.api.ActionConfiguration;
import org.gradle.api.internal.DefaultActionConfiguration;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.workers.ForkMode;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;

public class DefaultWorkerConfiguration extends DefaultBaseWorkerSpec implements WorkerConfiguration {
    private final ActionConfiguration actionConfiguration = new DefaultActionConfiguration();

    public DefaultWorkerConfiguration(JavaForkOptionsFactory forkOptionsFactory) {
        super(forkOptionsFactory);
    }

    @Override
    public void params(Object... params) {
        actionConfiguration.params(params);
    }

    @Override
    public void setParams(Object... params) {
        actionConfiguration.setParams(params);
    }

    @Override
    public Object[] getParams() {
        return actionConfiguration.getParams();
    }

    @Override
    public ForkMode getForkMode() {
        switch (getIsolationMode()) {
            case AUTO:
                return ForkMode.AUTO;
            case NONE:
            case CLASSLOADER:
                return ForkMode.NEVER;
            case PROCESS:
                return ForkMode.ALWAYS;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void setForkMode(ForkMode forkMode) {
        switch (forkMode) {
            case AUTO:
                setIsolationMode(IsolationMode.AUTO);
                break;
            case NEVER:
                setIsolationMode(IsolationMode.CLASSLOADER);
                break;
            case ALWAYS:
                setIsolationMode(IsolationMode.PROCESS);
                break;
        }
    }

}
