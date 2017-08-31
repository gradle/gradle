/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.composite.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutions;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.api.tasks.TaskReference;
import org.gradle.initialization.GradleLauncher;
import org.gradle.internal.Factory;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;

import java.io.File;
import java.util.List;

public class DefaultIncludedBuild implements IncludedBuildInternal {
    private final File projectDir;
    private final Factory<GradleLauncher> gradleLauncherFactory;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final WorkerLeaseRegistry.WorkerLease parentLease;
    private final List<Action<? super DependencySubstitutions>> dependencySubstitutionActions = Lists.newArrayList();

    private DefaultDependencySubstitutions dependencySubstitutions;

    private GradleLauncher gradleLauncher;
    private String name;

    public DefaultIncludedBuild(File projectDir, Factory<GradleLauncher> launcherFactory, ImmutableModuleIdentifierFactory moduleIdentifierFactory, WorkerLeaseRegistry.WorkerLease parentLease) {
        this.projectDir = projectDir;
        this.gradleLauncherFactory = launcherFactory;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.parentLease = parentLease;
    }

    public File getProjectDir() {
        return projectDir;
    }

    @Override
    public TaskReference task(String path) {
        Preconditions.checkArgument(path.startsWith(":"), "Task path '%s' is not a qualified task path (e.g. ':task' or ':project:task').", path);
        return new IncludedBuildTaskReference(getName(), path);
    }

    @Override
    public String getName() {
        if (name == null) {
            name = getLoadedSettings().getRootProject().getName();
        }
        return name;
    }

    @Override
    public void dependencySubstitution(Action<? super DependencySubstitutions> action) {
        if (dependencySubstitutions != null) {
            throw new IllegalStateException("Cannot configure included build after dependency substitutions are resolved.");
        }
        dependencySubstitutionActions.add(action);
    }

    public DependencySubstitutionsInternal resolveDependencySubstitutions() {
        if (dependencySubstitutions == null) {
            dependencySubstitutions = DefaultDependencySubstitutions.forIncludedBuild(this, moduleIdentifierFactory);

            for (Action<? super DependencySubstitutions> action : dependencySubstitutionActions) {
                action.execute(dependencySubstitutions);
            }
        }
        return dependencySubstitutions;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        return getGradleLauncher().getLoadedSettings();
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        return getGradleLauncher().getConfiguredBuild();
    }

    @Override
    public void finishBuild() {
        getGradleLauncher().finishBuild();
    }

    public synchronized void addTasks(Iterable<String> taskPaths) {
        getGradleLauncher().scheduleTasks(taskPaths);
    }

    private GradleLauncher getGradleLauncher() {
        if (gradleLauncher == null) {
            gradleLauncher = gradleLauncherFactory.create();
        }
        return gradleLauncher;
    }

    @Override
    public synchronized void execute(final Iterable<String> tasks, final Object listener) {
        final GradleLauncher launcher = getGradleLauncher();
        launcher.addListener(listener);
        launcher.scheduleTasks(tasks);
        WorkerLeaseService workerLeaseService = gradleLauncher.getGradle().getServices().get(WorkerLeaseService.class);
        try {
            workerLeaseService.withSharedLease(parentLease, new Runnable() {
                @Override
                public void run() {
                    launcher.executeTasks();
                }
            });
        } finally {
            markAsNotReusable();
        }
    }

    private void markAsNotReusable() {
        gradleLauncher = null;
    }

    @Override
    public String toString() {
        return String.format("includedBuild[%s]", projectDir.getName());
    }
}
