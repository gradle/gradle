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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;

public class SingleParentCopySpec extends DefaultCopySpec {

    CopySpecResolver parentResolver;

    public SingleParentCopySpec(FileResolver resolver, Instantiator instantiator, CopySpecResolver parentResolver) {
        super(resolver, instantiator);
        this.parentResolver = parentResolver;
    }

    public CopySpecInternal addChild() {
        DefaultCopySpec child = new SingleParentCopySpec(fileResolver, instantiator, buildResolverRelativeToParent(parentResolver));
        childSpecs.add(child);
        return child;
    }

    protected CopySpecInternal addChildAtPosition(int position) {
        DefaultCopySpec child = instantiator.newInstance(SingleParentCopySpec.class, fileResolver, instantiator, buildResolverRelativeToParent(parentResolver));
        childSpecs.add(position, child);
        return child;
    }

    public boolean isCaseSensitive() {
        return buildResolverRelativeToParent(parentResolver).isCaseSensitive();
    }

    public boolean getIncludeEmptyDirs() {
        return buildResolverRelativeToParent(parentResolver).getIncludeEmptyDirs();
    }

    public DuplicatesStrategy getDuplicatesStrategy() {
        return buildResolverRelativeToParent(parentResolver).getDuplicatesStrategy();
    }

    public Integer getDirMode() {
        return buildResolverRelativeToParent(parentResolver).getDirMode();
    }

    public Integer getFileMode() {
        return buildResolverRelativeToParent(parentResolver).getFileMode();
    }

}
