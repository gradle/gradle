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
import org.gradle.plugins.javascript.rhino.worker.RhinoWorkerHandleFactory;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.SingleUseWorkerProcessBuilder;
import org.gradle.process.internal.WorkerProcessFactory;

import java.io.File;

public class DefaultRhinoWorkerHandleFactory implements RhinoWorkerHandleFactory {

    private final WorkerProcessFactory workerProcessBuilderFactory;

    public DefaultRhinoWorkerHandleFactory(WorkerProcessFactory workerProcessBuilderFactory) {
        this.workerProcessBuilderFactory = workerProcessBuilderFactory;
    }

    @Override
    public <T> T create(Iterable<File> rhinoClasspath, Class<T> protocolType, Class<? extends T> workerImplementationType, LogLevel logLevel, Action<JavaExecSpec> javaExecSpecAction) {
        SingleUseWorkerProcessBuilder<T> builder = workerProcessBuilderFactory.create(protocolType, workerImplementationType);
        builder.setBaseName("Gradle Rhino Worker");
        builder.setLogLevel(logLevel);
        builder.applicationClasspath(rhinoClasspath);
        builder.sharedPackages("org.mozilla.javascript");

        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        if (javaExecSpecAction != null) {
            javaExecSpecAction.execute(javaCommand);
        }

        return builder.build();
    }
}
