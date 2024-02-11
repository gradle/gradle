/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyCollector;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dependencies.AbstractModuleDependency;
import org.gradle.api.internal.provider.DefaultSetProperty;
import org.gradle.api.internal.provider.PropertyInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class DefaultDependencyCollector implements DependencyCollector {
    private final DependencyFactoryInternal dependencyFactory;

    @Inject
    public DefaultDependencyCollector(DependencyFactoryInternal dependencyFactory) {
        this.dependencyFactory = dependencyFactory;
        this.getDependencies().finalizeValueOnRead();
        this.getDependencyConstraints().finalizeValueOnRead();
    }

    @Nested
    @Override
    public abstract SetProperty<Dependency> getDependencies();

    @Nested
    @Override
    public abstract SetProperty<DependencyConstraint> getDependencyConstraints();

    @SuppressWarnings("unchecked")
    private <D extends Dependency> D ensureMutable(D dependency) {
        // Only done to make MinimalExternalModuleDependency mutable for configuration
        return (D) dependencyFactory.createDependency(dependency);
    }

    private <D extends Dependency> D applyConfiguration(D dependency, @Nullable Action<? super D> config) {
        D mutable = ensureMutable(dependency);
        if (config != null) {
            config.execute(mutable);
        }

        if (mutable instanceof AbstractModuleDependency) {
            ((AbstractModuleDependency) mutable).addMutationValidator(validator -> {
                if (((PropertyInternal<?>) getDependencies()).isFinalized()) {
                    DeprecationLogger.deprecateAction("Mutating dependency " + mutable + " after it has been finalized")
                        .willBecomeAnErrorInGradle9()
                        .withUpgradeGuideSection(8, "dependency_mutate_dependency_collector_after_finalize")
                        .nagUser();
                }
            });
        }

        return mutable;
    }

    private <D extends Dependency> void doAddEager(D dependency, @Nullable Action<? super D> config) {
        getDependencies().add(applyConfiguration(dependency, config));
    }

    private <D extends Dependency> void doAddLazy(Provider<D> dependency, @Nullable Action<? super D> config) {
        Provider<D> provider = dependency.map(dep -> applyConfiguration(dep, config));
        DefaultSetProperty.addOptionalProvider(getDependencies(), provider);
    }

    private <D extends Dependency> List<Dependency> createDependencyList(Iterable<? extends D> bundle, @Nullable Action<? super D> config) {
        List<Dependency> newList = new ArrayList<>(
            bundle instanceof Collection<?> ? ((Collection<?>) bundle).size() : 0
        );
        for (D dep : bundle) {
            newList.add(applyConfiguration(dep, config));
        }
        return newList;
    }

    private <D extends Dependency> void doAddBundleEager(Iterable<? extends D> bundle, @Nullable Action<? super D> config) {
        List<Dependency> dependencies = createDependencyList(bundle, config);
        getDependencies().addAll(dependencies);
    }

    private <D extends Dependency> void doAddBundleLazy(Provider<? extends Iterable<? extends D>> dependency, @Nullable Action<? super D> config) {
        Provider<List<Dependency>> provider = dependency.map(bundle -> createDependencyList(bundle, config));
        getDependencies().addAll(provider);
    }

    @Override
    public void add(CharSequence dependencyNotation) {
        doAddEager(dependencyFactory.create(dependencyNotation), null);
    }

    @Override
    public void add(CharSequence dependencyNotation, Action<? super ExternalModuleDependency> configuration) {
        doAddEager(dependencyFactory.create(dependencyNotation), configuration);
    }

    @Override
    public void add(FileCollection files) {
        doAddEager(dependencyFactory.create(files), null);
    }

    @Override
    public void add(FileCollection files, Action<? super FileCollectionDependency> configuration) {
        doAddEager(dependencyFactory.create(files), configuration);
    }

    @Override
    public void add(ProviderConvertible<? extends MinimalExternalModuleDependency> externalModule) {
        doAddLazy(externalModule.asProvider(), null);
    }

    @Override
    public void add(ProviderConvertible<? extends MinimalExternalModuleDependency> externalModule, Action<? super ExternalModuleDependency> configuration) {
        doAddLazy(externalModule.asProvider(), configuration);
    }

    @Override
    public void add(Dependency dependency) {
        doAddEager(dependency, null);
    }

    @Override
    public <D extends Dependency> void add(D dependency, Action<? super D> configuration) {
        doAddEager(dependency, configuration);
    }

    @Override
    public void add(Provider<? extends Dependency> dependency) {
        doAddLazy(dependency, null);
    }

    @Override
    public <D extends Dependency> void add(Provider<? extends D> dependency, Action<? super D> configuration) {
        doAddLazy(dependency, configuration);
    }


    private static DependencyConstraint applyConstraintConfiguration(DependencyConstraint dependencyConstraint, @Nullable Action<? super DependencyConstraint> config) {
        if (config != null) {
            config.execute(dependencyConstraint);
        }

        // TODO Use DependencyConstraint mutation validator https://github.com/gradle/gradle/issues/27904

        return dependencyConstraint;
    }

    private void doAddConstraintEager(DependencyConstraint dependencyConstraint, @Nullable Action<? super DependencyConstraint> config) {
        getDependencyConstraints().add(applyConstraintConfiguration(dependencyConstraint, config));
    }

    private void doAddConstraintLazy(Provider<? extends DependencyConstraint> dependencyConstraint, @Nullable Action<? super DependencyConstraint> config) {
        Provider<DependencyConstraint> provider = dependencyConstraint.map(dep -> applyConstraintConfiguration(dep, config));
        DefaultSetProperty.addOptionalProvider(getDependencyConstraints(), provider);
    }

    @Override
    public void addConstraint(DependencyConstraint dependencyConstraint) {
        doAddConstraintEager(dependencyConstraint, null);
    }

    @Override
    public void addConstraint(DependencyConstraint dependencyConstraint, Action<? super DependencyConstraint> configuration) {
        doAddConstraintEager(dependencyConstraint, configuration);
    }

    @Override
    public void addConstraint(Provider<? extends DependencyConstraint> dependencyConstraint) {
        doAddConstraintLazy(dependencyConstraint, null);
    }

    @Override
    public void addConstraint(Provider<? extends DependencyConstraint> dependencyConstraint, Action<? super DependencyConstraint> configuration) {
        doAddConstraintLazy(dependencyConstraint, configuration);
    }

    @Override
    public <D extends Dependency> void bundle(Iterable<? extends D> bundle) {
        doAddBundleEager(bundle, null);
    }

    @Override
    public <D extends Dependency> void bundle(Iterable<? extends D> bundle, Action<? super D> configuration) {
        doAddBundleEager(bundle, configuration);
    }

    @Override
    public <D extends Dependency> void bundle(Provider<? extends Iterable<? extends D>> bundle) {
        doAddBundleLazy(bundle, null);
    }

    @Override
    public <D extends Dependency> void bundle(Provider<? extends Iterable<? extends D>> bundle, Action<? super D> configuration) {
        doAddBundleLazy(bundle, configuration);
    }

    @Override
    public <D extends Dependency> void bundle(ProviderConvertible<? extends Iterable<? extends D>> bundle) {
        doAddBundleLazy(bundle.asProvider(), null);
    }

    @Override
    public <D extends Dependency> void bundle(ProviderConvertible<? extends Iterable<? extends D>> bundle, Action<? super D> configuration) {
        doAddBundleLazy(bundle.asProvider(), configuration);
    }
}
