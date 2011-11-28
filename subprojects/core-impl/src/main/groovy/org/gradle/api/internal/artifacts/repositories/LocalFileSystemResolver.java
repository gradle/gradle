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
package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.LocalFileRepositoryCacheManager;

public class LocalFileSystemResolver extends FileSystemResolver {

    private static final String FILE_URL_PREFIX = "file:";

    public LocalFileSystemResolver(String name) {
        setRepositoryCacheManager(new LocalFileRepositoryCacheManager(name));
    }

    @Override
    public void addArtifactPattern(String pattern) {
        super.addArtifactPattern(getFilePath(pattern));
    }

    @Override
    public void addIvyPattern(String pattern) {
        super.addIvyPattern(getFilePath(pattern));
    }

    private String getFilePath(String pattern) {
        if (!pattern.startsWith(FILE_URL_PREFIX)) {
            throw new IllegalArgumentException("Can only handle URIs of type file");
        }
        return pattern.substring(FILE_URL_PREFIX.length());
    }
}
