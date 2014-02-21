/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.rhino.worker.internal;

import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.Factory;
import org.gradle.plugins.javascript.rhino.worker.RhinoWorkerHandle;
import org.gradle.plugins.javascript.rhino.worker.RhinoWorkerHandleFactory;
import org.gradle.plugins.javascript.rhino.worker.RhinoWorkerSpec;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.WorkerProcess;
import org.gradle.process.internal.WorkerProcessBuilder;

import java.io.File;
import java.io.Serializable;

public class DefaultRhinoWorkerHandleFactory implements RhinoWorkerHandleFactory {

    private final Factory<WorkerProcessBuilder> workerProcessBuilderFactory;

    public DefaultRhinoWorkerHandleFactory(Factory<WorkerProcessBuilder> workerProcessBuilderFactory) {
        this.workerProcessBuilderFactory = workerProcessBuilderFactory;
    }

    public <R extends Serializable, P extends Serializable> RhinoWorkerHandle<R, P> create(Iterable<File> rhinoClasspath, RhinoWorkerSpec<R, P> workerSpec, LogLevel logLevel, Action<JavaExecSpec> javaExecSpecAction) {
        WorkerProcessBuilder builder = workerProcessBuilderFactory.create();
        builder.setBaseName("Gradle Rhino Worker");
        builder.setLogLevel(logLevel);
        builder.applicationClasspath(rhinoClasspath);
        builder.sharedPackages("org.mozilla.javascript");

        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        if (javaExecSpecAction != null) {
            javaExecSpecAction.execute(javaCommand);
        }

        WorkerProcess workerProcess = builder.worker(new RhinoServer<R, P>(workerSpec)).build();
        return new DefaultRhinoWorkerHandle<R, P>(workerSpec.getResultType(), workerProcess);
    }

}
