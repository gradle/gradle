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

package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.gradle.plugin.use.PluginDependency;
import org.gradle.plugin.use.PluginDependencyProvider;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

public class DefaultPluginDependencyProvider implements PluginDependencyProvider {

    private final Provider<PluginDependency> delegate;
    private final Provider<PluginDependency> withoutVersion;

    public DefaultPluginDependencyProvider(Provider<PluginDependency> delegate, Provider<PluginDependency> withoutVersion) {
        this.delegate = delegate;
        this.withoutVersion = withoutVersion;
    }

    @Override
    public PluginDependency get() {
        return delegate.get();
    }

    @Nullable
    @Override
    public PluginDependency getOrNull() {
        return delegate.getOrNull();
    }

    @Override
    public PluginDependency getOrElse(PluginDependency defaultValue) {
        return delegate.getOrElse(defaultValue);
    }

    @Override
    public <S> Provider<S> map(Transformer<? extends S, ? super PluginDependency> transformer) {
        return delegate.map(transformer);
    }

    @Override
    public <S> Provider<S> flatMap(Transformer<? extends Provider<? extends S>, ? super PluginDependency> transformer) {
        return delegate.flatMap(transformer);
    }

    @Override
    public boolean isPresent() {
        return delegate.isPresent();
    }

    @Override
    public Provider<PluginDependency> orElse(PluginDependency value) {
        return delegate.orElse(value);
    }

    @Override
    public Provider<PluginDependency> orElse(Provider<? extends PluginDependency> provider) {
        return delegate.orElse(provider);
    }

    @Override
    @Deprecated
    public Provider<PluginDependency> forUseAtConfigurationTime() {
        return this;
    }

    @Override
    public <B, R> Provider<R> zip(Provider<B> right, BiFunction<PluginDependency, B, R> combiner) {
        return delegate.zip(right, combiner);
    }

    @Override
    public Provider<PluginDependency> getWithoutVersion() {
        return withoutVersion;
    }
}
