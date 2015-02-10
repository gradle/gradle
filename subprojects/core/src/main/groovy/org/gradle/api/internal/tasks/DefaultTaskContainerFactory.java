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
package org.gradle.api.internal.tasks;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.collection.internal.BridgedCollections;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.NamedEntityInstantiator;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;

public class DefaultTaskContainerFactory implements Factory<TaskContainerInternal> {
    private final ModelRegistry modelRegistry;
    private final Instantiator instantiator;
    private final ITaskFactory taskFactory;
    private Project project;
    public ProjectAccessListener projectAccessListener;

    public DefaultTaskContainerFactory(ModelRegistry modelRegistry, Instantiator instantiator, ITaskFactory taskFactory, Project project, ProjectAccessListener projectAccessListener) {
        this.modelRegistry = modelRegistry;
        this.instantiator = instantiator;
        this.taskFactory = taskFactory;
        this.project = project;
        this.projectAccessListener = projectAccessListener;
    }

    ModelType<Task> taskModelType = ModelType.of(Task.class);

    public TaskContainerInternal create() {
        BridgedCollections.staticTypes(
                modelRegistry,
                TaskContainerInternal.MODEL_PATH,
                TaskContainerInternal.MODEL_TYPE,
                taskModelType,
                ModelType.of(TaskContainer.class),
                new Transformer<TaskContainerInternal, MutableModelNode>() {
                    @Override
                    public TaskContainerInternal transform(MutableModelNode mutableModelNode) {
                        ModelReference<NamedEntityInstantiator<Task>> instantiatorReference = BridgedCollections.instantiatorReference(TaskContainerInternal.MODEL_PATH, TaskContainerInternal.TASK_MODEL_TYPE);
                        return instantiator.newInstance(DefaultTaskContainer.class, mutableModelNode, instantiatorReference, project, instantiator, taskFactory, projectAccessListener);
                    }
                },
                new Task.Namer(),
                "Project.<init>.tasks()",
                new Namer()
        );

        ModelNode modelNode = modelRegistry.atStateOrLater(TaskContainerInternal.MODEL_PATH, ModelNode.State.Created);
        if (modelNode == null) {
            throw new IllegalStateException("Couldn't get task container from model registry");
        }

        // TODO LD use something more stable than a cast here
        MutableModelNode mutableModelNode = (MutableModelNode) modelNode;
        return mutableModelNode.getPrivateData(TaskContainerInternal.MODEL_TYPE);
    }

    private static class Namer implements Transformer<String, String> {
        public String transform(String s) {
            return "Project.<init>.tasks." + s + "()";
        }
    }

}
