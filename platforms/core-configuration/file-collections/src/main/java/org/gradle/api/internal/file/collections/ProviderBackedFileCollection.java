/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.file.collections;

import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.ProviderResolutionStrategy;
import org.gradle.api.internal.provider.ValueSupplier;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.internal.PatternSetFactory;
import org.gradle.internal.file.PathToFileResolver;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public class ProviderBackedFileCollection extends CompositeFileCollection {
    private final ProviderInternal<?> provider;
    private final PathToFileResolver resolver;
    private final ProviderResolutionStrategy providerResolutionStrategy;

    public ProviderBackedFileCollection(ProviderInternal<?> provider, PathToFileResolver resolver, TaskDependencyFactory taskDependencyFactory, PatternSetFactory patternSetFactory, ProviderResolutionStrategy providerResolutionStrategy) {
        super(taskDependencyFactory, patternSetFactory);
        this.provider = provider;
        this.resolver = resolver;
        this.providerResolutionStrategy = providerResolutionStrategy;
    }

    @Override
    public String getDisplayName() {
        return "file collection";
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        ValueSupplier.ValueProducer producer = provider.getProducer();
        if (producer.isKnown()) {
            producer.visitDependencies(context);
        } else {
            // Producer is unknown, so unpack the value
            UnpackingVisitor unpackingVisitor = new UnpackingVisitor(context::add, resolver, taskDependencyFactory, patternSetFactory);
            unpackingVisitor.add(providerResolutionStrategy.resolve(provider));
        }
    }

    @Override
    protected void visitContents(FileCollectionStructureVisitor visitor) {
        visitChildren(child -> child.visitStructure(visitor), visitor.getValueConsumer());
    }

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        visitChildren(visitor, ValueSupplier.ValueConsumer.IgnoreUnsafeRead);
    }

    private void visitChildren(Consumer<FileCollectionInternal> visitor, ValueSupplier.ValueConsumer consumer) {
        UnpackingVisitor unpackingVisitor = new UnpackingVisitor(visitor, resolver, taskDependencyFactory, patternSetFactory);
        unpackingVisitor.add(resolveProvider(consumer));
    }

    @Nullable
    private Object resolveProvider(ValueSupplier.ValueConsumer consumer) {
        if (consumer == ValueSupplier.ValueConsumer.IgnoreUnsafeRead) {
            return providerResolutionStrategy.resolve(provider);
        }
        // Reading with the consumer finalizes the properties the provider value is derived from
        ValueSupplier.Value<?> value = provider.calculateValue(consumer);
        if (value.isMissing()) {
            // Let the strategy decide whether a missing value is an error, and report it the usual way
            return providerResolutionStrategy.resolve(provider);
        }
        return value.get();
    }

    public ProviderInternal<?> getProvider() {
        return provider;
    }

    public PathToFileResolver getResolver() {
        return resolver;
    }

    public ProviderResolutionStrategy getProviderResolutionStrategy() {
        return providerResolutionStrategy;
    }
}
