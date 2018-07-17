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
import groovy.lang.Closure;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.NamedDomainObjectContainerConfigureDelegate;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.Cast;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.Transformers;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.NamedEntityInstantiator;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

@NonNullApi
public class DefaultTaskContainer extends DefaultTaskCollection<Task> implements TaskContainerInternal {
    private static final Object[] NO_ARGS = new Object[0];
    public final static String EAGERLY_CREATE_LAZY_TASKS_PROPERTY = "org.gradle.internal.tasks.eager";

    private static final Set<String> VALID_TASK_ARGUMENTS = ImmutableSet.of(
        Task.TASK_ACTION, Task.TASK_DEPENDS_ON, Task.TASK_DESCRIPTION, Task.TASK_GROUP, Task.TASK_NAME, Task.TASK_OVERWRITE, Task.TASK_TYPE, Task.TASK_CONSTRUCTOR_ARGS
    );
    private static final Set<String> MANDATORY_TASK_ARGUMENTS = ImmutableSet.of(
        Task.TASK_NAME, Task.TASK_TYPE
    );

    private final ITaskFactory taskFactory;
    private final ProjectAccessListener projectAccessListener;
    private final BuildOperationExecutor buildOperationExecutor;

    private final TaskStatistics statistics;
    private final boolean eagerlyCreateLazyTasks;
    private final Map<String, TaskProvider<? extends Task>> placeholders = Maps.newLinkedHashMap();

    private MutableModelNode modelNode;

    public DefaultTaskContainer(ProjectInternal project, Instantiator instantiator, ITaskFactory taskFactory, ProjectAccessListener projectAccessListener, TaskStatistics statistics, BuildOperationExecutor buildOperationExecutor) {
        super(Task.class, instantiator, project);
        this.taskFactory = taskFactory;
        this.projectAccessListener = projectAccessListener;
        this.statistics = statistics;
        this.eagerlyCreateLazyTasks = Boolean.getBoolean(EAGERLY_CREATE_LAZY_TASKS_PROPERTY);
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public Task create(Map<String, ?> options) {
        Map<String, ?> factoryOptions = options;
        final boolean replace;
        if (options.containsKey(Task.TASK_OVERWRITE)) {
            factoryOptions = new HashMap<String, Object>(options);
            Object replaceStr = factoryOptions.remove(Task.TASK_OVERWRITE);
            replace = "true".equals(replaceStr.toString());
        } else {
            replace = false;
        }

        final Map<String, ?> actualArgs = checkTaskArgsAndCreateDefaultValues(factoryOptions);

        final String name = actualArgs.get(Task.TASK_NAME).toString();
        if (!GUtil.isTrue(name)) {
            throw new InvalidUserDataException("The task name must be provided.");
        }

        final Class<? extends TaskInternal> type = Cast.uncheckedCast(actualArgs.get(Task.TASK_TYPE));

        final TaskIdentity<? extends TaskInternal> identity = TaskIdentity.create(name, type, project);
        return buildOperationExecutor.call(new CallableBuildOperation<Task>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return realizeDescriptor(identity, replace, true);
            }

            @Override
            public Task call(BuildOperationContext context) {
                Object[] constructorArgs = getConstructorArgs(actualArgs);
                TaskInternal task = createTask(identity, constructorArgs);
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

                addTask(task, replace);
                context.setResult(REALIZE_RESULT);
                return task;
            }
        });
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

    private <T extends Task> void addTask(T task, boolean replaceExisting) {
        String name = task.getName();

        DefaultTaskProvider<? extends Task> placeholderProvider = (DefaultTaskProvider) placeholders.remove(name);
        if (placeholderProvider != null) {
            placeholderProvider.removed = true;
            if (!replaceExisting) {
                if (modelNode != null) {
                    modelNode.removeLink(name);
                }
                warnAboutPlaceholderDeprecation(name);
            }
        }

        if (replaceExisting) {
            Task existing = findByNameWithoutRules(name);
            if (existing != null) {
                remove(existing);
            } else {
                DefaultTaskProvider<? extends Task> taskProvider = (DefaultTaskProvider) findByNameLaterWithoutRules(name);
                if (taskProvider != null) {
                    taskProvider.removed = true;
                }
            }
        } else if (hasWithName(name)) {
            duplicateTask(name);
        }

        add(task);
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
    public <T extends Task> T create(final String name, final Class<T> type, final Object... constructorArgs) throws InvalidUserDataException {
        final TaskIdentity<T> identity = TaskIdentity.create(name, type, project);
        return buildOperationExecutor.call(new CallableBuildOperation<T>() {
            @Override
            public T call(BuildOperationContext context) {
                T task = createTask(identity, constructorArgs);
                statistics.eagerTask(type);
                addTask(task, false);
                context.setResult(REALIZE_RESULT);
                return task;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return realizeDescriptor(identity, false, true);
            }
        });
    }

    private <T extends Task> T createTask(TaskIdentity<T> identity, Object... constructorArgs) throws InvalidUserDataException {
        for (int i = 0; i < constructorArgs.length; i++) {
            if (constructorArgs[i] == null) {
                throw new NullPointerException(String.format("Received null for %s constructor argument #%s", identity.type.getName(), i + 1));
            }
        }
        return taskFactory.create(identity, constructorArgs);
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
    public TaskProvider<Task> register(String name, Action<? super Task> configurationAction) throws InvalidUserDataException {
        return Cast.uncheckedCast(register(name, DefaultTask.class, configurationAction));
    }

    @Override
    public <T extends Task> TaskProvider<T> register(String name, Class<T> type, Action<? super T> configurationAction) throws InvalidUserDataException {
        return registerTask(name, type, configurationAction, NO_ARGS);
    }

    @Override
    public <T extends Task> TaskProvider<T> register(String name, Class<T> type) throws InvalidUserDataException {
        return register(name, type, NO_ARGS);
    }

    @Override
    public TaskProvider<Task> register(String name) throws InvalidUserDataException {
        return Cast.uncheckedCast(register(name, DefaultTask.class));
    }

    @Override
    public <T extends Task> TaskProvider<T> register(String name, Class<T> type, Object... constructorArgs) {
        return registerTask(name, type, null, constructorArgs);
    }

    private <T extends Task> TaskProvider<T> registerTask(final String name, final Class<T> type, @Nullable final Action<? super T> configurationAction, final Object... constructorArgs) {
        if (hasWithName(name)) {
            duplicateTask(name);
        }
        final TaskIdentity<T> identity = TaskIdentity.create(name, type, project);
        TaskProvider<T> provider = buildOperationExecutor.call(new CallableBuildOperation<TaskProvider<T>>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return registerDescriptor(identity);
            }

            @Override
            public TaskProvider<T> call(BuildOperationContext context) {
                DefaultTaskProvider<T> provider = Cast.uncheckedCast(getInstantiator()
                    .newInstance(TaskCreatingProvider.class, DefaultTaskContainer.this, identity, configurationAction, constructorArgs)
                );
                addLater(provider);
                context.setResult(REGISTER_RESULT);
                return provider;
            }
        });

        if (eagerlyCreateLazyTasks) {
            provider.get();
        }

        return provider;
    }

    public <T extends Task> T replace(final String name, final Class<T> type) {
        final TaskIdentity<T> identity = TaskIdentity.create(name, type, project);
        return buildOperationExecutor.call(new CallableBuildOperation<T>() {
            @Override
            public T call(BuildOperationContext context) {
                T task = taskFactory.create(identity);
                addTask(task, true);
                context.setResult(REALIZE_RESULT);
                return task;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return realizeDescriptor(identity, true, true);
            }
        });
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

    public Task resolveTask(String path) {
        if (Strings.isNullOrEmpty(path)) {
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
        if (placeholders.isEmpty() && modelNode == null) {
            return names;
        }
        TreeSet<String> allNames = new TreeSet<String>(names);
        allNames.addAll(placeholders.keySet());
        if (modelNode != null) {
            allNames.addAll(modelNode.getLinkNames());
        }
        return allNames;
    }

    public void realize() {
        flushPlaceholders();
        if (modelNode != null) {
            project.getModelRegistry().realizeNode(modelNode.getPath());
        }
    }

    @Override
    public void discoverTasks() {
        project.fireDeferredConfiguration();
        if (modelNode != null) {
            project.getModelRegistry().atStateOrLater(modelNode.getPath(), ModelNode.State.SelfClosed);
        }
    }

    private void flushPlaceholders() {
        // @formatter:off
        for (Iterator<TaskProvider<?>> iterator = placeholders.values().iterator(); iterator.hasNext();) {
            // @formatter:on
            iterator.next().get();
            iterator.remove();
        }
    }

    @Override
    public void prepareForExecution(Task task) {
        assert task.getProject() == project;
        if (modelNode != null && modelNode.hasLink(task.getName())) {
            realizeTask(MODEL_PATH.child(task.getName()), ModelNode.State.GraphClosed);
        }
    }

    /**
     * @return true if this method _may_ have done some work.
     */
    private boolean maybeCreateTasks(String name) {
        TaskProvider<?> placeholder = placeholders.remove(name);
        if (placeholder != null) {
            placeholder.get();
            return true;
        }
        if (modelNode != null && modelNode.hasLink(name)) {
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
        if (findByNameWithoutRules(placeholderName) == null) {
            final TaskIdentity<T> identity = TaskIdentity.create(placeholderName, taskType, project);
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    TaskCreatingProvider<T> provider = Cast.uncheckedCast(getInstantiator()
                        .newInstance(TaskCreatingProvider.class, DefaultTaskContainer.this, identity, configure, NO_ARGS)
                    );
                    placeholders.put(placeholderName, provider);
                    deferredElementKnown(placeholderName, provider);
                    context.setResult(REGISTER_RESULT);
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return registerDescriptor(identity);
                }
            });
        } else {
            warnAboutPlaceholderDeprecation(placeholderName);
        }
    }

    private void warnAboutPlaceholderDeprecation(String placeholderName) {
        DeprecationLogger.nagUserOfDeprecated(
            "Creating a custom task named '" + placeholderName + "'",
            "You can configure the existing task using the '" + placeholderName + " { }' syntax or create your custom task under a different name."
        );
    }

    public <U extends Task> NamedDomainObjectContainer<U> containerWithType(Class<U> type) {
        throw new UnsupportedOperationException();
    }

    public Set<? extends Class<? extends Task>> getCreateableTypes() {
        return Collections.singleton(getType());
    }

    public void setModelNode(MutableModelNode modelNode) {
        this.modelNode = modelNode;
    }

    @Override
    public void whenElementKnown(Action<? super ElementInfo<Task>> action) {
        super.whenElementKnown(action);
        for (Map.Entry<String, TaskProvider<?>> entry : placeholders.entrySet()) {
            deferredElementKnown(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public <S extends Task> TaskCollection<S> withType(Class<S> type) {
        Instantiator instantiator = getInstantiator();
        return Cast.uncheckedCast(instantiator.newInstance(RealizableTaskCollection.class, type, super.withType(type), modelNode, instantiator));
    }

    // Cannot be private due to reflective instantiation
    public class TaskCreatingProvider<I extends Task> extends DefaultTaskProvider<I> {
        private Object[] constructorArgs;
        private I task;
        private Throwable cause;
        private ImmutableActionSet<I> onCreate;

        public TaskCreatingProvider(TaskIdentity<I> identity, @Nullable Action<? super I> configureAction, Object... constructorArgs) {
            super(identity);
            this.constructorArgs = constructorArgs;
            onCreate = ImmutableActionSet.<I>empty().mergeFrom(getEventRegister().getAddActions());
            statistics.lazyTask();
            if (configureAction != null) {
                configure(configureAction);
            }
        }

        @Override
        public void configure(final Action<? super I> action) {
            if (task != null) {
                // Already realized, just run the action now
                action.execute(task);
                return;
            }
            // Collect any container level add actions then add the task specific action
            onCreate = onCreate.mergeFrom(getEventRegister().getAddActions()).add(action);
        }

        @Override
        public I getOrNull() {
            if (cause != null) {
                throw createIllegalStateException();
            }
            if (task == null) {
                task = getType().cast(findByNameWithoutRules(getName()));
                if (task == null) {
                    buildOperationExecutor.run(new RunnableBuildOperation() {
                        @Override
                        public void run(BuildOperationContext context) {
                            try {
                                // Collect any container level add actions added since the last call to configure()
                                onCreate = onCreate.mergeFrom(getEventRegister().getAddActions());

                                // Create the task
                                task = createTask(identity, constructorArgs);
                                realized(TaskCreatingProvider.this);
                                statistics.lazyTaskRealized(getType());

                                // Register the task
                                add(task, onCreate);
                                // TODO removing this stuff from the store should be handled through some sort of decoration
                                context.setResult(REALIZE_RESULT);
                            } catch (RuntimeException ex) {
                                cause = ex;
                                throw createIllegalStateException();
                            } finally {
                                // Discard state that is no longer required
                                constructorArgs = null;
                                onCreate = ImmutableActionSet.empty();
                            }
                        }

                        @Override
                        public BuildOperationDescriptor.Builder description() {
                            return realizeDescriptor(identity, false, false);
                        }
                    });
                }
            }
            return task;
        }

        private IllegalStateException createIllegalStateException() {
            return new IllegalStateException(String.format("Could not create task '%s' (%s)", getName(), getType().getSimpleName()), cause);
        }
    }

    private static BuildOperationDescriptor.Builder realizeDescriptor(TaskIdentity<?> identity, boolean replacement, boolean eager) {
        return BuildOperationDescriptor.displayName("Realize task " + identity.identityPath)
            .details(new RealizeDetails(identity, replacement, eager));
    }

    private static BuildOperationDescriptor.Builder registerDescriptor(TaskIdentity<?> identity) {
        return BuildOperationDescriptor.displayName("Register task " + identity.identityPath)
            .details(new RegisterDetails(identity));
    }

    private static final RegisterTaskBuildOperationType.Result REGISTER_RESULT = new RegisterTaskBuildOperationType.Result() {
    };
    private static final RealizeTaskBuildOperationType.Result REALIZE_RESULT = new RealizeTaskBuildOperationType.Result() {
    };

    private static final class RealizeDetails implements RealizeTaskBuildOperationType.Details {

        private final TaskIdentity<?> identity;
        private final boolean replacement;
        private final boolean eager;

        RealizeDetails(TaskIdentity<?> identity, boolean replacement, boolean eager) {
            this.identity = identity;
            this.replacement = replacement;
            this.eager = eager;
        }

        @Override
        public String getBuildPath() {
            return identity.buildPath.toString();
        }

        @Override
        public String getTaskPath() {
            return identity.projectPath.toString();
        }

        @Override
        public long getTaskId() {
            return identity.uniqueId;
        }

        @Override
        public boolean isReplacement() {
            return replacement;
        }

        @Override
        public boolean isEager() {
            return eager;
        }

    }

    private static final class RegisterDetails implements RegisterTaskBuildOperationType.Details {

        private final TaskIdentity<?> identity;

        RegisterDetails(TaskIdentity<?> identity) {
            this.identity = identity;
        }

        @Override
        public String getBuildPath() {
            return identity.buildPath.toString();
        }

        @Override
        public String getTaskPath() {
            return identity.projectPath.toString();
        }

        @Override
        public long getTaskId() {
            return identity.uniqueId;
        }

        @Override
        public boolean isReplacement() {
            return false;
        }

    }

}
