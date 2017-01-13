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
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.NamedDomainObjectContainerConfigureDelegate;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskReference;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.Transformers;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelRegistrations;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.NamedEntityInstantiator;
import org.gradle.model.internal.core.UnmanagedModelProjection;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

public class DefaultTaskContainer extends DefaultTaskCollection<Task> implements TaskContainerInternal {
    private final MutableModelNode modelNode;
    private final ITaskFactory taskFactory;
    private final ProjectAccessListener projectAccessListener;
    private final Set<String> placeholders = Sets.newHashSet();
    private final NamedEntityInstantiator<Task> instantiator;

    public DefaultTaskContainer(MutableModelNode modelNode, ProjectInternal project, Instantiator instantiator, ITaskFactory taskFactory, ProjectAccessListener projectAccessListener) {
        super(Task.class, instantiator, project);
        this.modelNode = modelNode;
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

    @Override
    public Task resolveTask(TaskReference reference) {
        for (TaskReferenceResolver taskResolver : project.getServices().getAll(TaskReferenceResolver.class)) {
            Task constructed = taskResolver.constructTask(reference, this);
            if (constructed != null) {
                return constructed;
            }
        }

        throw new UnknownTaskException(String.format("Task reference '%s' could not be resolved in %s.", reference.getName(), project));
    }

    public Task getByPath(String path) throws UnknownTaskException {
        Task task = findByPath(path);
        if (task == null) {
            throw new UnknownTaskException(String.format("Task with path '%s' not found in %s.", path, project));
        }
        return task;
    }

    public TaskContainerInternal configure(Closure configureClosure) {
        return ConfigureUtil.configureSelf(configureClosure, this, new NamedDomainObjectContainerConfigureDelegate(configureClosure, this));
    }

    @Override
    public NamedEntityInstantiator<Task> getEntityInstantiator() {
        return instantiator;
    }

    public DynamicObject getTasksAsDynamicObject() {
        return getElementsAsDynamicObject();
    }

    public SortedSet<String> getNames() {
        return Sets.newTreeSet(modelNode.getLinkNames());
    }

    public void realize() {
        project.getModelRegistry().realizeNode(modelNode.getPath());
    }

    @Override
    public void discoverTasks() {
        project.fireDeferredConfiguration();
        project.getModelRegistry().atStateOrLater(modelNode.getPath(), ModelNode.State.SelfClosed);
    }

    @Override
    public void prepareForExecution(Task task) {
        assert task.getProject() == project;
        if (modelNode.hasLink(task.getName())) {
            realizeTask(MODEL_PATH.child(task.getName()), ModelNode.State.GraphClosed);
        }
    }

    /**
     * @return true if this method _may_ have done some work.
     */
    private boolean maybeCreateTasks(String name) {
        if (modelNode.hasLink(name)) {
            realizeTask(MODEL_PATH.child(name), ModelNode.State.Initialized);
            return true;
        }
        return false;
    }

    public Task findByName(String name) {
        Task task = super.findByName(name);
        if (task != null) {
            return task;
        }
        if (!maybeCreateTasks(name)) {
            return null;
        }
        placeholders.remove(name);
        return super.findByNameWithoutRules(name);
    }

    private Task realizeTask(ModelPath taskPath, ModelNode.State minState) {
        return project.getModelRegistry().atStateOrLater(taskPath, ModelType.of(Task.class), minState);
    }

    public <T extends TaskInternal> void addPlaceholderAction(final String placeholderName, final Class<T> taskType, final Action<? super T> configure) {
        if (!modelNode.hasLink(placeholderName)) {
            final ModelType<T> taskModelType = ModelType.of(taskType);
            ModelPath path = MODEL_PATH.child(placeholderName);
            modelNode.addLink(
                ModelRegistrations.of(path)
                    .action(ModelActionRole.Create, new TaskCreator<T>(placeholderName, taskType, configure, taskModelType))
                    .withProjection(new UnmanagedModelProjection<T>(taskModelType))
                    .descriptor(new SimpleModelRuleDescriptor("tasks.addPlaceholderAction(" + placeholderName + ")"))
                    .build()
            );
        }
        if (findByNameWithoutRules(placeholderName) == null) {
            placeholders.add(placeholderName);
        }
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

    private static class TaskCreator<T extends TaskInternal> implements Action<MutableModelNode> {
        private final String placeholderName;
        private final Class<T> taskType;
        private final Action<? super T> configure;
        private final ModelType<T> taskModelType;

        public TaskCreator(String placeholderName, Class<T> taskType, Action<? super T> configure, ModelType<T> taskModelType) {
            this.placeholderName = placeholderName;
            this.taskType = taskType;
            this.configure = configure;
            this.taskModelType = taskModelType;
        }

        @Override
        public void execute(final MutableModelNode mutableModelNode) {
            DefaultTaskContainer taskContainer = mutableModelNode.getParent().getPrivateData(ModelType.of(DefaultTaskContainer.class));
            T task = taskContainer.taskFactory.create(placeholderName, taskType);
            configure.execute(task);
            taskContainer.add(task);
            mutableModelNode.setPrivateData(taskModelType, task);
        }
    }

    @Override
    public <S extends Task> TaskCollection<S> withType(Class<S> type) {
        return new RealizableTaskCollection<S>(type, super.withType(type), modelNode);
    }
}
