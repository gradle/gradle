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
package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.internal.Cast;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.tasks.TaskOutputsUtil.ensureParentDirectoryExists;
import static org.gradle.internal.Cast.uncheckedCast;
import static org.gradle.util.GUtil.uncheckedCall;

@SuppressWarnings("deprecation")
public class OutputFilesPropertyAnnotationHandler extends AbstractOutputFilePropertyAnnotationHandler {

    private static final String DEPRECATION_MESSAGE = String.format("Please use separate properties for each file annotated with @%s, or reorganize output files under a single output directory annotated with @%s.",
        OutputFile.class.getSimpleName(), OutputDirectory.class.getSimpleName());

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return OutputFiles.class;
    }

    @Override
    public boolean attachActions(TaskPropertyActionContext context) {
        DeprecationLogger.nagUserOfDiscontinuedAnnotation(OutputFiles.class, DEPRECATION_MESSAGE);
        return super.attachActions(context);
    }

    @Override
    protected void validate(String propertyName, Object value, Collection<String> messages) {
        if (value != null) {
            for (File file : Cast.<Iterable<File>>uncheckedCast(value)) {
                validateFile(propertyName, file, messages);
            }
        }
    }

    @Override
    protected void update(final TaskPropertyActionContext context, TaskInternal task, final Callable<Object> futureValue) {
        task.getOutputs().configure(new Action<TaskOutputs>() {
            @Override
            public void execute(TaskOutputs taskOutputs) {
                Iterable<File> files = uncheckedCast(uncheckedCall(futureValue));
                if (files != null) {
                    int counter = 0;
                    for (File file : files) {
                        taskOutputs.file(file).withPropertyName(context.getName() + "$" + (++counter));
                    }
                }
            }
        });
        task.prependParallelSafeAction(new Action<Task>() {
            public void execute(Task task) {
                Iterable<File> files = uncheckedCast(uncheckedCall(futureValue));
                if (files != null) {
                    for (File file : files) {
                        ensureParentDirectoryExists(file);
                    }
                }
            }
        });
    }
}
