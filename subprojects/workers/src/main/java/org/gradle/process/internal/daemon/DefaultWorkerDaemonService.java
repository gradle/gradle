/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.daemon;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.process.daemon.WorkerDaemonExecutor;
import org.gradle.process.daemon.WorkerDaemonService;

public class DefaultWorkerDaemonService implements WorkerDaemonService {
    private final WorkerDaemonFactory workerDaemonFactory;
    private final FileResolver fileResolver;

    public DefaultWorkerDaemonService(WorkerDaemonFactory workerDaemonFactory, FileResolver fileResolver) {
        this.workerDaemonFactory = workerDaemonFactory;
        this.fileResolver = fileResolver;
    }

    @Override
    public WorkerDaemonExecutor daemonRunnable(Class<? extends Runnable> runnableClass) {
        return new WorkerDaemonRunnableExecutor(workerDaemonFactory, fileResolver, runnableClass, WorkerDaemonServer.class);
    }
}
