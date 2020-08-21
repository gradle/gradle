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
import org.gradle.api.internal.provider.ValueSupplier;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;

import java.util.function.Consumer;

public class ProviderBackedFileCollection extends CompositeFileCollection {
    private final ProviderInternal<?> provider;
    private final PathToFileResolver resolver;

    public ProviderBackedFileCollection(ProviderInternal<?> provider, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory) {
        super(patternSetFactory);
        this.provider = provider;
        this.resolver = resolver;
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
            super.visitDependencies(context);
        }
    }

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        UnpackingVisitor unpackingVisitor = new UnpackingVisitor(visitor, resolver, patternSetFactory);
        unpackingVisitor.add(provider.get());
    }

    public ProviderInternal<?> getProvider() {
        return provider;
    }
}
