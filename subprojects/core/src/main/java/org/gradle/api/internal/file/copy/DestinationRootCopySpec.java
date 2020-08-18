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
import org.gradle.internal.file.PathToFileResolver;

import javax.inject.Inject;
import java.io.File;

public class DestinationRootCopySpec extends DelegatingCopySpecInternal {

    private final PathToFileResolver fileResolver;
    private final CopySpecInternal delegate;

    private Object destinationDir;

    @Inject
    public DestinationRootCopySpec(PathToFileResolver fileResolver, CopySpecInternal delegate) {
        this.fileResolver = fileResolver;
        this.delegate = delegate;
    }

    @Override
    protected CopySpecInternal getDelegateCopySpec() {
        return delegate;
    }

    @Override
    public CopySpec into(Object destinationDir) {
        this.destinationDir = destinationDir;
        return this;
    }

    public File getDestinationDir() {
        return destinationDir == null ? null : fileResolver.resolve(destinationDir);
    }

    // TODO:configuration-cache - remove this
    public CopySpecInternal getDelegate() {
        return delegate;
    }
}
