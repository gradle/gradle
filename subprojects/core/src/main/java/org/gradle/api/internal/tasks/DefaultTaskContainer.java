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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.NamedDomainObjectContainerConfigureDelegate;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskReference;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.Cast;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

@NonNullApi
public class DefaultTaskContainer extends DefaultTaskCollection<Task> implements TaskContainerInternal {
    private static final Object[] NO_ARGS = new Object[0];
    private final static String EAGERLY_CREATE_LAZY_TASKS_PROPERTY = "org.gradle.internal.tasks.eager";

    private static final Set<String> VALID_TASK_ARGUMENTS = ImmutableSet.of(
        Task.TASK_ACTION, Task.TASK_DEPENDS_ON, Task.TASK_DESCRIPTION, Task.TASK_GROUP, Task.TASK_NAME, Task.TASK_OVERWRITE, Task.TASK_TYPE, Task.TASK_CONSTRUCTOR_ARGS
    );
    private static final Set<String> MANDATORY_TASK_ARGUMENTS = ImmutableSet.of(
        Task.TASK_NAME, Task.TASK_TYPE
    );

    private final MutableModelNode modelNode;
    private final ITaskFactory taskFactory;
    private final ProjectAccessListener projectAccessListener;
    private final Set<String> placeholders = Sets.newHashSet();
    private final Object lock = new Object();

    private final TaskStatistics statistics;
    private final boolean eagerlyCreateLazyTasks;

    public DefaultTaskContainer(MutableModelNode modelNode, ProjectInternal project, Instantiator instantiator, ITaskFactory taskFactory, ProjectAccessListener projectAccessListener, TaskStatistics statistics) {
        super(Task.class, instantiator, project);
        this.modelNode = modelNode;
        this.taskFactory = taskFactory;
        this.projectAccessListener = projectAccessListener;
        this.statistics = statistics;
        this.eagerlyCreateLazyTasks = Boolean.getBoolean(EAGERLY_CREATE_LAZY_TASKS_PROPERTY);
    }

    public Task create(Map<String, ?> options) {
        Map<String, ?> factoryOptions = options;
        boolean replace = false;
        if (options.containsKey(Task.TASK_OVERWRITE)) {
            factoryOptions = new HashMap<String, Object>(options);
            Object replaceStr = factoryOptions.remove(Task.TASK_OVERWRITE);
            replace = "true".equals(replaceStr.toString());
        }

        Map<String, ?> actualArgs = checkTaskArgsAndCreateDefaultValues(factoryOptions);

        String name = actualArgs.get(Task.TASK_NAME).toString();
        if (!GUtil.isTrue(name)) {
            throw new InvalidUserDataException("The task name must be provided.");
        }

        Class<? extends TaskInternal> type = Cast.uncheckedCast(actualArgs.get(Task.TASK_TYPE));
        Object[] constructorArgs = getConstructorArgs(actualArgs);
        TaskInternal task = createTask(name, type, constructorArgs);
        statistics.eagerTask(type);

        Object dependsOnTasks = actualArgs.get(Task.TASK_DEPENDS_ON);
        if (dependsOnTasks != null) {
            task.dependsOn(dependsOnTasks);
        }
        Object description = actualArgs.get(Task.TASK_DESCRIPTION);
        if (description != null) {
            task.setDescription(description.toString());
        }
        Object group = actualArgs.get(Task.TASK_GROUP);
        if (group != null) {
            task.setGroup(group.toString());
        }
        Object action = actualArgs.get(Task.TASK_ACTION);
        if (action instanceof Action) {
            Action<? super Task> taskAction = Cast.uncheckedCast(action);
            task.doFirst(taskAction);
        } else if (action != null) {
            Closure closure = (Closure) action;
            task.doFirst(closure);
        }

        return addTask(task, replace);
    }

    private static Object[] getConstructorArgs(Map<String, ?> args) {
        Object constructorArgs = args.get(Task.TASK_CONSTRUCTOR_ARGS);
        if (constructorArgs instanceof List) {
            List<?> asList = (List<?>) constructorArgs;
            return asList.toArray(new Object[asList.size()]);
        }
        if (constructorArgs instanceof Object[]) {
            return (Object[]) constructorArgs;
        }
        if (constructorArgs != null) {
            throw new IllegalArgumentException(String.format("%s must be a List or Object[].  Received %s", Task.TASK_CONSTRUCTOR_ARGS, constructorArgs.getClass()));
        }
        return NO_ARGS;
    }

    private static Map<String, ?> checkTaskArgsAndCreateDefaultValues(Map<String, ?> args) {
        validateArgs(args);
        if (!args.keySet().containsAll(MANDATORY_TASK_ARGUMENTS)) {
            Map<String, Object> argsWithDefaults = Maps.newHashMap(args);
            setIfNull(argsWithDefaults, Task.TASK_NAME, "");
            setIfNull(argsWithDefaults, Task.TASK_TYPE, DefaultTask.class);
            return argsWithDefaults;
        }
        return args;
    }

    private static void validateArgs(Map<String, ?> args) {
        if (!VALID_TASK_ARGUMENTS.containsAll(args.keySet())) {
            Map<String, Object> unknownArguments = new HashMap<String, Object>(args);
            unknownArguments.keySet().removeAll(VALID_TASK_ARGUMENTS);
            throw new InvalidUserDataException(String.format("Could not create task '%s': Unknown argument(s) in task definition: %s",
                args.get(Task.TASK_NAME), unknownArguments.keySet()));
        }
    }

    private static void setIfNull(Map<String, Object> map, String key, Object defaultValue) {
        if (map.get(key) == null) {
            map.put(key, defaultValue);
        }
    }

    private <T extends Task> T addTask(T task, boolean replaceExisting) {
        String name = task.getName();

        if (placeholders.remove(name)) {
            modelNode.removeLink(name);
        }

        if (replaceExisting) {
            Task existing = findByNameWithoutRules(name);
            if (existing != null) {
                remove(existing);
            }
        } else if (hasWithName(name)) {
            duplicateTask(name);
        }

        add(task);

        return task;
    }

    private <T extends Task> T duplicateTask(String task) {
        throw new InvalidUserDataException(String.format("Cannot add task '%s' as a task with that name already exists.", task));
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

    @Override
    public <T extends Task> T create(String name, Class<T> type) {
        return create(name, type, NO_ARGS);
    }

    @Override
    public <T extends Task> T create(String name, Class<T> type, Object... constructorArgs) throws InvalidUserDataException {
        T task = createTask(name, type, constructorArgs);
        statistics.eagerTask(type);
        return addTask(task, false);
    }

    private <T extends Task> T createTask(String name, Class<T> type, Object... constructorArgs) throws InvalidUserDataException {
        for (int i = 0; i < constructorArgs.length; i++) {
            if (constructorArgs[i] == null) {
                throw new NullPointerException(String.format("Received null for %s constructor argument #%s", type.getName(), i + 1));
            }
        }
        return taskFactory.create(name, type, constructorArgs);
    }

    public Task create(String name) {
        return create(name, DefaultTask.class);
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
        return replace(name, DefaultTask.class);
    }

    public Task create(String name, Closure configureClosure) {
        return create(name).configure(configureClosure);
    }

    public <T extends Task> T create(String name, Class<T> type, Action<? super T> configuration) throws InvalidUserDataException {
        T task = create(name, type);
        configuration.execute(task);
        return task;
    }

    @Override
    public Provider<Task> createLater(String name, Action<? super Task> configurationAction) {
        return Cast.uncheckedCast(createLater(name, DefaultTask.class, configurationAction));
    }

    @Override
    public <T extends Task> Provider<T> createLater(final String name, final Class<T> type, Action<? super T> configurationAction) {
        if (hasWithName(name)) {
            duplicateTask(name);
        }
        TaskProvider<T> provider = new TaskCreatingProvider<T>(type, name, configurationAction);
        addLater(provider);
        if (eagerlyCreateLazyTasks) {
            provider.get();
        }
        return provider;
    }

    public <T extends Task> T replace(String name, Class<T> type) {
        T task = taskFactory.create(name, type);
        return addTask(task, true);
    }

    public Task findByPath(String path) {
        if (Strings.isNullOrEmpty(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        if (!path.contains(Project.PATH_SEPARATOR)) {
            return findByName(path);
        }

        String projectPath = StringUtils.substringBeforeLast(path, Project.PATH_SEPARATOR);
        ProjectInternal project = this.project.findProject(Strings.isNullOrEmpty(projectPath) ? Project.PATH_SEPARATOR : projectPath);
        if (project == null) {
            return null;
        }
        projectAccessListener.beforeRequestingTaskByPath(project);

        return project.getTasks().findByName(StringUtils.substringAfterLast(path, Project.PATH_SEPARATOR));
    }

    @Override
    public <T extends Task> Provider<T> getByNameLater(Class<T> type, String name) throws InvalidUserDataException {
        return new TaskLookupProvider<T>(type, name);
    }

    public Task resolveTask(String path) {
        if (Strings.isNullOrEmpty(path)) {
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
        return taskFactory;
    }

    public DynamicObject getTasksAsDynamicObject() {
        return getElementsAsDynamicObject();
    }

    @Override
    public SortedSet<String> getNames() {
        SortedSet<String> names = super.getNames();
        if (placeholders.isEmpty()) {
            return names;
        }
        TreeSet<String> allNames = new TreeSet<String>(names);
        allNames.addAll(placeholders);
        return allNames;
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

    public <T extends Task> void addPlaceholderAction(final String placeholderName, final Class<T> taskType, final Action<? super T> configure) {
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

    private static class TaskCreator<T extends Task> implements Action<MutableModelNode> {
        private final String placeholderName;
        private final Class<T> taskType;
        private final Action<? super T> configure;
        private final ModelType<T> taskModelType;

        TaskCreator(String placeholderName, Class<T> taskType, Action<? super T> configure, ModelType<T> taskModelType) {
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

    private abstract class TaskProvider<T extends Task> extends AbstractProvider<T> implements Named {
        final Class<T> type;
        final String name;

        TaskProvider(Class<T> type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Class<T> getType() {
            return type;
        }

        @Override
        public boolean isPresent() {
            return findByNameWithoutRules(name) != null;
        }
    }

    private class TaskCreatingProvider<T extends Task> extends TaskProvider<T> {
        Action<? super T> configureAction;
        T task;

        public TaskCreatingProvider(Class<T> type, String name, Action<? super T> configureAction) {
            super(type, name);
            this.configureAction = configureAction;
            statistics.lazyTask();
        }

        @Override
        public T getOrNull() {
            if (task == null) {
                // We synchronize this to prevent multiple threads from attempting to create a task concurrently
                synchronized (lock) {
                    task = type.cast(findByNameWithoutRules(name));
                    if (task == null) {
                        task = createTask(name, type, NO_ARGS);
                        statistics.lazyTaskRealized();
                        add(task);
                        configureAction.execute(task);
                        configureAction = null;
                    }
                }
            }
            return task;
        }
    }

    private class TaskLookupProvider<T extends Task> extends TaskProvider<T> {
        T task;

        public TaskLookupProvider(Class<T> type, String name) {
            super(type, name);
        }

        @Override
        public T getOrNull() {
            if (task == null) {
                // We synchronize this to prevent multiple threads from attempting to create a task concurrently
                synchronized (lock) {
                    task = type.cast(getByName(name));
                }
            }
            return task;
        }
    }
}
