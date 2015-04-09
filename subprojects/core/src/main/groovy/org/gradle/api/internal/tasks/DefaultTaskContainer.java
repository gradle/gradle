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

import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.*;
import org.gradle.api.internal.DynamicObject;
import org.gradle.api.internal.NamedDomainObjectContainerConfigureDelegate;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.BiAction;
import org.gradle.internal.Transformers;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraph;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;

import java.util.*;

public class DefaultTaskContainer extends DefaultTaskCollection<Task> implements TaskContainerInternal {
    private final MutableModelNode modelNode;
    private final ModelReference<NamedEntityInstantiator<Task>> instantiatorReference;
    private final ITaskFactory taskFactory;
    private final ProjectAccessListener projectAccessListener;
    private final Set<String> placeholders = Sets.newHashSet();
    private final NamedEntityInstantiator<Task> instantiator;

    public DefaultTaskContainer(MutableModelNode modelNode, ModelReference<NamedEntityInstantiator<Task>> instantiatorReference, ProjectInternal project, Instantiator instantiator, ITaskFactory taskFactory, ProjectAccessListener projectAccessListener) {
        super(Task.class, instantiator, project);
        this.modelNode = modelNode;
        this.instantiatorReference = instantiatorReference;
        this.taskFactory = taskFactory;
        this.projectAccessListener = projectAccessListener;
        this.instantiator = new TaskInstantiator(taskFactory);
    }

    public Task create(Map<String, ?> options) {
        Map<String, Object> mutableOptions = new HashMap<String, Object>(options);

        Object replaceStr = mutableOptions.remove(Task.TASK_OVERWRITE);
        boolean replace = replaceStr != null && "true".equals(replaceStr.toString());

        Task task = taskFactory.createTask(mutableOptions);
        String name = task.getName();

        if (placeholders.remove(name)) {
            modelNode.removeLink(name);
        }

        Task existing = findByNameWithoutRules(name);
        if (existing != null) {
            if (replace) {
                remove(existing);
            } else {
                throw new InvalidUserDataException(String.format(
                        "Cannot add %s as a task with that name already exists.", task));
            }
        }

        add(task);

        return task;
    }

    public <U extends Task> U maybeCreate(String name, Class<U> type) throws InvalidUserDataException {
        Task existing = findByName(name);
        if (existing != null) {
            return Transformers.cast(type).transform(existing);
        }
        return create(name, type);
    }

    public Task create(Map<String, ?> options, Closure configureClosure) throws InvalidUserDataException {
        return create(options).configure(configureClosure);
    }

    public <T extends Task> T create(String name, Class<T> type) {
        return type.cast(create(GUtil.map(Task.TASK_NAME, name, Task.TASK_TYPE, type)));
    }

    public Task create(String name) {
        return create(GUtil.map(Task.TASK_NAME, name));
    }

    public Task create(String name, Action<? super Task> configureAction) throws InvalidUserDataException {
        Task task = create(name);
        configureAction.execute(task);
        return task;
    }

    public Task maybeCreate(String name) {
        Task task = findByName(name);
        if (task != null) {
            return task;
        }
        return create(name);
    }

    public Task replace(String name) {
        return create(GUtil.map(Task.TASK_NAME, name, Task.TASK_OVERWRITE, true));
    }

    public Task create(String name, Closure configureClosure) {
        return create(name).configure(configureClosure);
    }

    public <T extends Task> T create(String name, Class<T> type, Action<? super T> configuration) throws InvalidUserDataException {
        T task = create(name, type);
        configuration.execute(task);
        return task;
    }

    public <T extends Task> T replace(String name, Class<T> type) {
        return type.cast(create(GUtil.map(Task.TASK_NAME, name, Task.TASK_TYPE, type, Task.TASK_OVERWRITE, true)));
    }

    public Task findByPath(String path) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        if (!path.contains(Project.PATH_SEPARATOR)) {
            return findByName(path);
        }

        String projectPath = StringUtils.substringBeforeLast(path, Project.PATH_SEPARATOR);
        ProjectInternal project = this.project.findProject(!GUtil.isTrue(projectPath) ? Project.PATH_SEPARATOR : projectPath);
        if (project == null) {
            return null;
        }
        projectAccessListener.beforeRequestingTaskByPath(project);

        return project.getTasks().findByName(StringUtils.substringAfterLast(path, Project.PATH_SEPARATOR));
    }

    public Task resolveTask(String path) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        return getByPath(path);
    }

    public Task getByPath(String path) throws UnknownTaskException {
        Task task = findByPath(path);
        if (task == null) {
            throw new UnknownTaskException(String.format("Task with path '%s' not found in %s.", path, project));
        }
        return task;
    }

    public TaskContainerInternal configure(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, new NamedDomainObjectContainerConfigureDelegate(configureClosure.getOwner(), this));
        return this;
    }

    @Override
    public NamedEntityInstantiator<Task> getEntityInstantiator() {
        return instantiator;
    }

    public DynamicObject getTasksAsDynamicObject() {
        return getElementsAsDynamicObject();
    }

    public SortedSet<String> getNames() {
        return Sets.newTreeSet(modelNode.getLinkNames(ModelType.of(Task.class)));
    }

    public void realize() {
        project.getModelRegistry().realizeNode(modelNode.getPath());

        new CachingDirectedGraphWalker<Task, Void>(new DirectedGraph<Task, Void>() {
            public void getNodeValues(Task node, Collection<? super Void> values, Collection<? super Task> connectedNodes) {
                connectedNodes.addAll(node.getTaskDependencies().getDependencies(node));
            }
        }).add(this).findValues();
    }

    @Override
    public void discoverTasks() {
        project.fireDeferredConfiguration();
        project.getModelRegistry().atStateOrLater(modelNode.getPath(), ModelNode.State.SelfClosed);
    }

    @Override
    public void maybeRealizeTask(String name) {
        if (modelNode.hasLink(name)) {
            realizeTask(MODEL_PATH.child(name));
        }
    }

    public Task findByName(String name) {
        Task task = super.findByName(name);
        if (task != null) {
            return task;
        }
        maybeMaterializePlaceholder(name);
        maybeRealizeTask(name);
        return super.findByName(name);
    }

    private void maybeMaterializePlaceholder(String name) {
        if (placeholders.remove(name)) {
            if (super.findByName(name) == null && modelNode.hasLink(name)) {
                realizeTask(MODEL_PATH.child(name));
            }
        }
    }

    private Task realizeTask(ModelPath taskPath) {
        return project.getModelRegistry().realize(taskPath, ModelType.of(Task.class));
    }

    public <T extends TaskInternal> void addPlaceholderAction(final String placeholderName, final Class<T> taskType, final Action<? super T> configure) {
        if (!modelNode.hasLink(placeholderName)) {
            final ModelType<T> taskModelType = ModelType.of(taskType);
            ModelPath path = MODEL_PATH.child(placeholderName);
            addPlaceholderModelLink(placeholderName, taskType, configure, taskModelType, path, instantiatorReference, modelNode);
        }
        if (findByNameWithoutRules(placeholderName) == null) {
            placeholders.add(placeholderName);
        }
    }

    private static <T extends TaskInternal> void addPlaceholderModelLink(final String placeholderName, final Class<T> taskType, final Action<? super T> configure, final ModelType<T> taskModelType, ModelPath path, final ModelReference<NamedEntityInstantiator<Task>> instantiatorReference, final MutableModelNode modelNode) {
        modelNode.addLink(
                ModelCreators
                        .of(ModelReference.of(path), new BiAction<MutableModelNode, List<ModelView<?>>>() {
                            @Override
                            public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> inputs) {
                                NamedEntityInstantiator<Task> instantiator = ModelViews.getInstance(inputs.get(0), instantiatorReference);
                                final T task = instantiator.create(placeholderName, taskType);
                                configure.execute(task);
                                DeprecationLogger.whileDisabled(new Runnable() {
                                    @Override
                                    public void run() {
                                        modelNode.getPrivateData(ModelType.of(TaskContainerInternal.class)).add(task);
                                    }
                                });
                                mutableModelNode.setPrivateData(taskModelType, task);
                            }
                        })
                        .inputs(instantiatorReference)
                        .withProjection(new UnmanagedModelProjection<T>(taskModelType, true, true))
                        .descriptor(new SimpleModelRuleDescriptor("tasks.addPlaceholderAction(" + placeholderName + ")"))
                        .build()
        );
    }

    public <U extends Task> NamedDomainObjectContainer<U> containerWithType(Class<U> type) {
        throw new UnsupportedOperationException();
    }

    public Set<? extends Class<? extends Task>> getCreateableTypes() {
        return Collections.singleton(getType());
    }

    private static class TaskInstantiator implements NamedEntityInstantiator<Task> {
        private final ITaskFactory taskFactory;

        public TaskInstantiator(ITaskFactory taskFactory) {
            this.taskFactory = taskFactory;
        }

        @Override
        public <S extends Task> S create(String name, Class<S> type) {
            if (type.isAssignableFrom(TaskInternal.class)) {
                return type.cast(taskFactory.create(name, TaskInternal.class));
            }
            return type.cast(taskFactory.create(name, type.asSubclass(TaskInternal.class)));
        }
    }
}