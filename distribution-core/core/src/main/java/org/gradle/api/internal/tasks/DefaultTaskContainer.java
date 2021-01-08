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
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.MutationGuards;
import org.gradle.api.internal.NamedDomainObjectContainerConfigureDelegate;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.CrossProjectConfigurator;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.internal.project.taskfactory.TaskInstantiator;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.Transformers;
import org.gradle.internal.exceptions.Contextual;
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
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.util.Collection;
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
    private final NamedEntityInstantiator<Task> taskInstantiator;
    private final ProjectAccessListener projectAccessListener;
    private final BuildOperationExecutor buildOperationExecutor;

    private final TaskStatistics statistics;
    private final boolean eagerlyCreateLazyTasks;

    private MutableModelNode modelNode;

    public DefaultTaskContainer(final ProjectInternal project,
                                Instantiator instantiator,
                                final ITaskFactory taskFactory,
                                ProjectAccessListener projectAccessListener,
                                TaskStatistics statistics,
                                BuildOperationExecutor buildOperationExecutor,
                                CrossProjectConfigurator crossProjectConfigurator,
                                CollectionCallbackActionDecorator callbackDecorator) {
        super(Task.class, instantiator, project, MutationGuards.of(crossProjectConfigurator), callbackDecorator);
        this.taskFactory = taskFactory;
        taskInstantiator = new TaskInstantiator(taskFactory, project);
        this.projectAccessListener = projectAccessListener;
        this.statistics = statistics;
        this.eagerlyCreateLazyTasks = Boolean.getBoolean(EAGERLY_CREATE_LAZY_TASKS_PROPERTY);
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public Task create(Map<String, ?> options) {
        assertMutable("create(Map<String, ?>)");
        return doCreate(options, Actions.doNothing());
    }

    private Task doCreate(Map<String, ?> options, final Action<? super Task> configureAction) {
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
                try {
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
                    configureAction.execute(task);
                    context.setResult(REALIZE_RESULT);
                    return task;
                } catch (Throwable t) {
                    throw taskCreationException(name, t);
                }
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

        if (replaceExisting) {
            Task existing = findByNameWithoutRules(name);
            if (existing != null) {
                throw new IllegalStateException("Replacing an existing task that may have already been used by other plugins is not supported.  Use a different name for this task ('" + name + "').");
            } else {
                TaskCreatingProvider<? extends Task> taskProvider = Cast.uncheckedCast(findByNameLaterWithoutRules(name));
                if (taskProvider != null) {
                    removeInternal(taskProvider);

                    final Action<? super T> onCreate;
                    if (!taskProvider.getType().isAssignableFrom(task.getClass())) {
                        throw new IllegalStateException("Replacing an existing task with an incompatible type is not supported.  Use a different name for this task ('" + name + "') or use a compatible type (" + ((TaskInternal) task).getTaskIdentity().type.getName() + ")");
                    } else {
                        onCreate = Cast.uncheckedCast(taskProvider.getOnCreateActions().mergeFrom(getEventRegister().getAddActions()));
                    }

                    add(task, onCreate);
                    return; // Exit early as we are reusing the create actions from the provider
                } else {
                    throw new IllegalStateException("Unnecessarily replacing a task that does not exist is not supported.  Use create() or register() directly instead.  You attempted to replace a task named '" + name + "', but there is no existing task with that name.");
                }
            }
        } else if (hasWithName(name)) {
            failOnDuplicateTask(name);
        }

        addInternal(task);
    }

    private void failOnDuplicateTask(String task) {
        throw new DuplicateTaskException(String.format("Cannot add task '%s' as a task with that name already exists.", task));
    }

    @Override
    public <U extends Task> U maybeCreate(String name, Class<U> type) throws InvalidUserDataException {
        Task existing = findByName(name);
        if (existing != null) {
            return Transformers.cast(type).transform(existing);
        }
        return create(name, type);
    }

    @Override
    public Task create(Map<String, ?> options, Closure configureClosure) throws InvalidUserDataException {
        assertMutable("create(Map<String, ?>, Closure)");
        return doCreate(options, ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public <T extends Task> T create(String name, Class<T> type) {
        assertMutable("create(String, Class)");
        return doCreate(name, type, NO_ARGS, Actions.doNothing());
    }

    @Override
    public <T extends Task> T create(final String name, final Class<T> type, final Object... constructorArgs) throws InvalidUserDataException {
        assertMutable("create(String, Class, Object...)");
        return doCreate(name, type, constructorArgs, Actions.doNothing());
    }

    /**
     * @param constructorArgs null == do not invoke constructor, empty == invoke constructor with no args, non-empty = invoke constructor with args
     */
    private <T extends Task> T doCreate(final String name, final Class<T> type, @Nullable final Object[] constructorArgs, final Action<? super T> configureAction) throws InvalidUserDataException {
        final TaskIdentity<T> identity = TaskIdentity.create(name, type, project);
        return buildOperationExecutor.call(new CallableBuildOperation<T>() {
            @Override
            public T call(BuildOperationContext context) {
                try {
                    T task = createTask(identity, constructorArgs);
                    statistics.eagerTask(type);
                    addTask(task, false);
                    configureAction.execute(task);
                    context.setResult(REALIZE_RESULT);
                    return task;
                } catch (Throwable t) {
                    throw taskCreationException(name, t);
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return realizeDescriptor(identity, false, true);
            }
        });
    }

    private <T extends Task> T createTask(TaskIdentity<T> identity, @Nullable Object[] constructorArgs) throws InvalidUserDataException {
        if (constructorArgs != null) {
            for (int i = 0; i < constructorArgs.length; i++) {
                if (constructorArgs[i] == null) {
                    throw new NullPointerException(String.format("Received null for %s constructor argument #%s", identity.type.getName(), i + 1));
                }
            }
        }
        return taskFactory.create(identity, constructorArgs);
    }

    @Override
    public Task create(String name) {
        assertMutable("create(String)");
        return doCreate(name, DefaultTask.class, NO_ARGS, Actions.doNothing());
    }

    @Override
    public Task create(String name, Action<? super Task> configureAction) throws InvalidUserDataException {
        assertMutable("create(String, Action)");
        return doCreate(name, DefaultTask.class, NO_ARGS, configureAction);
    }

    @Override
    public Task maybeCreate(String name) {
        Task task = findByName(name);
        if (task != null) {
            return task;
        }
        return create(name);
    }

    @Override
    public Task replace(String name) {
        assertMutable("replace(String)");
        return replace(name, DefaultTask.class);
    }

    @Override
    public Task create(String name, Closure configureClosure) {
        assertMutable("create(String, Closure)");
        return doCreate(name, DefaultTask.class, NO_ARGS, ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public <T extends Task> T create(String name, Class<T> type, Action<? super T> configuration) throws InvalidUserDataException {
        assertMutable("create(String, Class, Action)");
        T task = create(name, type);
        configuration.execute(task);
        return task;
    }

    @Override
    public TaskProvider<Task> register(String name, Action<? super Task> configurationAction) throws InvalidUserDataException {
        assertMutable("register(String, Action)");
        return Cast.uncheckedCast(register(name, DefaultTask.class, configurationAction));
    }

    @Override
    public <T extends Task> TaskProvider<T> register(String name, Class<T> type, Action<? super T> configurationAction) throws InvalidUserDataException {
        assertMutable("register(String, Class, Action)");
        return registerTask(name, type, configurationAction, NO_ARGS);
    }

    @Override
    public <T extends Task> TaskProvider<T> register(String name, Class<T> type) throws InvalidUserDataException {
        assertMutable("register(String, Class)");
        return register(name, type, NO_ARGS);
    }

    @Override
    public TaskProvider<Task> register(String name) throws InvalidUserDataException {
        assertMutable("register(String)");
        return Cast.uncheckedCast(register(name, DefaultTask.class));
    }

    @Override
    public <T extends Task> TaskProvider<T> register(String name, Class<T> type, Object... constructorArgs) {
        assertMutable("register(String, Class, Object...)");
        return registerTask(name, type, null, constructorArgs);
    }

    private <T extends Task> TaskProvider<T> registerTask(final String name, final Class<T> type, @Nullable final Action<? super T> configurationAction, final Object... constructorArgs) {
        if (hasWithName(name)) {
            failOnDuplicateTask(name);
        }

        final TaskIdentity<T> identity = TaskIdentity.create(name, type, project);

        TaskProvider<T> provider = buildOperationExecutor.call(new CallableBuildOperation<TaskProvider<T>>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return registerDescriptor(identity);
            }

            @Override
            public TaskProvider<T> call(BuildOperationContext context) {
                TaskProvider<T> provider = Cast.uncheckedNonnullCast(
                    getInstantiator().newInstance(
                        TaskCreatingProvider.class, DefaultTaskContainer.this, identity, configurationAction, constructorArgs
                    )
                );
                addLaterInternal(provider);
                context.setResult(REGISTER_RESULT);
                return provider;
            }
        });

        if (eagerlyCreateLazyTasks) {
            provider.get();
        }

        return provider;
    }

    @Override
    public <T extends Task> T replace(final String name, final Class<T> type) {
        assertMutable("replace(String, Class)");
        final TaskIdentity<T> identity = TaskIdentity.create(name, type, project);
        return buildOperationExecutor.call(new CallableBuildOperation<T>() {
            @Override
            public T call(BuildOperationContext context) {
                try {
                    T task = taskFactory.create(identity, NO_ARGS);
                    addTask(task, true);
                    context.setResult(REALIZE_RESULT);
                    return task;
                } catch (Throwable t) {
                    throw taskCreationException(name, t);
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return realizeDescriptor(identity, true, true);
            }
        });
    }

    @Override
    public <T extends Task> T createWithoutConstructor(String name, Class<T> type) {
        assertMutable("createWithoutConstructor(String, Class, Object...)");
        return doCreate(name, type, null, Actions.doNothing());
    }

    @Override
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
    public Task resolveTask(String path) {
        if (Strings.isNullOrEmpty(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        return getByPath(path);
    }

    @Override
    public Task getByPath(String path) throws UnknownTaskException {
        Task task = findByPath(path);
        if (task == null) {
            throw new UnknownTaskException(String.format("Task with path '%s' not found in %s.", path, project));
        }
        return task;
    }

    @Override
    public TaskContainerInternal configure(Closure configureClosure) {
        return ConfigureUtil.configureSelf(configureClosure, this, new NamedDomainObjectContainerConfigureDelegate(configureClosure, this));
    }

    @Override
    public NamedEntityInstantiator<Task> getEntityInstantiator() {
        return taskInstantiator;
    }

    @Override
    public DynamicObject getTasksAsDynamicObject() {
        return getElementsAsDynamicObject();
    }

    @Override
    public SortedSet<String> getNames() {
        SortedSet<String> names = super.getNames();
        if (modelNode == null) {
            return names;
        } else {
            TreeSet<String> allNames = new TreeSet<String>(names);
            allNames.addAll(modelNode.getLinkNames());
            return allNames;
        }
    }

    @Override
    public void realize() {
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
        if (modelNode != null && modelNode.hasLink(name)) {
            realizeTask(MODEL_PATH.child(name), ModelNode.State.Initialized);
            return true;
        }
        return false;
    }

    @Override
    public Task findByName(String name) {
        Task task = super.findByName(name);
        if (task != null) {
            return task;
        }
        if (!maybeCreateTasks(name)) {
            return null;
        }
        return super.findByNameWithoutRules(name);
    }

    private Task realizeTask(ModelPath taskPath, ModelNode.State minState) {
        return project.getModelRegistry().atStateOrLater(taskPath, ModelType.of(Task.class), minState);
    }

    @Override
    public <U extends Task> NamedDomainObjectContainer<U> containerWithType(Class<U> type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<? extends Class<? extends Task>> getCreateableTypes() {
        return Collections.singleton(getType());
    }

    public void setModelNode(MutableModelNode modelNode) {
        this.modelNode = modelNode;
    }

    @Override
    public <S extends Task> TaskCollection<S> withType(Class<S> type) {
        Instantiator instantiator = getInstantiator();
        return Cast.uncheckedCast(instantiator.newInstance(DefaultRealizableTaskCollection.class, type, super.withType(type), modelNode, instantiator));
    }

    @Deprecated
    @Override
    public boolean remove(Object o) {
        throw unsupportedTaskRemovalException();
    }

    private boolean removeInternal(Object o) {
        return super.remove(o);
    }

    @Deprecated
    @Override
    public boolean removeAll(Collection<?> c) {
        throw unsupportedTaskRemovalException();
    }

    @Override
    public void clear() {
        throw unsupportedTaskRemovalException();
    }

    @Override
    public boolean retainAll(Collection<?> target) {
        throw unsupportedTaskRemovalException();
    }

    @Override
    public Iterator<Task> iterator() {
        final Iterator<Task> delegate = super.iterator();
        return new Iterator<Task>() {
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public Task next() {
                return delegate.next();
            }

            @Override
            public void remove() {
                throw unsupportedTaskRemovalException();
            }
        };
    }

    private static RuntimeException unsupportedTaskRemovalException() {
        return new UnsupportedOperationException("Removing tasks from the task container is not supported.  Disable the tasks or use replace() instead.");
    }

    @Override
    public Action<? super Task> whenObjectRemoved(Action<? super Task> action) {
        throw new UnsupportedOperationException("Registering actions on task removal is not supported.");
    }

    @Override
    public void whenObjectRemoved(Closure action) {
        throw new UnsupportedOperationException("Registering actions on task removal is not supported.");
    }

    // Cannot be private due to reflective instantiation
    public class TaskCreatingProvider<I extends Task> extends AbstractDomainObjectCreatingProvider<I> implements TaskProvider<I> {
        private final TaskIdentity<I> identity;
        private Object[] constructorArgs;

        public TaskCreatingProvider(TaskIdentity<I> identity, @Nullable Action<? super I> configureAction, Object... constructorArgs) {
            super(identity.name, identity.type, configureAction);
            this.identity = identity;
            this.constructorArgs = constructorArgs;
            statistics.lazyTask();
        }

        public ImmutableActionSet<I> getOnCreateActions() {
            return onCreate;
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.taskState(get());
        }

        @Override
        protected void tryCreate() {
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    try {
                        TaskCreatingProvider.super.tryCreate();
                        // TODO removing this stuff from the store should be handled through some sort of decoration
                        context.setResult(REALIZE_RESULT);
                    } finally {
                        constructorArgs = null;
                    }
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return realizeDescriptor(identity, false, false);
                }
            });
        }

        @Override
        protected I createDomainObject() {
            return createTask(identity, constructorArgs);
        }

        @Override
        protected void onLazyDomainObjectRealized() {
            statistics.lazyTaskRealized(getType());
        }

        @Override
        protected RuntimeException domainObjectCreationException(Throwable cause) {
            return taskCreationException(getName(), cause);
        }
    }

    private RuntimeException taskCreationException(String name, Throwable cause) {
        if (cause instanceof DuplicateTaskException) {
            return (RuntimeException) cause;
        }
        return new TaskCreationException(String.format("Could not create task '%s'.", project.identityPath(name)), cause);
    }

    private static BuildOperationDescriptor.Builder realizeDescriptor(TaskIdentity<?> identity, boolean replacement, boolean eager) {
        return BuildOperationDescriptor.displayName("Realize task " + identity.identityPath)
            .details(new RealizeDetails(identity, replacement, eager));
    }

    private static BuildOperationDescriptor.Builder registerDescriptor(TaskIdentity<?> identity) {
        return BuildOperationDescriptor.displayName("Register task " + identity.identityPath)
            .details(new RegisterDetails(identity));
    }

    @Deprecated
    @Override
    public boolean add(Task o) {
        throw new UnsupportedOperationException("Adding a task directly to the task container is not supported.  Use register() instead.");
    }

    @Deprecated
    @Override
    public boolean addAll(Collection<? extends Task> c) {
        throw new UnsupportedOperationException("Adding a collection of tasks directly to the task container is not supported.  Use register() instead.");
    }

    @Override
    public void addLater(Provider<? extends Task> provider) {
        throw new UnsupportedOperationException("Adding a task provider directly to the task container is not supported.  Use the register() method instead.");
    }

    @Override
    public void addAllLater(Provider<? extends Iterable<Task>> provider) {
        throw new UnsupportedOperationException("Adding a task provider directly to the task container is not supported.  Use the register() method instead.");
    }

    @Override
    public boolean addInternal(Task task) {
        return super.add(task);
    }

    @Override
    public boolean addAllInternal(Collection<? extends Task> task) {
        return super.addAll(task);
    }

    private void addLaterInternal(Provider<? extends Task> provider) {
        super.addLater(provider);
    }

    private static final RegisterTaskBuildOperationType.Result REGISTER_RESULT = new RegisterTaskBuildOperationType.Result() {
    };
    private static final RealizeTaskBuildOperationType.Result REALIZE_RESULT = new RealizeTaskBuildOperationType.Result() {
    };

    @Contextual
    private static class TaskCreationException extends GradleException {
        TaskCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class DuplicateTaskException extends InvalidUserDataException {
        public DuplicateTaskException(String message) {
            super(message);
        }
    }

    @Contextual
    private static class IncompatibleTaskTypeException extends InvalidUserDataException {
        public IncompatibleTaskTypeException(String message) {
            super(message);
        }
    }

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
