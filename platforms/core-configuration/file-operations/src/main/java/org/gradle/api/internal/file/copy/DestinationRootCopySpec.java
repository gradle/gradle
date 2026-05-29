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
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.file.PathToFileResolver;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.api.internal.lambdas.SerializableLambdas.transformer;

public class DestinationRootCopySpec extends DelegatingCopySpecInternal {

    private final PathToFileResolver fileResolver;
    private final CopySpecInternal delegate;
    private final DirectoryProperty destinationDirectory;

    @Inject
    public DestinationRootCopySpec(PathToFileResolver fileResolver, FilePropertyFactory filePropertyFactory, CopySpecInternal delegate) {
        this.fileResolver = fileResolver;
        this.delegate = delegate;
        this.destinationDirectory = filePropertyFactory.newDirectoryProperty();
    }

    @Override
    protected CopySpecInternal getDelegateCopySpec() {
        return delegate;
    }

    @Override
    public CopySpec into(@Nullable Object destinationDir) {
        if (destinationDir == null) {
            destinationDirectory.set((Directory) null);
        } else if (destinationDir instanceof DirectoryProperty) {
            destinationDirectory.set((DirectoryProperty) destinationDir);
        } else if (destinationDir instanceof Directory) {
            destinationDirectory.set((Directory) destinationDir);
        } else if (destinationDir instanceof Provider) {
            destinationDirectory.fileProvider(((Provider<?>) destinationDir).map(transformer(value ->
                value instanceof FileSystemLocation
                    ? ((FileSystemLocation) value).getAsFile()
                    : fileResolver.resolve(value))));
        } else {
            // Resolve all other notations (String, File, Closure, Callable, ...) lazily, preserving legacy behavior.
            destinationDirectory.fileProvider(Providers.changing(() -> fileResolver.resolve(destinationDir)));
        }
        return this;
    }

    /**
     * The lazy view of the destination directory, backing both {@link #into(Object)} and {@link #getDestinationDir()}.
     */
    public DirectoryProperty getDestinationDirectory() {
        return destinationDirectory;
    }

    @Override
    @Nullable
    public File getDestinationDir() {
        return destinationDirectory.getAsFile().getOrNull();
    }

    // TODO:configuration-cache - remove this
    public CopySpecInternal getDelegate() {
        return delegate;
    }
}
