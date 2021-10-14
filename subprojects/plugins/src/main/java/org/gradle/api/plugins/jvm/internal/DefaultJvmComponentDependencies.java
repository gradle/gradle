/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.catalog.DependencyBundleValueSource;
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.JvmComponentDependencies;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.api.provider.ValueSource;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultJvmComponentDependencies implements JvmComponentDependencies {
    private final Configuration implementation;
    private final Configuration compileOnly;
    private final Configuration runtimeOnly;

    @Inject
    public DefaultJvmComponentDependencies(Configuration implementation, Configuration compileOnly, Configuration runtimeOnly) {
        this.implementation = implementation;
        this.compileOnly = compileOnly;
        this.runtimeOnly = runtimeOnly;
    }

    @Inject
    protected DependencyHandler getDependencyHandler() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void implementation(Object dependencyNotation) {
        doAdd(implementation, dependencyNotation);
    }

    @Override
    public void implementation(Object dependencyNotation, Action<? super Dependency> configuration) {
        doAdd(implementation, dependencyNotation, configuration);
    }

    @Override
    public void runtimeOnly(Object dependencyNotation) {
        doAdd(runtimeOnly, dependencyNotation);
    }

    @Override
    public void runtimeOnly(Object dependencyNotation, Action<? super Dependency> configuration) {
        doAdd(runtimeOnly, dependencyNotation, configuration);
    }

    @Override
    public void compileOnly(Object dependencyNotation) {
        doAdd(compileOnly, dependencyNotation);
    }

    @Override
    public void compileOnly(Object dependencyNotation, Action<? super Dependency> configuration) {
        doAdd(compileOnly, dependencyNotation, configuration);
    }

    private void doAdd(Configuration bucket, Object dependencyNotation) {
        doAdd(bucket, dependencyNotation, null);
    }

    private void doAdd(Configuration bucket, Object dependencyNotation, @Nullable Action<? super Dependency> configuration) {
        if (dependencyNotation instanceof ProviderConvertible<?>) {
            doAddLazy(bucket, ((ProviderConvertible<?>) dependencyNotation).asProvider(), configuration);
        } else if (dependencyNotation instanceof Provider<?>) {
            doAddLazy(bucket, (Provider<?>) dependencyNotation, configuration);
        } else {
            doAddEager(bucket, dependencyNotation, configuration);
        }
    }

    private void doAddEager(Configuration bucket, Object dependencyNotation, @Nullable Action<? super Dependency> configuration) {
        Dependency dependency = create(dependencyNotation, configuration);
        bucket.getDependencies().add(dependency);
    }

    private void doAddLazy(Configuration bucket, Provider<?> dependencyProvider, @Nullable Action<? super Dependency> configuration) {
        if (dependencyProvider instanceof DefaultValueSourceProviderFactory.ValueSourceProvider) {
            Class<? extends ValueSource<?, ?>> valueSourceType = ((DefaultValueSourceProviderFactory.ValueSourceProvider<?, ?>) dependencyProvider).getValueSourceType();
            if (valueSourceType.isAssignableFrom(DependencyBundleValueSource.class)) {
                doAddListProvider(bucket, dependencyProvider, configuration);
                return;
            }
        }
        Provider<Dependency> lazyDependency = dependencyProvider.map(mapDependencyProvider(bucket, configuration));
        bucket.getDependencies().addLater(lazyDependency);
    }

    private void doAddListProvider(Configuration bucket, Provider<?> dependencyNotation, @Nullable Action<? super Dependency> configuration) {
        // workaround for the fact that mapping to a list will not create a `CollectionProviderInternal`
        ListProperty<Dependency> dependencies = getObjectFactory().listProperty(Dependency.class);
        dependencies.set(dependencyNotation.map(notation -> {
            List<Dependency> deps = Cast.uncheckedCast(notation);
            return deps.stream().map(d -> create(d, configuration)).collect(Collectors.toList());
        }));
        bucket.getDependencies().addAllLater(dependencies);
    }

    private <T> Transformer<Dependency, T> mapDependencyProvider(Configuration bucket, @Nullable Action<? super Dependency> configuration) {
        return lazyNotation -> {
            if (lazyNotation instanceof Configuration) {
                throw new InvalidUserDataException("Adding a configuration as a dependency using a provider isn't supported. You should call " + bucket.getName() + ".extendsFrom(" + ((Configuration) lazyNotation).getName() + ") instead");
            }
            return create(lazyNotation, configuration);
        };
    }

    private Dependency create(Object dependencyNotation, @Nullable Action<? super Dependency> configuration) {
        Dependency dependency = getDependencyHandler().create(dependencyNotation);
        if (configuration != null) {
            configuration.execute(dependency);
        }
        return dependency;
    }
}
