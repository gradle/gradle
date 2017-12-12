/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;

public class DefaultTaskPropertyWalker implements TaskPropertyWalker {
    private final PropertyWalker propertyWalker;

    public DefaultTaskPropertyWalker(PropertyWalker propertyWalker) {
        this.propertyWalker = propertyWalker;
    }

    @Override
    public void visitProperties(final TaskInternal task, PropertyVisitor visitor) {
        final PropertySpecFactory specFactory = new DefaultPropertySpecFactory(task, ((ProjectInternal) task.getProject()).getFileResolver());
        propertyWalker.visitProperties(specFactory, visitor, task);
        task.getInputs().visitRegisteredProperties(visitor);
        task.getOutputs().visitRuntimeProperties(visitor);
        int destroyableCount = 0;
        for (Object path : ((TaskDestroyablesInternal) task.getDestroyables()).getRegisteredPaths()) {
            visitor.visitDestroyableProperty(new DefaultTaskDestroyablePropertySpec("$" + ++destroyableCount, path));
        }
        int localStateCount = 0;
        for (Object path : ((TaskLocalStateInternal) task.getLocalState()).getRegisteredPaths()) {
            visitor.visitLocalStateProperty(new DefaultTaskLocalStatePropertySpec("$" + ++localStateCount, path));
        }
    }
}
