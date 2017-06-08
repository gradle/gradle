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

import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;

import javax.inject.Inject;

public class WorkerDaemonServer extends WorkerServer {
    private final WorkerDirectoryProvider workerDirectoryProvider;

    @Inject
    WorkerDaemonServer(WorkerDirectoryProvider workerDirectoryProvider) {
        this.workerDirectoryProvider = workerDirectoryProvider;
    }

    @Override
    public DefaultWorkResult execute(ActionExecutionSpec spec) {
        ProcessEnvironment processEnvironment = NativeServices.getInstance().get(ProcessEnvironment.class);
        try {
            processEnvironment.maybeSetProcessDir(spec.getExecutionWorkingDir());
            return super.execute(spec);
        } catch (Throwable t) {
            return new DefaultWorkResult(true, t);
        } finally {
            processEnvironment.maybeSetProcessDir(workerDirectoryProvider.getIdleWorkingDirectory());
        }
    }

    @Override
    public String toString() {
        return "WorkerDaemonServer{}";
    }
}
