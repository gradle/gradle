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

package org.gradle.api.internal;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import groovy.util.ObservableList;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.gradle.api.Action;
import org.gradle.api.AntBuilder;
import org.gradle.api.Describable;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.ClassLoaderAwareTaskAction;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.DefaultTaskDestroyables;
import org.gradle.api.internal.tasks.DefaultTaskInputs;
import org.gradle.api.internal.tasks.DefaultTaskLocalState;
import org.gradle.api.internal.tasks.DefaultTaskOutputs;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskLocalStateInternal;
import org.gradle.api.internal.tasks.TaskMutator;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.execution.DefaultTaskExecutionContext;
import org.gradle.api.internal.tasks.execution.TaskValidator;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskDestroyables;
import org.gradle.api.tasks.TaskInstantiationException;
import org.gradle.api.tasks.TaskLocalState;
import org.gradle.internal.Factory;
import org.gradle.internal.logging.compatbridge.LoggingManagerInternalCompatibilityBridge;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.scripts.ScriptOrigin;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.StandardOutputCapture;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GFileUtils;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.util.GUtil.uncheckedCall;

public abstract class AbstractTask implements TaskInternal, DynamicObjectAware {
    private static final Logger BUILD_LOGGER = Logging.getLogger(Task.class);
    private static final ThreadLocal<TaskInfo> NEXT_INSTANCE = new ThreadLocal<TaskInfo>();

    private final ProjectInternal project;

    private final String name;

    private List<ContextAwareTaskAction> actions;

    private Path path;

    private Path identityPath;

    private boolean enabled = true;

    private final DefaultTaskDependency dependencies;

    private final DefaultTaskDependency mustRunAfter;

    private final DefaultTaskDependency finalizedBy;

    private final DefaultTaskDependency shouldRunAfter;

    private ExtensibleDynamicObject extensibleDynamicObject;

    private String description;

    private String group;

    private AndSpec<Task> onlyIfSpec = createNewOnlyIfSpec();

    private TaskExecuter executer;

    private final ServiceRegistry services;

    private final TaskStateInternal state;

    private final TaskMutator taskMutator;
    private ObservableList observableActionList;
    private boolean impliesSubProjects;
    private boolean hasCustomActions;

    private final TaskInputsInternal taskInputs;
    private final TaskOutputsInternal taskOutputs;
    private final TaskDestroyables taskDestroyables;
    private final TaskLocalStateInternal taskLocalState;
    private final Class<? extends Task> publicType;
    private LoggingManagerInternal loggingManager;

    private String toStringValue;

    protected AbstractTask() {
        this(taskInfo());
    }

    private static TaskInfo taskInfo() {
        return NEXT_INSTANCE.get();
    }

    private AbstractTask(TaskInfo taskInfo) {
        if (taskInfo == null) {
            throw new TaskInstantiationException(String.format("Task of type '%s' has been instantiated directly which is not supported. Tasks can only be created using the DSL.", getClass().getName()));
        }

        this.project = taskInfo.project;
        this.name = taskInfo.name;
        this.publicType = taskInfo.publicType;
        assert project != null;
        assert name != null;
        state = new TaskStateInternal();
        TaskContainerInternal tasks = project.getTasks();
        dependencies = new DefaultTaskDependency(tasks);
        mustRunAfter = new DefaultTaskDependency(tasks);
        finalizedBy = new DefaultTaskDependency(tasks);
        shouldRunAfter = new DefaultTaskDependency(tasks);
        services = project.getServices();

        FileResolver fileResolver = project.getFileResolver();
        taskMutator = new TaskMutator(this);
        taskInputs = new DefaultTaskInputs(fileResolver, this, taskMutator);
        taskOutputs = new DefaultTaskOutputs(fileResolver, this, taskMutator);
        taskDestroyables = new DefaultTaskDestroyables(fileResolver, this, taskMutator);
        taskLocalState = new DefaultTaskLocalState(fileResolver, this, taskMutator);
    }

    private void assertDynamicObject() {
        if (extensibleDynamicObject == null) {
            extensibleDynamicObject = new ExtensibleDynamicObject(this, publicType, services.get(Instantiator.class));
        }
    }

    public static <T extends Task> T injectIntoNewInstance(ProjectInternal project, String name, Class<? extends Task> publicType, Callable<T> factory) {
        NEXT_INSTANCE.set(new TaskInfo(project, name, publicType));
        try {
            return uncheckedCall(factory);
        } finally {
            NEXT_INSTANCE.set(null);
        }
    }

    @Override
    public TaskStateInternal getState() {
        return state;
    }

    @Override
    public AntBuilder getAnt() {
        return project.getAnt();
    }

    @Override
    public Project getProject() {
        return project;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Action<? super Task>> getActions() {
        if (observableActionList == null) {
            observableActionList = new ObservableActionWrapperList(getTaskActions());
            observableActionList.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    taskMutator.assertMutable("Task.getActions()", evt);
                }
            });
        }
        return observableActionList;
    }

    @Override
    public List<ContextAwareTaskAction> getTaskActions() {
        if (actions == null) {
            actions = new ArrayList<ContextAwareTaskAction>();
        }
        return actions;
    }

    @Override
    public void setActions(final List<Action<? super Task>> replacements) {
        taskMutator.mutate("Task.setActions(List<Action>)", new Runnable() {
            public void run() {
                getTaskActions().clear();
                for (Action<? super Task> action : replacements) {
                    doLast(action);
                }
            }
        });
    }

    @Override
    public TaskDependencyInternal getTaskDependencies() {
        return dependencies;
    }

    @Override
    public Set<Object> getDependsOn() {
        return dependencies.getValues();
    }

    @Override
    public void setDependsOn(final Iterable<?> dependsOn) {
        taskMutator.mutate("Task.setDependsOn(Iterable)", new Runnable() {
            public void run() {
                dependencies.setValues(dependsOn);
            }
        });
    }

    @Override
    public void onlyIf(final Closure onlyIfClosure) {
        taskMutator.mutate("Task.onlyIf(Closure)", new Runnable() {
            public void run() {
                onlyIfSpec = onlyIfSpec.and(onlyIfClosure);
            }
        });
    }

    @Override
    public void onlyIf(final Spec<? super Task> spec) {
        taskMutator.mutate("Task.onlyIf(Spec)", new Runnable() {
            public void run() {
                onlyIfSpec = onlyIfSpec.and(spec);
            }
        });
    }

    @Override
    public void setOnlyIf(final Spec<? super Task> spec) {
        taskMutator.mutate("Task.setOnlyIf(Spec)", new Runnable() {
            public void run() {
                onlyIfSpec = createNewOnlyIfSpec().and(spec);
            }
        });
    }

    @Override
    public void setOnlyIf(final Closure onlyIfClosure) {
        taskMutator.mutate("Task.setOnlyIf(Closure)", new Runnable() {
            public void run() {
                onlyIfSpec = createNewOnlyIfSpec().and(onlyIfClosure);
            }
        });
    }

    private AndSpec<Task> createNewOnlyIfSpec() {
        return new AndSpec<Task>(new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return element == AbstractTask.this && enabled;
            }
        });
    }

    @Override
    public Spec<? super TaskInternal> getOnlyIf() {
        return onlyIfSpec;
    }

    @Override
    public boolean getDidWork() {
        return state.getDidWork();
    }

    @Override
    public void setDidWork(boolean didWork) {
        state.setDidWork(didWork);
    }

    @Internal
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean getEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        taskMutator.mutate("Task.setEnabled(boolean)", new Runnable() {
            public void run() {
                AbstractTask.this.enabled = enabled;
            }
        });
    }

    @Override
    public boolean getImpliesSubProjects() {
        return impliesSubProjects;
    }

    @Override
    public void setImpliesSubProjects(boolean impliesSubProjects) {
        this.impliesSubProjects = impliesSubProjects;
    }

    @Override
    public String getPath() {
        if (path == null) {
            path = project.getProjectPath().child(name);
        }
        return path.getPath();
    }

    @Override
    public Path getIdentityPath() {
        if (identityPath == null) {
            identityPath = project.getIdentityPath().child(name);
        }
        return identityPath;
    }

    @Override
    public Task deleteAllActions() {
        taskMutator.mutate("Task.deleteAllActions()",
            new Runnable() {
                public void run() {
                    getTaskActions().clear();
                }
            }
        );
        return this;
    }

    @Override
    public final void execute() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("TaskInternal.execute()", getReuseTaskLogicAdvice());
        TaskExecuter executer = DeprecationLogger.whileDisabled(new Factory<TaskExecuter>() {
            @Override
            public TaskExecuter create() {
                return getExecuter();
            }
        });
        executer.execute(this, state, new DefaultTaskExecutionContext());
        state.rethrowFailure();
    }

    @Override
    public TaskExecuter getExecuter() {
        DeprecationLogger.nagUserOfDiscontinuedProperty("TaskInternal.executer", getReuseTaskLogicAdvice());
        if (executer == null) {
            executer = services.get(TaskExecuter.class);
        }
        return executer;
    }

    @Override
    public void setExecuter(TaskExecuter executer) {
        DeprecationLogger.nagUserOfDiscontinuedProperty("TaskInternal.executer", getReuseTaskLogicAdvice());
        this.executer = executer;
    }

    private String getReuseTaskLogicAdvice() {
        String reuseTaskLogicUrl = services.get(DocumentationRegistry.class).getDocumentationFor("custom_tasks", "sec:reusing_task_logic");
        return "There are better ways to re-use task logic, see " + reuseTaskLogicUrl + ".";
    }

    @Override
    public Task dependsOn(final Object... paths) {
        taskMutator.mutate("Task.dependsOn(Object...)", new Runnable() {
            public void run() {
                dependencies.add(paths);
            }
        });
        return this;
    }

    @Override
    public Task doFirst(final Action<? super Task> action) {
        return doFirst("doFirst {} action", action);
    }

    @Override
    public Task doFirst(final String actionName, final Action<? super Task> action) {
        hasCustomActions = true;
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        taskMutator.mutate("Task.doFirst(Action)", new Runnable() {
            public void run() {
                getTaskActions().add(0, wrap(action, actionName));
            }
        });
        return this;
    }

    @Override
    public Task doLast(final Action<? super Task> action) {
        return doLast("doLast {} action", action);
    }

    @Override
    public Task doLast(final String actionName, final Action<? super Task> action) {
        hasCustomActions = true;
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        taskMutator.mutate("Task.doLast(Action)", new Runnable() {
            public void run() {
                getTaskActions().add(wrap(action, actionName));
            }
        });
        return this;
    }

    @Override
    public int compareTo(Task otherTask) {
        int depthCompare = project.compareTo(otherTask.getProject());
        if (depthCompare == 0) {
            return getPath().compareTo(otherTask.getPath());
        } else {
            return depthCompare;
        }
    }

    @Override
    public String toString() {
        if (toStringValue == null) {
            toStringValue = "task '" + getIdentityPath() + "'";
        }
        return toStringValue;
    }

    @Override
    public Logger getLogger() {
        return BUILD_LOGGER;
    }

    @Override
    public LoggingManagerInternal getLogging() {
        if (loggingManager == null) {
            loggingManager = new LoggingManagerInternalCompatibilityBridge(services.getFactory(org.gradle.internal.logging.LoggingManagerInternal.class).create());
        }
        return loggingManager;
    }

    @Override
    public StandardOutputCapture getStandardOutputCapture() {
        return getLogging();
    }

    @Override
    public Object property(String propertyName) throws MissingPropertyException {
        assertDynamicObject();
        return extensibleDynamicObject.getProperty(propertyName);
    }

    @Override
    public boolean hasProperty(String propertyName) {
        assertDynamicObject();
        return extensibleDynamicObject.hasProperty(propertyName);
    }

    @Override
    public void setProperty(String name, Object value) {
        assertDynamicObject();
        extensibleDynamicObject.setProperty(name, value);
    }

    @Override
    public Convention getConvention() {
        assertDynamicObject();
        return extensibleDynamicObject.getConvention();
    }

    @Internal
    @Override
    public ExtensionContainer getExtensions() {
        return getConvention();
    }

    @Internal
    @Override
    public DynamicObject getAsDynamicObject() {
        assertDynamicObject();
        return extensibleDynamicObject;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public void setGroup(String group) {
        this.group = group;
    }

    @Override
    public TaskInputsInternal getInputs() {
        return taskInputs;
    }

    @Override
    public TaskOutputsInternal getOutputs() {
        return taskOutputs;
    }

    @Override
    public TaskDestroyables getDestroyables() {
        return taskDestroyables;
    }

    @Override
    public TaskLocalState getLocalState() {
        return taskLocalState;
    }

    @Internal
    protected ServiceRegistry getServices() {
        return services;
    }

    @Override
    public boolean dependsOnTaskDidWork() {
        DeprecationLogger.nagUserOfDiscontinuedMethod(
            "Task.dependsOnTaskDidWork()",
            "Instead, check the value of \"didWork()\" for each task, or declare the task inputs and outputs and let Gradle decide what needs to be run.");
        TaskDependency dependency = getTaskDependencies();
        for (Task depTask : dependency.getDependencies(this)) {
            if (depTask.getDidWork()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Task doFirst(final Closure action) {
        hasCustomActions = true;
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        taskMutator.mutate("Task.doFirst(Closure)", new Runnable() {
            public void run() {
                getTaskActions().add(0, convertClosureToAction(action, "doFirst {} action"));
            }
        });
        return this;
    }

    @Override
    public Task doLast(final Closure action) {
        hasCustomActions = true;
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        taskMutator.mutate("Task.doLast(Closure)", new Runnable() {
            public void run() {
                getTaskActions().add(convertClosureToAction(action, "doLast {} action"));
            }
        });
        return this;
    }

    @Override
    public Task leftShift(final Closure action) {
        DeprecationLogger.nagUserWith("The Task.leftShift(Closure) method has been deprecated and is scheduled to be removed in Gradle 5.0. Please use Task.doLast(Action) instead.");

        hasCustomActions = true;
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        taskMutator.mutate("Task.leftShift(Closure)", new Runnable() {
            public void run() {
                getTaskActions().add(taskMutator.leftShift(convertClosureToAction(action, "doLast {} action")));
            }
        });
        return this;
    }

    @Override
    public Task configure(Closure closure) {
        return ConfigureUtil.configureSelf(closure, this);
    }

    @Override
    public File getTemporaryDir() {
        File dir = getServices().get(TemporaryFileProvider.class).newTemporaryFile(getName());
        GFileUtils.mkdirs(dir);
        return dir;
    }

    // note: this method is on TaskInternal
    @Override
    public Factory<File> getTemporaryDirFactory() {
        return new Factory<File>() {
            public File create() {
                return getTemporaryDir();
            }
        };
    }

    @Override
    public void addValidator(TaskValidator validator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<TaskValidator> getValidators() {
        throw new UnsupportedOperationException();
    }

    private ContextAwareTaskAction convertClosureToAction(Closure actionClosure, String actionName) {
        return new ClosureTaskAction(actionClosure, actionName);
    }

    private ContextAwareTaskAction wrap(final Action<? super Task> action) {
        return wrap(action, "unnamed action");
    }

    private ContextAwareTaskAction wrap(final Action<? super Task> action, String actionName) {
        if (action instanceof ContextAwareTaskAction) {
            return (ContextAwareTaskAction) action;
        }
        return new TaskActionWrapper(action, actionName);
    }

    private static class TaskInfo {
        private final ProjectInternal project;
        private final Class<? extends Task> publicType;
        private final String name;

        private TaskInfo(ProjectInternal project, String name, Class<? extends Task> publicType) {
            this.name = name;
            this.project = project;
            this.publicType = publicType;
        }
    }

    private static class ClosureTaskAction implements ContextAwareTaskAction {
        private final Closure closure;
        private final String actionName;

        private ClosureTaskAction(Closure closure, String actionName) {
            this.closure = closure;
            this.actionName = actionName;
        }

        @Override
        public void contextualise(TaskExecutionContext context) {
        }

        @Override
        public void releaseContext() {
        }

        @Override
        public void execute(Task task) {
            closure.setDelegate(task);
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(closure.getClass().getClassLoader());
            try {
                if (closure.getMaximumNumberOfParameters() == 0) {
                    closure.call();
                } else {
                    closure.call(task);
                }
            } catch (InvokerInvocationException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw e;
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }

        @Override
        public ClassLoader getClassLoader() {
            return closure.getClass().getClassLoader();
        }

        @Override
        public String getActionClassName() {
            return AbstractTask.getActionClassName(closure);
        }

        @Override
        public String getDisplayName() {
            return "Execute " + actionName;
        }
    }

    private static class TaskActionWrapper implements ContextAwareTaskAction {
        private final Action<? super Task> action;
        private final String maybeActionName;

        /**
         * The <i>action name</i> is used to construct a human readable name for
         * the actions to be used in progress logging. It is only used if
         * the wrapped action does not already implement {@link Describable}.
         */
        public TaskActionWrapper(Action<? super Task> action, String maybeActionName) {
            this.action = action;
            this.maybeActionName = maybeActionName;
        }

        @Override
        public void contextualise(TaskExecutionContext context) {
            if (action instanceof ContextAwareTaskAction) {
                ((ContextAwareTaskAction) action).contextualise(context);
            }
        }

        @Override
        public void releaseContext() {
            if (action instanceof ContextAwareTaskAction) {
                ((ContextAwareTaskAction) action).releaseContext();
            }
        }

        @Override
        public void execute(Task task) {
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(action.getClass().getClassLoader());
            try {
                action.execute(task);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }

        @Override
        public ClassLoader getClassLoader() {
            if (action instanceof ClassLoaderAwareTaskAction) {
                return ((ClassLoaderAwareTaskAction) action).getClassLoader();
            } else {
                return action.getClass().getClassLoader();
            }
        }

        @Override
        public String getActionClassName() {
            if (action instanceof ClassLoaderAwareTaskAction) {
                return ((ClassLoaderAwareTaskAction) action).getActionClassName();
            } else {
                return AbstractTask.getActionClassName(action);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TaskActionWrapper)) {
                return false;
            }

            TaskActionWrapper that = (TaskActionWrapper) o;

            if (action != null ? !action.equals(that.action) : that.action != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return action != null ? action.hashCode() : 0;
        }

        @Override
        public String getDisplayName() {
            if (action instanceof Describable) {
                return ((Describable) action).getDisplayName();
            }
            return "Execute " + maybeActionName;
        }
    }

    private static String getActionClassName(Object action) {
        if (action instanceof ScriptOrigin) {
            ScriptOrigin origin = (ScriptOrigin) action;
            return origin.getOriginalClassName() + "_" + origin.getContentHash();
        } else {
            return action.getClass().getName();
        }
    }

    @Override
    public void setMustRunAfter(final Iterable<?> mustRunAfterTasks) {
        taskMutator.mutate("Task.setMustRunAfter(Iterable)", new Runnable() {
            public void run() {
                mustRunAfter.setValues(mustRunAfterTasks);
            }
        });
    }

    @Override
    public Task mustRunAfter(final Object... paths) {
        taskMutator.mutate("Task.mustRunAfter(Object...)", new Runnable() {
            public void run() {
                mustRunAfter.add(paths);
            }
        });
        return this;
    }

    @Override
    public TaskDependency getMustRunAfter() {
        return mustRunAfter;
    }

    @Override
    public void setFinalizedBy(final Iterable<?> finalizedByTasks) {
        taskMutator.mutate("Task.setFinalizedBy(Iterable)", new Runnable() {
            public void run() {
                finalizedBy.setValues(finalizedByTasks);
            }
        });
    }

    @Override
    public Task finalizedBy(final Object... paths) {
        taskMutator.mutate("Task.finalizedBy(Object...)", new Runnable() {
            public void run() {
                finalizedBy.add(paths);
            }
        });
        return this;
    }

    @Override
    public TaskDependency getFinalizedBy() {
        return finalizedBy;
    }

    @Override
    public TaskDependency shouldRunAfter(final Object... paths) {
        taskMutator.mutate("Task.shouldRunAfter(Object...)", new Runnable() {
            public void run() {
                shouldRunAfter.add(paths);
            }
        });
        return shouldRunAfter;
    }

    @Override
    public void setShouldRunAfter(final Iterable<?> shouldRunAfterTasks) {
        taskMutator.mutate("Task.setShouldRunAfter(Iterable)", new Runnable() {
            public void run() {
                shouldRunAfter.setValues(shouldRunAfterTasks);
            }
        });
    }

    @Override
    public TaskDependency getShouldRunAfter() {
        return shouldRunAfter;
    }

    private class ObservableActionWrapperList extends ObservableList {
        public ObservableActionWrapperList(List delegate) {
            super(delegate);
        }

        @Override
        public boolean add(Object action) {
            if (action == null) {
                throw new InvalidUserDataException("Action must not be null!");
            }
            return super.add(wrap((Action<? super Task>) action));
        }

        @Override
        public void add(int index, Object action) {
            if (action == null) {
                throw new InvalidUserDataException("Action must not be null!");
            }
            super.add(index, wrap((Action<? super Task>) action));
        }

        @Override
        public boolean addAll(Collection actions) {
            if (actions == null) {
                throw new InvalidUserDataException("Actions must not be null!");
            }
            return super.addAll(transformToContextAwareTaskActions(actions));
        }

        @Override
        public boolean addAll(int index, Collection actions) {
            if (actions == null) {
                throw new InvalidUserDataException("Actions must not be null!");
            }
            return super.addAll(index, transformToContextAwareTaskActions(actions));
        }

        @Override
        public boolean removeAll(Collection actions) {
            return super.removeAll(transformToContextAwareTaskActions(actions));
        }

        @Override
        public boolean remove(Object action) {
            return super.remove(wrap((Action<? super Task>) action));
        }

        private Collection<ContextAwareTaskAction> transformToContextAwareTaskActions(Collection<Object> c) {
            return Collections2.transform(c, new Function<Object, ContextAwareTaskAction>() {
                public ContextAwareTaskAction apply(@Nullable Object input) {
                    return wrap((Action<? super Task>) input);
                }
            });
        }
    }

    @Override
    public void prependParallelSafeAction(final Action<? super Task> action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        getTaskActions().add(0, wrap(action));
    }

    @Override
    public void appendParallelSafeAction(final Action<? super Task> action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        getTaskActions().add(wrap(action));
    }

    @Override
    public boolean isHasCustomActions() {
        return hasCustomActions;
    }
}
