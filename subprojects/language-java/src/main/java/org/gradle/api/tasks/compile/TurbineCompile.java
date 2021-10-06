/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.tasks.compile;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;

public abstract class TurbineCompile extends AbstractCompile {
    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @Classpath
    public abstract ConfigurableFileCollection getTurbineClasspath();

    @TaskAction
    public void doCompilation() {
        WorkerExecutor executor = getWorkerExecutor();
        WorkQueue queue = executor.classLoaderIsolation(spec -> {
           spec.getClasspath().from(getTurbineClasspath());
        });

        queue.submit(TurbineCompileAction.class, turbineWorkParameters -> {
            turbineWorkParameters.getSources().from(getSource());
            turbineWorkParameters.getClasspath().from(getClasspath());
            turbineWorkParameters.getOutput().set(getDestinationDirectory());
        });
    }

    public interface TurbineWorkParameters extends WorkParameters {
        ConfigurableFileCollection getSources();
        ConfigurableFileCollection getClasspath();
        DirectoryProperty getOutput();
    }

    public static abstract class TurbineCompileAction implements WorkAction<TurbineWorkParameters> {
        @Override
        public void execute() {
            TurbineWorkParameters parameters = getParameters();

            File outputFile;
            try {
                outputFile = Files.createFile(new File(parameters.getOutput().getAsFile().get(), "ijar.jar").toPath()).toFile();
            } catch (IOException e) {
                throw new GradleException("Failed to create output", e);
            }

            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputFile.toPath()))) {
                pw.print("Sources:\n" + parameters.getSources().getFiles().toString());
                pw.println();
                pw.print("Classpath:\n" + parameters.getClasspath().getFiles().toString());
            } catch (Exception e) {
                throw new GradleException("Failed to create output", e);
            }
        }
    }
}
