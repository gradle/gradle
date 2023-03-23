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

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
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
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectOrderingUtil;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.DefaultTaskDestroyables;
import org.gradle.api.internal.tasks.DefaultTaskInputs;
import org.gradle.api.internal.tasks.DefaultTaskLocalState;
import org.gradle.api.internal.tasks.DefaultTaskOutputs;
import org.gradle.api.internal.tasks.DefaultTaskRequiredServices;
import org.gradle.api.internal.tasks.InputChangesAwareTaskAction;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskExecutionAccessChecker;
import org.gradle.api.internal.tasks.TaskLocalStateInternal;
import org.gradle.api.internal.tasks.TaskMutator;
import org.gradle.api.internal.tasks.TaskRequiredServices;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.execution.DescribingAndSpec;
import org.gradle.api.internal.tasks.properties.ServiceReferenceSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.internal.BuildServiceProvider;
import org.gradle.api.services.internal.BuildServiceRegistryInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskDestroyables;
import org.gradle.api.tasks.TaskInstantiationException;
import org.gradle.api.tasks.TaskLocalState;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.extensibility.ExtensibleDynamicObject;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.logging.slf4j.ContextAwareTaskLogger;
import org.gradle.internal.logging.slf4j.DefaultContextAwareTaskLogger;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.properties.bean.PropertyWalker;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.scripts.ScriptOriginUtil;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.gradle.util.Path;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.gradle.util.internal.GUtil.uncheckedCall;

/**
 * @deprecated This class will be removed in Gradle 9.0. Please use {@link org.gradle.api.DefaultTask} instead.
 */
@Deprecated
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class AbstractTask implements TaskInternal, DynamicObjectAware {
    private static final Logger BUILD_LOGGER = Logging.getLogger(Task.class);
    private static final ThreadLocal<TaskInfo> NEXT_INSTANCE = new ThreadLocal<TaskInfo>();

    private final TaskIdentity<?> identity;

    private final ProjectInternal project;

    private List<InputChangesAwareTaskAction> actions;

    private boolean enabled = true;

    private final DefaultTaskDependency dependencies;

    /**
     * "lifecycle dependencies" are dependencies declared via an explicit {@link Task#dependsOn(Object...)}
     */
    private final DefaultTaskDependency lifecycleDependencies;

    private final DefaultTaskDependency mustRunAfter;

    private final DefaultTaskDependency finalizedBy;

    private final DefaultTaskDependency shouldRunAfter;

    private ExtensibleDynamicObject extensibleDynamicObject;

    private String description;

    private String group;

    private final Property<Duration> timeout;

    private DescribingAndSpec<Task> onlyIfSpec = createNewOnlyIfSpec();

    private String reasonNotToTrackState;

    private String reasonIncompatibleWithConfigurationCache;

    private final ServiceRegistry services;

    private final TaskStateInternal state;

    private final ContextAwareTaskLogger logger = new DefaultContextAwareTaskLogger(BUILD_LOGGER);

    private final TaskMutator taskMutator;
    private ObservableList observableActionList;
    private boolean impliesSubProjects;
    private boolean hasCustomActions;

    private final TaskInputsInternal taskInputs;
    private final TaskOutputsInternal taskOutputs;
    private final TaskDestroyables taskDestroyables;
    private final TaskLocalStateInternal taskLocalState;
    private final TaskRequiredServices taskRequiredServices;
    private final TaskExecutionAccessChecker taskExecutionAccessChecker;
    private LoggingManagerInternal loggingManager;

    protected AbstractTask() {
        this(taskInfo());
    }

    private static TaskInfo taskInfo() {
        return NEXT_INSTANCE.get();
    }

    private AbstractTask(TaskInfo taskInfo) {
        if (taskInfo == null) {
            throw new TaskInstantiationException(String.format("Task of type '%s' has been instantiated directly which is not supported. Tasks can only be created using the Gradle API or DSL.", getClass().getName()));
        }

        this.identity = taskInfo.identity;
        this.project = taskInfo.project;

        assert project != null;
        assert identity.name != null;
        this.state = new TaskStateInternal();
        final TaskDependencyFactory taskDependencyFactory = project.getTaskDependencyFactory();
        this.mustRunAfter = taskDependencyFactory.configurableDependency();
        this.finalizedBy = taskDependencyFactory.configurableDependency();
        this.shouldRunAfter = taskDependencyFactory.configurableDependency();
        this.lifecycleDependencies = taskDependencyFactory.configurableDependency();

        this.services = project.getServices();

        PropertyWalker propertyWalker = services.get(PropertyWalker.class);
        FileCollectionFactory fileCollectionFactory = services.get(FileCollectionFactory.class);
        taskMutator = new TaskMutator(this);
        taskInputs = new DefaultTaskInputs(this, taskMutator, propertyWalker, project.getTaskDependencyFactory(), fileCollectionFactory);
        taskOutputs = new DefaultTaskOutputs(this, taskMutator, propertyWalker, project.getTaskDependencyFactory(), fileCollectionFactory);
        taskDestroyables = new DefaultTaskDestroyables(taskMutator, fileCollectionFactory);
        taskLocalState = new DefaultTaskLocalState(taskMutator, fileCollectionFactory);
        this.dependencies = taskDependencyFactory.configurableDependency(ImmutableSet.of(taskInputs, lifecycleDependencies));
        taskRequiredServices = new DefaultTaskRequiredServices(this, taskMutator, propertyWalker);
        taskExecutionAccessChecker = services.get(TaskExecutionAccessChecker.class);

        this.timeout = project.getObjects().property(Duration.class);
    }

    private void assertDynamicObject() {
        if (extensibleDynamicObject == null) {
            extensibleDynamicObject = new ExtensibleDynamicObject(this, identity.type, services.get(InstanceGenerator.class));
        }
    }

    public static <T extends Task> T injectIntoNewInstance(ProjectInternal project, TaskIdentity<T> identity, Callable<T> factory) {
        NEXT_INSTANCE.set(new TaskInfo(identity, project));
        try {
            return uncheckedCall(factory);
        } finally {
            NEXT_INSTANCE.set(null);
        }
    }

    @Internal
    @Override
    public TaskStateInternal getState() {
        return state;
    }

    @Override
    @Internal
    public AntBuilder getAnt() {
        return project.getAnt();
    }

    @Internal
    @Override
    public Project getProject() {
        taskExecutionAccessChecker.notifyProjectAccess(this);
        return project;
    }

    @Internal
    @Override
    public String getName() {
        return identity.name;
    }

    @Override
    public TaskIdentity<?> getTaskIdentity() {
        return identity;
    }

    @Internal
    @Override
    public List<Action<? super Task>> getActions() {
        if (observableActionList == null) {
            observableActionList = new ObservableActionWrapperList(getTaskActions());
            observableActionList.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    taskMutator.assertMutable("Task.getActions()", evt);
                }
            });
        }
        return Cast.uncheckedNonnullCast(observableActionList);
    }

    @Override
    public List<InputChangesAwareTaskAction> getTaskActions() {
        if (actions == null) {
            actions = new ArrayList<InputChangesAwareTaskAction>(3);
        }
        return actions;
    }

    @Override
    public boolean hasTaskActions() {
        return actions != null && !actions.isEmpty();
    }

    @Override
    public void setActions(final List<Action<? super Task>> replacements) {
        taskMutator.mutate("Task.setActions(List<Action>)", new Runnable() {
            @Override
            public void run() {
                getTaskActions().clear();
                for (Action<? super Task> action : replacements) {
                    doLast(action);
                }
            }
        });
    }

    @Internal
    @Override
    public TaskDependencyInternal getTaskDependencies() {
        taskExecutionAccessChecker.notifyTaskDependenciesAccess(this, "Task.taskDependencies");
        return dependencies;
    }

    @Internal
    @Override
    public TaskDependencyInternal getLifecycleDependencies() {
        return lifecycleDependencies;
    }

    @Internal
    @Override
    public Set<Object> getDependsOn() {
        taskExecutionAccessChecker.notifyTaskDependenciesAccess(this, "Task.dependsOn");
        return lifecycleDependencies.getMutableValues();
    }

    @Override
    public void setDependsOn(final Iterable<?> dependsOn) {
        taskMutator.mutate("Task.setDependsOn(Iterable)", new Runnable() {
            @Override
            public void run() {
                lifecycleDependencies.setValues(dependsOn);
            }
        });
    }

    @Override
    public void onlyIf(final Closure onlyIfClosure) {
        taskMutator.mutate("Task.onlyIf(Closure)", new Runnable() {
            @Override
            public void run() {
                onlyIfSpec = onlyIfSpec.and(onlyIfClosure, "Task satisfies onlyIf closure");
            }
        });
    }

    @Override
    public void onlyIf(final Spec<? super Task> spec) {
        taskMutator.mutate("Task.onlyIf(Spec)", new Runnable() {
            @Override
            public void run() {
                onlyIfSpec = onlyIfSpec.and(spec, "Task satisfies onlyIf spec");
            }
        });
    }

    @Override
    public void onlyIf(final String onlyIfReason, final Spec<? super Task> spec) {
        taskMutator.mutate("Task.onlyIf(String, Spec)", new Runnable() {
            @Override
            public void run() {
                onlyIfSpec = onlyIfSpec.and(spec, onlyIfReason);
            }
        });
    }

    @Override
    public void setOnlyIf(final Spec<? super Task> spec) {
        taskMutator.mutate("Task.setOnlyIf(Spec)", new Runnable() {
            @Override
            public void run() {
                onlyIfSpec = createNewOnlyIfSpec().and(spec, "Task satisfies onlyIf spec");
            }
        });
    }

    @Override
    public void setOnlyIf(String onlyIfReason, Spec<? super Task> spec) {
        taskMutator.mutate("Task.setOnlyIf(String, Spec)", new Runnable() {
            @Override
            public void run() {
                onlyIfSpec = createNewOnlyIfSpec().and(spec, onlyIfReason);
            }
        });
    }

    @Override
    public void setOnlyIf(final Closure onlyIfClosure) {
        taskMutator.mutate("Task.setOnlyIf(Closure)", new Runnable() {
            @Override
            public void run() {
                onlyIfSpec = createNewOnlyIfSpec().and(onlyIfClosure, "Task satisfies onlyIf closure");
            }
        });
    }

    private DescribingAndSpec<Task> createNewOnlyIfSpec() {
        return new DescribingAndSpec<>(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return element == AbstractTask.this && enabled;
            }
        }, "Task is enabled");
    }

    @Override
    public Spec<? super TaskInternal> getOnlyIf() {
        return onlyIfSpec;
    }

    @Override
    public void doNotTrackState(String reasonNotToTrackState) {
        if (reasonNotToTrackState == null) {
            throw new InvalidUserDataException("notTrackingReason must not be null!");
        }
        taskMutator.mutate("Task.doNotTrackState(String)",
            () -> this.reasonNotToTrackState = reasonNotToTrackState
        );
    }

    @Override
    public Optional<String> getReasonNotToTrackState() {
        return Optional.ofNullable(reasonNotToTrackState);
    }

    @Override
    public void notCompatibleWithConfigurationCache(String reason) {
        taskMutator.mutate("Task.notCompatibleWithConfigurationCache(String)", () -> {
            reasonIncompatibleWithConfigurationCache = reason;
        });
    }

    @Override
    public boolean isCompatibleWithConfigurationCache() {
        return reasonIncompatibleWithConfigurationCache == null;
    }

    @Override
    public Optional<String> getReasonTaskIsIncompatibleWithConfigurationCache() {
        return Optional.ofNullable(reasonIncompatibleWithConfigurationCache);
    }

    @Internal
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

    @Internal
    @Override
    public boolean getEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        taskMutator.mutate("Task.setEnabled(boolean)", new Runnable() {
            @Override
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

    @Internal
    @Override
    public String getPath() {
        return identity.projectPath.toString();
    }

    @Override
    public Path getIdentityPath() {
        return identity.identityPath;
    }

    @Override
    public Task dependsOn(final Object... paths) {
        taskMutator.mutate("Task.dependsOn(Object...)", new Runnable() {
            @Override
            public void run() {
                lifecycleDependencies.add(paths);
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
            @Override
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
            @Override
            public void run() {
                getTaskActions().add(wrap(action, actionName));
            }
        });
        return this;
    }

    @Override
    public int compareTo(Task otherTask) {
        int depthCompare = ProjectOrderingUtil.compare(project, ((AbstractTask) otherTask).project);
        if (depthCompare == 0) {
            return getPath().compareTo(otherTask.getPath());
        } else {
            return depthCompare;
        }
    }

    @Internal
    @Override
    public Logger getLogger() {
        return logger;
    }

    @Internal
    @Override
    public org.gradle.api.logging.LoggingManager getLogging() {
        return loggingManager();
    }

    @Override
    public StandardOutputCapture getStandardOutputCapture() {
        return loggingManager();
    }

    private LoggingManagerInternal loggingManager() {
        if (loggingManager == null) {
            loggingManager = services.getFactory(org.gradle.internal.logging.LoggingManagerInternal.class).create();
        }
        return loggingManager;
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

    @Internal
    @Override
    @Deprecated
    public org.gradle.api.plugins.Convention getConvention() {
        DeprecationLogger.deprecateMethod(AbstractTask.class, "getConvention()")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_access_to_conventions")
            .nagUser();
        return getConventionVia("Task.convention", false);
    }

    @Internal
    @Override
    public ExtensionContainer getExtensions() {
        return getConventionVia("Task.extensions", true);
    }

    private org.gradle.api.plugins.Convention getConventionVia(String invocationDescription, boolean disableDeprecationForConventionAccess) {
        notifyConventionAccess(invocationDescription);
        assertDynamicObject();
        if (disableDeprecationForConventionAccess) {
            return DeprecationLogger.whileDisabled(() -> extensibleDynamicObject.getConvention());
        }
        return extensibleDynamicObject.getConvention();
    }

    @Internal
    @Override
    public DynamicObject getAsDynamicObject() {
        assertDynamicObject();
        return extensibleDynamicObject;
    }

    @Internal
    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Internal
    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public void setGroup(String group) {
        this.group = group;
    }

    @Internal
    @Override
    public TaskInputsInternal getInputs() {
        return taskInputs;
    }

    @Internal
    @Override
    public TaskOutputsInternal getOutputs() {
        return taskOutputs;
    }

    @Internal
    @Override
    public TaskDestroyables getDestroyables() {
        return taskDestroyables;
    }

    @Internal
    @Override
    public TaskLocalState getLocalState() {
        return taskLocalState;
    }

    @Internal
    protected ServiceRegistry getServices() {
        return services;
    }

    @Override
    public Task doFirst(final Closure action) {
        hasCustomActions = true;
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        taskMutator.mutate("Task.doFirst(Closure)", new Runnable() {
            @Override
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
            @Override
            public void run() {
                getTaskActions().add(convertClosureToAction(action, "doLast {} action"));
            }
        });
        return this;
    }

    @Override
    public Task configure(Closure closure) {
        return ConfigureUtil.configureSelf(closure, this);
    }

    @Internal
    @Override
    public File getTemporaryDir() {
        return getServices().get(TemporaryFileProvider.class).newTemporaryDirectory(getName());
    }

    // note: this method is on TaskInternal
    @Override
    public Factory<File> getTemporaryDirFactory() {
        return getServices().get(TemporaryFileProvider.class).temporaryDirectoryFactory(getName());
    }

    private InputChangesAwareTaskAction convertClosureToAction(Closure actionClosure, String actionName) {
        return new ClosureTaskAction(actionClosure, actionName, getServices().get(UserCodeApplicationContext.class).current());
    }

    private InputChangesAwareTaskAction wrap(final Action<? super Task> action) {
        return wrap(action, "unnamed action");
    }

    private InputChangesAwareTaskAction wrap(final Action<? super Task> action, String actionName) {
        if (action instanceof InputChangesAwareTaskAction) {
            return (InputChangesAwareTaskAction) action;
        }
        if (action instanceof ConfigureUtil.WrappedConfigureAction) {
            Closure<?> configureClosure = ((ConfigureUtil.WrappedConfigureAction<?>) action).getConfigureClosure();
            return convertClosureToAction(configureClosure, actionName);
        }
        return new TaskActionWrapper(action, actionName);
    }

    private static class TaskInfo {
        private final TaskIdentity<?> identity;
        private final ProjectInternal project;

        private TaskInfo(TaskIdentity<?> identity, ProjectInternal project) {
            this.identity = identity;
            this.project = project;
        }
    }

    private static class ClosureTaskAction implements InputChangesAwareTaskAction {
        private final Closure<?> closure;
        private final String actionName;
        @Nullable
        private final UserCodeApplicationContext.Application application;

        private ClosureTaskAction(Closure<?> closure, String actionName, @Nullable UserCodeApplicationContext.Application application) {
            this.closure = closure;
            this.actionName = actionName;
            this.application = application;
        }

        @Override
        public void setInputChanges(InputChangesInternal inputChanges) {
        }

        @Override
        public void clearInputChanges() {
        }

        @Override
        public void execute(Task task) {
            if (application == null) {
                doExecute(task);
            } else {
                application.reapply(() -> doExecute(task));
            }
        }

        private void doExecute(Task task) {
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
        public ImplementationSnapshot getActionImplementation(ClassLoaderHierarchyHasher hasher) {
            return AbstractTask.getActionImplementation(closure, hasher);
        }

        @Override
        public String getDisplayName() {
            return "Execute " + actionName;
        }
    }

    private static class TaskActionWrapper implements InputChangesAwareTaskAction {
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
        public void setInputChanges(InputChangesInternal inputChanges) {
        }

        @Override
        public void clearInputChanges() {
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
        public ImplementationSnapshot getActionImplementation(ClassLoaderHierarchyHasher hasher) {
            return AbstractTask.getActionImplementation(action, hasher);
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
            return action.equals(that.action);
        }

        @Override
        public int hashCode() {
            return action.hashCode();
        }

        @Override
        public String getDisplayName() {
            if (action instanceof Describable) {
                return ((Describable) action).getDisplayName();
            }
            return "Execute " + maybeActionName;
        }
    }

    private static ImplementationSnapshot getActionImplementation(Object value, ClassLoaderHierarchyHasher hasher) {
        HashCode classLoaderHash = hasher.getClassLoaderHash(value.getClass().getClassLoader());
        String actionClassIdentifier = ScriptOriginUtil.getOriginClassIdentifier(value);
        return ImplementationSnapshot.of(actionClassIdentifier, value, classLoaderHash);
    }

    @Override
    public void setMustRunAfter(final Iterable<?> mustRunAfterTasks) {
        taskMutator.mutate("Task.setMustRunAfter(Iterable)", new Runnable() {
            @Override
            public void run() {
                mustRunAfter.setValues(mustRunAfterTasks);
            }
        });
    }

    @Override
    public Task mustRunAfter(final Object... paths) {
        taskMutator.mutate("Task.mustRunAfter(Object...)", new Runnable() {
            @Override
            public void run() {
                mustRunAfter.add(paths);
            }
        });
        return this;
    }

    @Internal
    @Override
    public TaskDependency getMustRunAfter() {
        return mustRunAfter;
    }

    @Override
    public void setFinalizedBy(final Iterable<?> finalizedByTasks) {
        taskMutator.mutate("Task.setFinalizedBy(Iterable)", new Runnable() {
            @Override
            public void run() {
                finalizedBy.setValues(finalizedByTasks);
            }
        });
    }

    @Override
    public Task finalizedBy(final Object... paths) {
        taskMutator.mutate("Task.finalizedBy(Object...)", new Runnable() {
            @Override
            public void run() {
                finalizedBy.add(paths);
            }
        });
        return this;
    }

    @Internal
    @Override
    public TaskDependency getFinalizedBy() {
        return finalizedBy;
    }

    @Override
    public TaskDependency shouldRunAfter(final Object... paths) {
        taskMutator.mutate("Task.shouldRunAfter(Object...)", new Runnable() {
            @Override
            public void run() {
                shouldRunAfter.add(paths);
            }
        });
        return shouldRunAfter;
    }

    @Override
    public void setShouldRunAfter(final Iterable<?> shouldRunAfterTasks) {
        taskMutator.mutate("Task.setShouldRunAfter(Iterable)", new Runnable() {
            @Override
            public void run() {
                shouldRunAfter.setValues(shouldRunAfterTasks);
            }
        });
    }

    @Internal
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
            return super.add(wrap(Cast.uncheckedNonnullCast(action)));
        }

        @Override
        public void add(int index, Object action) {
            if (action == null) {
                throw new InvalidUserDataException("Action must not be null!");
            }
            super.add(index, wrap(Cast.uncheckedNonnullCast(action)));
        }

        @Override
        public boolean addAll(Collection actions) {
            if (actions == null) {
                throw new InvalidUserDataException("Actions must not be null!");
            }
            return super.addAll(transformToContextAwareTaskActions(Cast.uncheckedNonnullCast(actions)));
        }

        @Override
        public boolean addAll(int index, Collection actions) {
            if (actions == null) {
                throw new InvalidUserDataException("Actions must not be null!");
            }
            return super.addAll(index, transformToContextAwareTaskActions(Cast.uncheckedNonnullCast(actions)));
        }

        @Override
        public Object set(int index, Object action) {
            if (action == null) {
                throw new InvalidUserDataException("Action must not be null!");
            }
            return super.set(index, wrap(Cast.uncheckedNonnullCast(action)));
        }

        @Override
        public boolean removeAll(Collection actions) {
            return super.removeAll(transformToContextAwareTaskActions(Cast.uncheckedNonnullCast(actions)));
        }

        @Override
        public boolean remove(Object action) {
            return super.remove(wrap(Cast.uncheckedNonnullCast(action)));
        }

        private Collection<InputChangesAwareTaskAction> transformToContextAwareTaskActions(Collection<Object> c) {
            return Collections2.transform(c, input -> wrap(Cast.uncheckedCast(input)));
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

    @Internal
    @Override
    public Property<Duration> getTimeout() {
        return timeout;
    }

    @Override
    public void usesService(Provider<? extends BuildService<?>> service) {
        taskRequiredServices.registerServiceUsage(service);
    }

    public TaskRequiredServices getRequiredServices() {
        return taskRequiredServices;
    }

    @Override
    public void acceptServiceReferences(Set<ServiceReferenceSpec> serviceReferences) {
        if (!taskRequiredServices.hasServiceReferences()) {
            BuildServiceRegistryInternal buildServiceRegistry = getBuildServiceRegistry();
            List<? extends BuildServiceProvider<?, ?>> asConsumedServices = serviceReferences.stream()
                .map(it -> buildServiceRegistry.consume(it.getBuildServiceName(), it.getBuildServiceType()))
                .collect(Collectors.toList());
            taskRequiredServices.acceptServiceReferences(asConsumedServices);
        }
    }

    @Override
    public List<ResourceLock> getSharedResources() {
        return getBuildServiceRegistry().getSharedResources(taskRequiredServices.getElements());
    }

    private void notifyConventionAccess(String invocationDescription) {
        taskExecutionAccessChecker.notifyConventionAccess(this, invocationDescription);
    }

    private BuildServiceRegistryInternal getBuildServiceRegistry() {
        return getServices().get(BuildServiceRegistryInternal.class);
    }
}
