/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.internal.file.MaybeCompressedFileResource;
import org.gradle.api.internal.file.archive.compression.Bzip2Archiver;
import org.gradle.api.internal.file.archive.compression.GzipArchiver;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceHandler;

public class DefaultResourceHandler implements ResourceHandler {
    private final FileResolver resolver;

    public DefaultResourceHandler(FileResolver resolver) {
        this.resolver = resolver;
    }

    public ReadableResource gzip(Object path) {
        return new GzipArchiver(resolver.resolveResource(path));
    }

    public ReadableResource bzip2(Object path) {
        return new Bzip2Archiver(resolver.resolveResource(path));
    }

    //this method is not on the interface, at least for now
    public ReadableResource maybeCompressed(Object tarPath) {
        if (tarPath instanceof ReadableResource) {
            return (ReadableResource) tarPath;
        } else {
            return new MaybeCompressedFileResource(resolver.resolveResource(tarPath));
        }
    }
}