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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;

public class SingleParentCopySpec extends DefaultCopySpec {

    private final CopySpecResolver parentResolver;

    private final ObjectFactory objectFactory;

    public SingleParentCopySpec(FileCollectionFactory fileCollectionFactory, ObjectFactory objectFactory, Instantiator instantiator, Factory<PatternSet> patternSetFactory, CopySpecResolver parentResolver) {
        super(fileCollectionFactory, objectFactory, instantiator, patternSetFactory);
        this.parentResolver = parentResolver;
        this.objectFactory = objectFactory;
        getCaseSensitive().convention(parentResolver.getCaseSensitive());
        getFilePermissions().convention(parentResolver.getFilePermissions());
        getDirPermissions().convention(parentResolver.getDirPermissions());
    }

    @Override
    public CopySpecInternal addChild() {
        DefaultCopySpec child = new SingleParentCopySpec(fileCollectionFactory, objectFactory, instantiator, patternSetFactory, buildResolverRelativeToParent(parentResolver));
        addChildSpec(child);
        return child;
    }

    @Override
    protected CopySpecInternal addChildAtPosition(int position) {
        DefaultCopySpec child = instantiator.newInstance(SingleParentCopySpec.class, fileCollectionFactory, instantiator, patternSetFactory, buildResolverRelativeToParent(parentResolver));
        addChildSpec(position, child);
        return child;
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
