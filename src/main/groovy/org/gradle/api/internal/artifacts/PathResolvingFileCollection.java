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
package org.gradle.api.internal.artifacts;

import org.gradle.api.Project;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

import groovy.lang.Closure;

/**
 * A {@link org.gradle.api.artifacts.FileCollection} which resolves a set of paths relative to a {@link Project}.
 */
public class PathResolvingFileCollection extends AbstractFileCollection {
    private final List<Object> files;
    private final Project project;

    public PathResolvingFileCollection(Project project, Object... files) {
        this.project = project;
        this.files = new ArrayList<Object>();
        GUtil.flatten(files, this.files);
    }

    public String getDisplayName() {
        return "file collection";
    }

    public Set<File> getFiles() {
        Set<File> result = new LinkedHashSet<File>();
        for (Object element : files) {
            if (element instanceof Closure) {
                Closure closure = (Closure) element;
                Object closureResult = closure.call();
                if (closureResult instanceof Collection) {
                    for (Object nested : (Collection<?>) closureResult) {
                        result.add(project.file(nested));
                    }
                } else {
                    result.add(project.file(closureResult));
                }
            } else {
                result.add(project.file(element));
            }
        }
        return result;
    }
}
