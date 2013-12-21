/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.concurrent.Callable;

public class OutputDirectoryPropertyAnnotationHandler implements PropertyAnnotationHandler {

    private final Class<? extends Annotation> annotationType;
    private final Transformer<Iterable<File>, Object> valueTransformer;
    
    public OutputDirectoryPropertyAnnotationHandler(Class<? extends Annotation> annotationType, Transformer<Iterable<File>, Object> valueTransformer) {
        this.annotationType = annotationType;
        this.valueTransformer = valueTransformer;
    }

    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    private final ValidationAction outputDirValidation = new ValidationAction() {
        public void validate(String propertyName, Object value, Collection<String> messages) {
            for (File file : valueTransformer.transform(value)) {
                if (file.exists() && !file.isDirectory()) {
                    messages.add(String.format("Directory '%s' specified for property '%s' is not a directory.", file, propertyName));
                    return;
                }

                for (File candidate = file; candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        messages.add(String.format("Cannot write to directory '%s' specified for property '%s', as ancestor '%s' is not a directory.", file, propertyName, candidate));
                        break;
                    }
                }
            }
        }
    };
    
    public void attachActions(final PropertyActionContext context) {
        context.setValidationAction(outputDirValidation);
        context.setConfigureAction(new UpdateAction() {
            public void update(Task task, final Callable<Object> futureValue) {
                task.getOutputs().files(futureValue);
                task.doFirst(new Action<Task>() {
                    public void execute(Task task) {
                        Iterable<File> files;
                        try {
                            files = valueTransformer.transform(futureValue.call());
                        } catch (Exception e) {
                            throw UncheckedException.throwAsUncheckedException(e);
                        }
                        for (File file : files) {
                            file = GFileUtils.canonicalise(file);
                            GFileUtils.mkdirs(file);
                        }
                    }
                });
            }
        });
    }
}
