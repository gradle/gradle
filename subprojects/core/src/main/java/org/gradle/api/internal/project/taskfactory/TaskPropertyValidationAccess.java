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

import org.gradle.api.Task;
import org.gradle.internal.Cast;

import java.util.Map;

/**
 * Class for easy access to task property validation from the validator task.
 */
public class TaskPropertyValidationAccess {
    @SuppressWarnings("unused")
    public static void collectTaskValidationProblems(Class<?> task, Map<String, Boolean> problems) {
        TaskClassInfoStore infoStore = new DefaultTaskClassInfoStore(new DefaultTaskClassValidatorExtractor(new ClasspathPropertyAnnotationHandler(), new CompileClasspathPropertyAnnotationHandler()));
        TaskClassInfo info = infoStore.getTaskClassInfo(Cast.<Class<? extends Task>>uncheckedCast(task));
        for (TaskClassValidationMessage validationMessage : info.getValidator().getValidationMessages()) {
            problems.put(String.format("Task type '%s': %s.", task.getName(), validationMessage), Boolean.FALSE);
        }
    }
}
