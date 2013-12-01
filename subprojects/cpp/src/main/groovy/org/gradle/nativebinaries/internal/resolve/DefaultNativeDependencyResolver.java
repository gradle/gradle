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

package org.gradle.nativebinaries.internal.resolve;

import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.nativebinaries.NativeDependencySet;
import org.gradle.nativebinaries.NativeLibraryRequirement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultNativeDependencyResolver implements NativeDependencyResolver {
    private final RelativeProjectFinder projectFinder;
    private final NotationParser<Object, NativeLibraryRequirement> parser;

    public DefaultNativeDependencyResolver(final ProjectInternal project) {
        projectFinder = new RelativeProjectFinder(project);
        parser = NativeDependencyNotationParser.parser();
    }

    public Collection<NativeDependencySet> resolve(NativeBinary target, Collection<?> libs) {
        List<NativeDependencySet> result = new ArrayList<NativeDependencySet>();
        for (Object lib : libs) {
            result.add(resolve(target, lib));
        }
        return result;
    }

    private NativeDependencySet resolve(NativeBinary target, Object lib) {
        if (lib instanceof NativeDependencySet) {
            return (NativeDependencySet) lib;
        }
        NativeLibraryRequirement libraryDependency = parser.parseNotation(lib);
        LibraryBinaryLocator registry = new LibraryBinaryLocator(projectFinder);
        LibraryResolver libraryResolver = new DefaultLibraryResolver(registry, libraryDependency, target);
        return new DeferredResolutionLibraryNativeDependencySet(libraryResolver);
    }

    private static class RelativeProjectFinder implements ProjectFinder {
        private final ProjectInternal project;

        public RelativeProjectFinder(ProjectInternal project) {
            this.project = project;
        }

        public ProjectInternal getProject(String path) {
            if (path == null || path.length() == 0) {
                return project;
            }
            return project.findProject(path);
        }
    }
}
