/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.invocation;

import com.google.common.collect.ImmutableList;
import groovy.lang.Closure;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.IsolatedAction;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.BuildScopeListenerRegistrationListener;
import org.gradle.api.internal.GradleLifecycleInternal;
import org.gradle.api.internal.MutationGuards;
import org.gradle.api.internal.project.CrossProjectConfigurator;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.invocation.GradleLifecycle;
import org.gradle.configuration.internal.ListenerBuildOperationDecorator;
import org.gradle.internal.Cast;
import org.gradle.internal.DeprecatedInGradleScope;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.InternalListener;
import org.gradle.internal.MutableActionSet;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.JavaPropertyReflectionUtil;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;

import java.util.Collection;

public class DefaultGradleLifecycle implements GradleLifecycleInternal {

    private boolean projectsLoaded;
    private ProjectInternal rootProject;
    private final IsolatableFactory isolatableFactory;
    private final CrossProjectConfigurator crossProjectConfigurator;
    private final ListenerBroadcast<BuildListener> buildListenerBroadcast;
    private final ListenerBroadcast<ProjectEvaluationListener> projectEvaluationListenerBroadcast;
    private final ListenerBuildOperationDecorator listenerBuildOperationDecorator;
    private final ListenerManager listenerManager;
    private final MutableActionSet<Project> rootProjectActions = new MutableActionSet<>();
    private final IsolatedProjectActions isolatedProjectActions = new IsolatedProjectActions(new IsolatedProjectActionsHost());

    public DefaultGradleLifecycle(
        IsolatableFactory isolatableFactory,
        CrossProjectConfigurator crossProjectConfigurator,
        ListenerManager listenerManager,
        ListenerBuildOperationDecorator listenerBuildOperationDecorator
    ) {
        this.listenerManager = listenerManager;
        this.isolatableFactory = isolatableFactory;
        this.crossProjectConfigurator = crossProjectConfigurator;
        buildListenerBroadcast = listenerManager.createAnonymousBroadcaster(BuildListener.class);
        projectEvaluationListenerBroadcast = listenerManager.createAnonymousBroadcaster(ProjectEvaluationListener.class);
        this.listenerBuildOperationDecorator = listenerBuildOperationDecorator;
        buildListenerBroadcast.add(new InternalBuildAdapter() {
            @Override
            public void projectsLoaded(Gradle gradle) {
                if (!rootProjectActions.isEmpty()) {
                    crossProjectConfigurator.rootProject(getRootProject(this), rootProjectActions);
                }
                if (!isolatedProjectActions.isEmpty()) {
                    projectEvaluationListenerBroadcast.add(isolatedProjectActions.isolate());
                }
                projectsLoaded = true;
            }
        });
    }

    @Override
    public boolean hasRootProject() {
        return rootProject != null;
    }

    @Override
    public ProjectInternal getRootProject(Object consumer) {
        if (rootProject == null) {
            throw new IllegalStateException("The root project is not yet available for " + consumer + ".");
        }
        return rootProject;
    }

    @Override
    public void resetState() {
        rootProject = null;
        projectsLoaded = false;
        rootProjectActions.clear();
        buildListenerBroadcast.removeAll();
        projectEvaluationListenerBroadcast.removeAll();
        isolatedProjectActions.clear();
    }

    @Override
    public void beforeProject(IsolatedAction<? super Project> action) {
        if (projectsLoaded) {
            throw new IllegalStateException("Gradle#onBeforeProject cannot be called after settings have been evaluated.");
        }
        // TODO:isolated how should decoration work for isolated actions? Should we just capture the current UserCodeApplication?
        isolatedProjectActions.onBeforeProject(action);
    }

    @Override
    public void rootProject(Action<? super Project> action) {
        rootProject("Gradle.rootProject", action);
    }

    @Override
    public void setRootProject(ProjectInternal rootProject) {
        assert this.rootProject == null;
        this.rootProject = rootProject;
    }

    @Override
    public void allprojects(Action<? super Project> action) {
        rootProject("Gradle.allprojects", project -> project.allprojects(action));
    }

    @Override
    public void beforeProject(Closure closure) {
        registerProjectEvaluationListener("Gradle.beforeProject", "beforeEvaluate", "beforeProject(Closure)", closure);
    }

    @Override
    public void beforeProject(Action<? super Project> action) {
        registerProjectEvaluationListener("Gradle.beforeProject", "beforeEvaluate", "beforeProject(Action)", action);
    }

    @Override
    public void afterProject(Closure closure) {
        registerProjectEvaluationListener("Gradle.afterProject", "afterEvaluate", "afterProject(Closure)", closure);
    }

    @Override
    public void afterProject(Action<? super Project> action) {
        registerProjectEvaluationListener("Gradle.afterProject", "afterEvaluate", "afterProject(Action)", action);
    }

    @Override
    public void beforeSettings(Closure<?> closure) {
        registerBuildListener("beforeSettings", closure);
    }

    @Override
    public void beforeSettings(Action<? super Settings> action) {
        buildListenerBroadcast.add("beforeSettings", action);
    }

    @Override
    public void settingsEvaluated(Closure closure) {
        registerBuildListener("settingsEvaluated", closure);
    }

    @Override
    public void settingsEvaluated(Action<? super Settings> action) {
        buildListenerBroadcast.add("settingsEvaluated", action);
    }

    @Override
    public void projectsLoaded(Closure closure) {
        registerBuildListener("Gradle.projectsLoaded", "projectsLoaded", "projectsLoaded(Closure)", closure);
    }

    @Override
    public void projectsLoaded(Action<? super Gradle> action) {
        registerBuildListener("Gradle.projectsLoaded", "projectsLoaded", "projectsLoaded(Action)", action);
    }

    @Override
    public void projectsEvaluated(Closure closure) {
        registerBuildListener("Gradle.projectsEvaluated", "projectsEvaluated", "projectsEvaluated(Closure)", closure);
    }

    @Override
    public void projectsEvaluated(Action<? super Gradle> action) {
        registerBuildListener("Gradle.projectsEvaluated", "projectsEvaluated", "projectsEvaluated(Action)", action);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void buildFinished(Closure closure) {
        notifyListenerRegistration("Gradle.buildFinished", closure);
        registerBuildListener("buildFinished", closure);
    }

    @Override
    public void buildFinished(Action<? super BuildResult> action) {
        notifyListenerRegistration("Gradle.buildFinished", action);
        buildListenerBroadcast.add("buildFinished", action);
    }

    @Override
    public void notifyListenerRegistration(String registrationPoint, Object listener) {
        if (isListenerSupportedWithConfigurationCache(listener)) {
            return;
        }
        getBuildScopeListenerRegistrationListener().onBuildScopeListenerRegistration(listener, registrationPoint, this);
    }

    @Override
    public ProjectEvaluationListener getProjectEvaluationBroadcaster() {
        return projectEvaluationListenerBroadcast.getSource();
    }

    @Override
    public BuildListener getBuildListenerBroadcaster() {
        return buildListenerBroadcast.getSource();
    }

    private BuildScopeListenerRegistrationListener getBuildScopeListenerRegistrationListener() {
        return listenerManager.getBroadcaster(BuildScopeListenerRegistrationListener.class);
    }

    private static boolean isListenerSupportedWithConfigurationCache(Object listener) {
        if (listener instanceof InternalListener) {
            // Internal listeners are always allowed: we know their lifecycle and ensure there are no problems when configuration cache is reused.
            return true;
        }
        if (JavaPropertyReflectionUtil.getAnnotation(listener.getClass(), DeprecatedInGradleScope.class) != null) {
            // Explicitly unsupported Listener types are disallowed.
            return false;
        }
        // We had to check for unsupported first to reject a listener that implements both allowed and disallowed interfaces.
        // Just reject everything we don't know.
        return listener instanceof ProjectEvaluationListener
            || listener instanceof TaskExecutionGraphListener
            || listener instanceof DependencyResolutionListener;
    }

    private void registerProjectEvaluationListener(String registrationPoint, String methodName, String signature, Action<? super Project> action) {
        assertProjectMutatingMethodAllowed(signature);
        projectEvaluationListenerBroadcast.add(methodName, decorate(registrationPoint, action));
    }

    private void registerProjectEvaluationListener(String registrationPoint, String methodName, String signature, Closure closure) {
        assertProjectMutatingMethodAllowed(signature);
        projectEvaluationListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch(methodName, decorate(registrationPoint, closure)));
    }

    private void registerBuildListener(String methodName, Closure<?> closure) {
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch(methodName, closure));
    }

    private void registerBuildListener(String registrationPoint, String methodName, String signature, Action<? super Gradle> action) {
        assertProjectMutatingMethodAllowed(signature);
        buildListenerBroadcast.add(methodName, decorate(registrationPoint, action));
    }

    private void registerBuildListener(String registrationPoint, String methodName, String signature, Closure closure) {
        assertProjectMutatingMethodAllowed(signature);
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch(methodName, decorate(registrationPoint, closure)));
    }

    private void assertProjectMutatingMethodAllowed(String methodName) {
        MutationGuards.of(crossProjectConfigurator).assertMutationAllowed(methodName, this, GradleLifecycle.class);
    }

    private Closure<?> decorate(String registrationPoint, Closure closure) {
        return listenerBuildOperationDecorator.decorate(registrationPoint, Cast.<Closure<?>>uncheckedNonnullCast(closure));
    }

    private <T> Action<T> decorate(String registrationPoint, Action<T> action) {
        return listenerBuildOperationDecorator.decorate(registrationPoint, action);
    }

    private void rootProject(String registrationPoint, Action<? super Project> action) {
        if (projectsLoaded) {
            assert rootProject != null;
            action.execute(rootProject);
        } else {
            // only need to decorate when this callback is delayed
            rootProjectActions.add(decorate(registrationPoint, action));
        }
    }

    private class IsolatedProjectActionsHost implements IsolatedProjectActions.Host {
        @Override
        public <T> ImmutableList<Isolatable<T>> isolateAll(Collection<T> actions) {
            return actions.stream()
                .map(isolatableFactory::isolate)
                .collect(ImmutableList.toImmutableList());
        }
    }
}
