/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file;

import org.gradle.api.Project;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.file.FileCollection;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

import groovy.lang.Closure;

/**
 * A {@link org.gradle.api.file.FileCollection} which resolves a set of paths relative to a {@link Project}.
 */
public class PathResolvingFileCollection extends AbstractFileCollection {
    private final List<Object> files;
    private final FileResolver resolver;

    public PathResolvingFileCollection(FileResolver resolver, Object... files) {
        this.resolver = resolver;
        this.files = Arrays.asList(files);
    }

    public String getDisplayName() {
        return "file collection";
    }

    public Set<File> getFiles() {
        Set<File> result = new LinkedHashSet<File>();
        List<Object> flattened = new ArrayList<Object>();
        GUtil.flatten(files, flattened);
        for (Object element : flattened) {
            if (element instanceof Closure) {
                Closure closure = (Closure) element;
                Object closureResult = closure.call();
                if (closureResult instanceof Collection) {
                    for (Object nested : (Collection<?>) closureResult) {
                        result.add(resolver.resolve(nested));
                    }
                } else {
                    result.add(resolver.resolve(closureResult));
                }
            } else if (element instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) element;
                result.addAll(fileCollection.getFiles());
            } else {
                result.add(resolver.resolve(element));
            }
        }
        return result;
    }
}
