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
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.file.PathToFileResolver;

import javax.inject.Inject;

public class DestinationRootCopySpec extends DelegatingCopySpecInternal {

    private final PathToFileResolver fileResolver;
    private final CopySpecInternal delegate;
    private final DirectoryProperty destinationDir;

    @Inject
    public DestinationRootCopySpec(PathToFileResolver fileResolver, ObjectFactory objectFactory, CopySpecInternal delegate) {
        this.fileResolver = fileResolver;
        this.destinationDir = objectFactory.directoryProperty();
        this.delegate = delegate;
    }

    @Override
    protected CopySpecInternal getDelegateCopySpec() {
        return delegate;
    }

    @Override
    public CopySpec into(Object destinationDir) {
        if (destinationDir instanceof Provider) {
            getDestinationDir().fileProvider(((Provider<?>) destinationDir).map(file -> {
                if (file instanceof FileSystemLocation) {
                    return ((FileSystemLocation) file).getAsFile();
                } else {
                    return fileResolver.resolve(file);
                }
            }));
        } else {
            getDestinationDir().fileProvider(Providers.changing(() -> fileResolver.resolve(destinationDir)));
        }
        return this;
    }

    @Override
    public DirectoryProperty getDestinationDir() {
        return destinationDir;
    }

    // TODO:configuration-cache - remove this
    public CopySpecInternal getDelegate() {
        return delegate;
    }
}
