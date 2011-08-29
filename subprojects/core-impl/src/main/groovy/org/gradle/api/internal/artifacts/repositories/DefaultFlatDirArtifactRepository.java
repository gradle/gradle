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

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.dsl.FlatDirectoryArtifactRepository;
import org.gradle.api.internal.artifacts.ivyservice.LocalFileRepositoryCacheManager;
import org.gradle.api.internal.file.FileResolver;

import java.io.File;
import java.util.*;

public class DefaultFlatDirArtifactRepository implements FlatDirectoryArtifactRepository, ArtifactRepositoryInternal {
    private final FileResolver fileResolver;
    private String name;
    private List<Object> dirs = new ArrayList<Object>();

    public DefaultFlatDirArtifactRepository(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<File> getDirs() {
        return fileResolver.resolveFiles(dirs).getFiles();
    }

    public void setDirs(Iterable<?> dirs) {
        this.dirs = new ArrayList<Object>(Arrays.asList(dirs));
    }

    public void dirs(Object... dirs) {
        this.dirs.addAll(Arrays.asList(dirs));
    }

    public void createResolvers(Collection<DependencyResolver> resolvers) {
        Set<File> dirs = getDirs();
        if (dirs.isEmpty()) {
            throw new InvalidUserDataException("You must specify at least one directory for a flat directory repository.");
        }

        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName(name);
        for (File root : dirs) {
            resolver.addArtifactPattern(root.getAbsolutePath() + "/[artifact]-[revision](-[classifier]).[ext]");
            resolver.addArtifactPattern(root.getAbsolutePath() + "/[artifact](-[classifier]).[ext]");
        }
        resolver.setValidate(false);
        resolver.setRepositoryCacheManager(new LocalFileRepositoryCacheManager(name));
        resolvers.add(resolver);
    }
}
