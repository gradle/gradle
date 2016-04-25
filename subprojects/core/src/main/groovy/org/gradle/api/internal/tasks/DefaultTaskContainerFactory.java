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
import org.gradle.internal.BiAction;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.collection.internal.BridgedCollections;
import org.gradle.model.internal.core.ChildNodeInitializerStrategyAccessors;
import org.gradle.model.internal.core.ModelMapModelProjection;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;

import static org.gradle.model.internal.core.NodePredicate.allLinks;

public class DefaultTaskContainerFactory implements Factory<TaskContainerInternal> {
    private static final ModelType<DefaultTaskContainer> DEFAULT_TASK_CONTAINER_MODEL_TYPE = ModelType.of(DefaultTaskContainer.class);
    private static final ModelType<TaskContainer> TASK_CONTAINER_MODEL_TYPE = ModelType.of(TaskContainer.class);
    private static final ModelType<Task> TASK_MODEL_TYPE = ModelType.of(Task.class);
    private static final ModelReference<Task> TASK_MODEL_REFERENCE = ModelReference.of(TASK_MODEL_TYPE);
    public static final SimpleModelRuleDescriptor COPY_TO_TASK_CONTAINER_DESCRIPTOR = new SimpleModelRuleDescriptor("copyToTaskContainer");
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

    public TaskContainerInternal create() {
        ModelReference<DefaultTaskContainer> containerReference = ModelReference.of(TaskContainerInternal.MODEL_PATH, DEFAULT_TASK_CONTAINER_MODEL_TYPE);

        ModelRegistrations.Builder registrationBuilder = BridgedCollections.registration(
            containerReference,
            new Transformer<DefaultTaskContainer, MutableModelNode>() {
                @Override
                public DefaultTaskContainer transform(MutableModelNode mutableModelNode) {
                    return instantiator.newInstance(DefaultTaskContainer.class, mutableModelNode, project, instantiator, taskFactory, projectAccessListener);
                }
            },
            new Task.Namer(),
            "Project.<init>.tasks()",
            new Namer()
        );

        modelRegistry.register(
            registrationBuilder
                .withProjection(ModelMapModelProjection.unmanaged(TASK_MODEL_TYPE, ChildNodeInitializerStrategyAccessors.of(NodeBackedModelMap.createUsingParentNode(new Transformer<NamedEntityInstantiator<Task>, MutableModelNode>() {
                    @Override
                    public NamedEntityInstantiator<Task> transform(MutableModelNode modelNode) {
                        return modelNode.getPrivateData(DEFAULT_TASK_CONTAINER_MODEL_TYPE).getEntityInstantiator();
                    }
                }))))
                .withProjection(UnmanagedModelProjection.of(TASK_CONTAINER_MODEL_TYPE))
                .build()
        );

        ModelNode modelNode = modelRegistry.atStateOrLater(TaskContainerInternal.MODEL_PATH, ModelNode.State.Created);

        // TODO LD use something more stable than a cast here
        MutableModelNode mutableModelNode = (MutableModelNode) modelNode;

        // Add tasks created through rules to the actual task container
        mutableModelNode.applyTo(allLinks(), ModelActionRole.Initialize, DirectNodeNoInputsModelAction.of(TASK_MODEL_REFERENCE, COPY_TO_TASK_CONTAINER_DESCRIPTOR, new BiAction<MutableModelNode, Task>() {
            @Override
            public void execute(MutableModelNode modelNode, Task task) {
                TaskContainerInternal taskContainer = modelNode.getParent().getPrivateData(TaskContainerInternal.MODEL_TYPE);
                taskContainer.add(task);
            }
        }));

        return mutableModelNode.getPrivateData(TaskContainerInternal.MODEL_TYPE);
    }

    private static class Namer implements Transformer<String, String> {
        public String transform(String s) {
            return "Project.<init>.tasks." + s + "()";
        }
    }

}
