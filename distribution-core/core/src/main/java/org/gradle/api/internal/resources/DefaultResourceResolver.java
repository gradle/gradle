/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.resources;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.resources.internal.LocalResourceAdapter;
import org.gradle.api.resources.internal.ReadableResourceInternal;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.resource.local.LocalFileStandInExternalResource;

public class DefaultResourceResolver implements ResourceResolver {
    private final FileResolver fileResolver;
    private final FileSystem fileSystem;

    public DefaultResourceResolver(FileResolver fileResolver, FileSystem fileSystem) {
        this.fileResolver = fileResolver;
        this.fileSystem = fileSystem;
    }

    @Override
    public ReadableResourceInternal resolveResource(Object path) {
        if (path instanceof ReadableResourceInternal) {
            return (ReadableResourceInternal) path;
        }
        return new LocalResourceAdapter(new LocalFileStandInExternalResource(fileResolver.resolve(path), fileSystem));
    }
}
