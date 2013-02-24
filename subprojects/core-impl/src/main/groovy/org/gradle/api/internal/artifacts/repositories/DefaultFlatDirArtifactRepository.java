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

import com.google.common.collect.Lists;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.internal.file.FileResolver;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class DefaultFlatDirArtifactRepository extends IvyResolverBackedArtifactRepository implements FlatDirectoryArtifactRepository, ArtifactRepositoryInternal {
    private final FileResolver fileResolver;
    private List<Object> dirs = new ArrayList<Object>();
    private final RepositoryCacheManager localCacheManager;

    public DefaultFlatDirArtifactRepository(FileResolver fileResolver, RepositoryCacheManager localCacheManager) {
        this.fileResolver = fileResolver;
        this.localCacheManager = localCacheManager;
    }

    public Set<File> getDirs() {
        return fileResolver.resolveFiles(dirs).getFiles();
    }

    public void setDirs(Iterable<?> dirs) {
        this.dirs = Lists.newArrayList(dirs);
    }

    public void dir(Object dir) {
        dirs(dir);
    }

    public void dirs(Object... dirs) {
        this.dirs.addAll(Arrays.asList(dirs));
    }

    public DependencyResolver createResolver() {
        Set<File> dirs = getDirs();
        if (dirs.isEmpty()) {
            throw new InvalidUserDataException("You must specify at least one directory for a flat directory repository.");
        }

        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName(getName());
        for (File root : dirs) {
            resolver.addArtifactPattern(root.getAbsolutePath() + "/[artifact]-[revision](-[classifier]).[ext]");
            resolver.addArtifactPattern(root.getAbsolutePath() + "/[artifact](-[classifier]).[ext]");
        }
        resolver.setValidate(false);
        resolver.setRepositoryCacheManager(localCacheManager);
        return resolver;
    }

}
