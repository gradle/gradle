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
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.internal.Cast;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.project.taskfactory.OutputPropertyAnnotationUtil.validateDirectory;
import static org.gradle.api.internal.tasks.TaskOutputsUtil.ensureDirectoryExists;
import static org.gradle.internal.Cast.uncheckedCast;
import static org.gradle.util.GUtil.uncheckedCall;

@SuppressWarnings("deprecation")
public class OutputDirectoriesPropertyAnnotationHandler extends AbstractOutputPropertyAnnotationHandler {

    private static final String DEPRECATION_MESSAGE = String.format("Please use separate properties for each directory annotated with @%s, or reorganize output under a single output directory.",
        OutputDirectory.class.getSimpleName());

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return OutputDirectories.class;
    }

    @Override
    public boolean attachActions(TaskPropertyActionContext context) {
        DeprecationLogger.nagUserOfDiscontinuedAnnotation(OutputDirectories.class, DEPRECATION_MESSAGE);
        return super.attachActions(context);
    }

    @Override
    protected void validate(String propertyName, Object value, Collection<String> messages) {
        if (value != null) {
            for (File directory : Cast.<Iterable<File>>uncheckedCast(value)) {
                validateDirectory(propertyName, directory, messages);
            }
        }
    }

    @Override
    protected void update(final TaskPropertyActionContext context, TaskInternal task, final Callable<Object> futureValue) {
        task.getOutputs().configure(new Action<TaskOutputs>() {
            @Override
            public void execute(TaskOutputs taskOutputs) {
                Iterable<File> directories = uncheckedCast(uncheckedCall(futureValue));
                if (directories != null) {
                    int counter = 0;
                    for (File directory : directories) {
                        taskOutputs.dir(directory).withPropertyName(context.getName() + "$" + (++counter));
                    }
                }
            }
        });
        task.prependParallelSafeAction(new Action<Task>() {
            public void execute(Task task) {
                Iterable<File> directories = uncheckedCast(uncheckedCall(futureValue));
                if (directories != null) {
                    for (File directory : directories) {
                        ensureDirectoryExists(directory);
                    }
                }
            }
        });
    }
}
