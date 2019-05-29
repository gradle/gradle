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

package org.gradle.api.internal.tasks;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Transformer;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.tasks.TaskValidationException;
import org.gradle.internal.reflect.ParameterValidationContext;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NonNullApi
public class TaskPropertyUtils {
    /**
     * Visits both properties declared via annotations on the properties of the task type as well as
     * properties declared via the runtime API ({@link org.gradle.api.tasks.TaskInputs} etc.).
     */
    public static void visitProperties(PropertyWalker propertyWalker, final TaskInternal task, final PropertyVisitor visitor) {
        StrictErrorsOnlyContext validationContext = new StrictErrorsOnlyContext(task);
        propertyWalker.visitProperties(task, validationContext, visitor);
        // Should instead forward these to the task's validation context
        validationContext.assertNoProblems();
        if (!visitor.visitOutputFilePropertiesOnly()) {
            task.getInputs().visitRegisteredProperties(visitor);
        }
        task.getOutputs().visitRegisteredProperties(visitor);
        if (visitor.visitOutputFilePropertiesOnly()) {
            return;
        }
        for (Object path : ((TaskDestroyablesInternal) task.getDestroyables()).getRegisteredPaths()) {
            visitor.visitDestroyableProperty(path);
        }
        for (Object path : ((TaskLocalStateInternal) task.getLocalState()).getRegisteredPaths()) {
            visitor.visitLocalStateProperty(path);
        }
    }

    /**
     * Checks if the given string can be used as a property name.
     *
     * @throws IllegalArgumentException if given name is an empty string.
     */
    public static String checkPropertyName(String propertyName) {
        if (propertyName.isEmpty()) {
            throw new IllegalArgumentException("Property name must not be empty string");
        }
        return propertyName;
    }

    private static class StrictErrorsOnlyContext implements ParameterValidationContext {
        private final TaskInternal task;
        List<String> problems;

        public StrictErrorsOnlyContext(TaskInternal task) {
            this.task = task;
        }

        void assertNoProblems() {
            if (problems == null) {
                return;
            }
            String message;
            if (problems.size() == 1) {
                message = String.format("A problem was found with the configuration of %s.", task);
            } else {
                Collections.sort(problems);
                message = String.format("Some problems were found with the configuration of %s.", task);
            }
            throw new TaskValidationException(message, CollectionUtils.collect(problems, new Transformer<InvalidUserDataException, String>() {
                @Override
                public InvalidUserDataException transform(String message) {
                    return new InvalidUserDataException(message);
                }
            }));
        }

        @Override
        public void visitError(@Nullable String ownerPath, String propertyName, String message) {
            // Ignore for now
        }

        @Override
        public void visitError(String message) {
            // Ignore for now
        }

        @Override
        public void visitErrorStrict(String message) {
            if (problems == null) {
                problems = new ArrayList<String>();
            }
            problems.add(message);
        }

        @Override
        public void visitErrorStrict(@Nullable String ownerPath, String propertyName, String message) {
            visitErrorStrict(message);
        }
    }
}
