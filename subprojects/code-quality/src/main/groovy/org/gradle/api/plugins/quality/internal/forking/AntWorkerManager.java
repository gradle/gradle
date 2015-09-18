/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.plugins.quality.internal.forking;

import org.gradle.internal.Factory;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.WorkerProcess;
import org.gradle.process.internal.WorkerProcessBuilder;

public class AntWorkerManager {

    public AntResult runAntTask(Factory<WorkerProcessBuilder> workerFactory, AntWorkerSpec spec) {
        WorkerProcess process = createWorkerProcess(workerFactory, spec);
        process.start();

        AntWorkerClient antWorkerClient = new AntWorkerClient();
        process.getConnection().addIncoming(AntWorkerClientProtocol.class, antWorkerClient);
        process.getConnection().connect();

        process.waitForStop();

        return antWorkerClient.getResult();
    }

    private WorkerProcess createWorkerProcess(Factory<WorkerProcessBuilder> workerFactory, AntWorkerSpec spec) {
        WorkerProcessBuilder builder = workerFactory.create();
        builder.setBaseName("Gradle Ant Worker");
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setMaxHeapSize(spec.getMaxHeapSize()); //TODO: Make this something reasonable / configurable
        javaCommand.setWorkingDir(spec.getWorkingDir());
        return builder.worker(new AntWorkerServer(spec)).build();
    }
}
