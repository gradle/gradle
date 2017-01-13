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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.tasks.TaskOutputsUtil.ensureDirectoryExists;
import static org.gradle.api.internal.tasks.TaskOutputsUtil.validateDirectory;

@SuppressWarnings("deprecation")
public class OutputDirectoriesPropertyAnnotationHandler extends AbstractPluralOutputPropertyAnnotationHandler {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return OutputDirectories.class;
    }

    @Override
    protected TaskOutputFilePropertyBuilder createPropertyBuilder(TaskPropertyActionContext context, TaskInternal task, Callable<Object> futureValue) {
        return task.getOutputs().dirs(futureValue);
    }

    @Override
    protected void doValidate(String propertyName, File directory, Collection<String> messages) {
        validateDirectory(propertyName, directory, messages);
    }

    @Override
    protected void doEnsureExists(File directory) {
        ensureDirectoryExists(directory);
    }
}
