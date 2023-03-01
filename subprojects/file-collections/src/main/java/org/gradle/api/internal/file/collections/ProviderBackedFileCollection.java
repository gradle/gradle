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
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.ProviderResolutionStrategy;
import org.gradle.api.internal.provider.ValueSupplier;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;

import java.util.function.Consumer;

public class ProviderBackedFileCollection extends CompositeFileCollection {
    private final ProviderInternal<?> provider;
    private final PathToFileResolver resolver;
    private final ProviderResolutionStrategy providerResolutionStrategy;

    public ProviderBackedFileCollection(ProviderInternal<?> provider, PathToFileResolver resolver, TaskDependencyFactory taskDependencyFactory, Factory<PatternSet> patternSetFactory, ProviderResolutionStrategy providerResolutionStrategy) {
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
            producer.visitProducerTasks(context);
        } else {
            // Producer is unknown, so unpack the value
            UnpackingVisitor unpackingVisitor = new UnpackingVisitor(context::add, resolver, taskDependencyFactory, patternSetFactory);
            unpackingVisitor.add(providerResolutionStrategy.resolve(provider));
        }
    }

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        UnpackingVisitor unpackingVisitor = new UnpackingVisitor(visitor, resolver, taskDependencyFactory, patternSetFactory);
        unpackingVisitor.add(providerResolutionStrategy.resolve(provider));
    }

    public ProviderInternal<?> getProvider() {
        return provider;
    }
}
