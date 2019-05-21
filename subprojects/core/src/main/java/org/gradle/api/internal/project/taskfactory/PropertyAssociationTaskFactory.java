/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.api.internal.tasks.properties.InputFilePropertyType;
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.instantiation.InstantiationScheme;

import javax.annotation.Nullable;

public class PropertyAssociationTaskFactory implements ITaskFactory {
    private final ITaskFactory delegate;
    private final PropertyWalker propertyWalker;

    public PropertyAssociationTaskFactory(ITaskFactory delegate, PropertyWalker propertyWalker) {
        this.delegate = delegate;
        this.propertyWalker = propertyWalker;
    }

    @Override
    public ITaskFactory createChild(ProjectInternal project, InstantiationScheme instantiationScheme) {
        return new PropertyAssociationTaskFactory(delegate.createChild(project, instantiationScheme), propertyWalker);
    }

    @Override
    public <S extends Task> S create(TaskIdentity<S> taskIdentity, @Nullable Object[] constructorArgs) {
        final S task = delegate.create(taskIdentity, constructorArgs);
        TaskPropertyUtils.visitProperties(propertyWalker, (TaskInternal) task, new Listener(task));
        return task;
    }

    private static class Listener implements PropertyVisitor {
        private final Task task;

        public Listener(Task task) {
            this.task = task;
        }

        @Override
        public boolean visitOutputFilePropertiesOnly() {
            return true;
        }

        @Override
        public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, boolean incremental, @Nullable Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitInputProperty(String propertyName, PropertyValue value, boolean optional) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitDestroyableProperty(Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitLocalStateProperty(Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
            value.attachProducer(task);
        }
    }
}
