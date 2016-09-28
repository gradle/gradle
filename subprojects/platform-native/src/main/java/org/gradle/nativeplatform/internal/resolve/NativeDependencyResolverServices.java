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
package org.gradle.nativeplatform.internal.resolve;

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.nativeplatform.internal.prebuilt.PrebuiltLibraryBinaryLocator;

import java.util.ArrayList;
import java.util.List;

public class NativeDependencyResolverServices {
    public LibraryBinaryLocator createLibraryBinaryLocator(ProjectModelResolver projectModelResolver) {
        List<LibraryBinaryLocator> locators = new ArrayList<LibraryBinaryLocator>();
        locators.add(new ProjectLibraryBinaryLocator(projectModelResolver));
        locators.add(new PrebuiltLibraryBinaryLocator(projectModelResolver));
        return new CachingLibraryBinaryLocator(new ChainedLibraryBinaryLocator(locators));
    }

    public NativeDependencyResolver createResolver(LibraryBinaryLocator locator, FileCollectionFactory fileCollectionFactory) {
        NativeDependencyResolver resolver = new LibraryNativeDependencyResolver(locator);
        resolver = new ApiRequirementNativeDependencyResolver(resolver, fileCollectionFactory);
        resolver = new RequirementParsingNativeDependencyResolver(resolver);
        resolver = new SourceSetNativeDependencyResolver(resolver, fileCollectionFactory);
        return new InputHandlingNativeDependencyResolver(resolver);
    }
}
