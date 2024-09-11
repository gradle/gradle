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

package org.gradle.internal.deployment;

import org.gradle.deployment.internal.Deployment;
import org.gradle.deployment.internal.DeploymentHandle;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleState;
import org.gradle.process.internal.JavaExecHandleBuilder;

import javax.inject.Inject;

public class JavaApplicationHandle implements DeploymentHandle {
    private final JavaExecHandleBuilder builder;
    private ExecHandle handle;

    @Inject
    public JavaApplicationHandle(JavaExecHandleBuilder builder) {
        this.builder = builder;
    }

    @Override
    public boolean isRunning() {
        return handle != null && handle.getState() == ExecHandleState.STARTED;
    }

    @Override
    public void start(Deployment deployment) {
        handle = builder.build().start();
    }

    @Override
    public void stop() {
        handle.abort();
    }
}
