/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;

public class SingleParentCopySpec extends DefaultCopySpec {

    private final CopySpecResolver parentResolver;

    private final PropertyFactory propertyFactory;

    public SingleParentCopySpec(FileCollectionFactory fileCollectionFactory, PropertyFactory propertyFactory, Instantiator instantiator, Factory<PatternSet> patternSetFactory, CopySpecResolver parentResolver) {
        super(fileCollectionFactory, propertyFactory, instantiator, patternSetFactory);
        this.parentResolver = parentResolver;
        this.propertyFactory = propertyFactory;
        getFilePermissions().convention(parentResolver.getFilePermissions());
        getDirPermissions().convention(parentResolver.getDirPermissions());
    }

    @Override
    public CopySpecInternal addChild() {
        DefaultCopySpec child = instantiator.newInstance(SingleParentCopySpec.class, fileCollectionFactory, propertyFactory, instantiator, patternSetFactory, buildResolverRelativeToParent(parentResolver));
        addChildSpec(child);
        return child;
    }

    @Override
    protected CopySpecInternal addChildAtPosition(int position) {
        DefaultCopySpec child = instantiator.newInstance(SingleParentCopySpec.class, fileCollectionFactory, propertyFactory, instantiator, patternSetFactory, buildResolverRelativeToParent(parentResolver));
        addChildSpec(position, child);
        return child;
    }

    @Override
    public boolean isCaseSensitive() {
        return buildResolverRelativeToParent(parentResolver).isCaseSensitive();
    }

    @Override
    public boolean getIncludeEmptyDirs() {
        return buildResolverRelativeToParent(parentResolver).getIncludeEmptyDirs();
    }

    @Override
    public DuplicatesStrategy getDuplicatesStrategy() {
        return buildResolverRelativeToParent(parentResolver).getDuplicatesStrategy();
    }

    @Override
    public String getFilteringCharset() {
        return buildResolverRelativeToParent(parentResolver).getFilteringCharset();
    }
}
