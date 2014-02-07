/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.nativebinaries.internal.prebuilt.PrebuiltLibraryBinaryLocator;

import java.util.ArrayList;
import java.util.List;

public class NativeDependencyResolverServices {

    public ProjectLocator createProjectLocator(ProjectFinder projectFinder, DependencyMetaDataProvider metaDataProvider) {
        String currentProjectPath = metaDataProvider.getModule().getProjectPath();
        return new DefaultProjectLocator(currentProjectPath, projectFinder);
    }

    public LibraryBinaryLocator createLibraryBinaryLocator(ProjectLocator projectLocator) {
        List<LibraryBinaryLocator> locators = new ArrayList<LibraryBinaryLocator>();
        locators.add(new ProjectLibraryBinaryLocator(projectLocator));
        locators.add(new PrebuiltLibraryBinaryLocator(projectLocator));
        return new ChainedLibraryBinaryLocator(locators);
    }

    public NativeDependencyResolver createResolver(LibraryBinaryLocator locator) {
        NativeDependencyResolver resolver = new LibraryNativeDependencyResolver(locator);
        resolver = new ApiRequirementNativeDependencyResolver(resolver);
        resolver = new RequirementParsingNativeDependencyResolver(resolver);
        resolver = new SourceSetNativeDependencyResolver(resolver);
        return new InputHandlingNativeDependencyResolver(resolver);
    }

}
