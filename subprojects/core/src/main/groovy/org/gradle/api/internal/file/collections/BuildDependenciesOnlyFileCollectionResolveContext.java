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
package org.gradle.api.internal.file.collections;

import org.gradle.api.Buildable;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.tasks.TaskDependency;

import java.util.Collection;
import java.util.List;

/**
 * <p>A {@link FileCollectionResolveContext} which is used to determine the builder dependencies of a file collection hierarchy. Ignores the contents of file
 * collections when resolving, replacing each file collection with an empty tree with the appropriate build dependencies. This is generally more efficient than
 * resolving the file collections.
 *
 * <p>Nested contexts created by this context will similarly ignore the contents of file collections.
 */
public class BuildDependenciesOnlyFileCollectionResolveContext extends DefaultFileCollectionResolveContext {
    public BuildDependenciesOnlyFileCollectionResolveContext() {
        super(new IdentityFileResolver(), new BuildableFileTreeInternalConverter(), new BuildableFileTreeInternalConverter());
    }

    /**
     * Resolves the contents of this context as a list of atomic {@link Buildable} instances.
     */
    public List<? extends Buildable> resolveAsBuildables() {
        return resolveAsFileCollections();
    }

    private static class BuildableFileTreeInternalConverter implements Converter<FileTree> {
        public void convertInto(Object element, Collection<? super FileTree> result, FileResolver resolver) {
            if (element instanceof DefaultFileCollectionResolveContext) {
                DefaultFileCollectionResolveContext nestedContext = (DefaultFileCollectionResolveContext) element;
                result.addAll(nestedContext.resolveAsFileTrees());
            } else if (element instanceof Buildable) {
                Buildable buildable = (Buildable) element;
                result.add(new FileTreeAdapter(new EmptyFileTree(buildable.getBuildDependencies())));
            } else if (element instanceof TaskDependency) {
                TaskDependency dependency = (TaskDependency) element;
                result.add(new FileTreeAdapter(new EmptyFileTree(dependency)));
            }
        }
    }
}
