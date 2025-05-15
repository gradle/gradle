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

import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.internal.file.PathToFileResolver;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;


public class DestinationRootCopySpec extends DelegatingCopySpecInternal {

    private final PathToFileResolver fileResolver;
    private final CopySpecInternal delegate;

    @Inject
    public DestinationRootCopySpec(PathToFileResolver fileResolver, CopySpecInternal delegate) {
        this.fileResolver = fileResolver;
        this.delegate = delegate;
    }

    @Override
    protected CopySpecInternal getDelegateCopySpec() {
        return delegate;
    }

    @Inject
    public DirectoryProperty getDestinationDirectory() {
        throw new UnsupportedOperationException("getDestinationDirProperty not supported");
    }

    @Override
    public CopySpec into(Object destinationDir) {
        getDestinationDirectory().files(destinationDir);
        return this;
    }

    @Override
    @Nullable
    public File getDestinationDir() {
        return getDestinationDirectory().map(fileResolver::resolve).getOrNull();
    }

    // TODO:configuration-cache - remove this
    public CopySpecInternal getDelegate() {
        return delegate;
    }
}
