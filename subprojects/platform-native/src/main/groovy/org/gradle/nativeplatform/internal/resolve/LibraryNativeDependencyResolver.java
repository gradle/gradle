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

package org.gradle.nativeplatform.internal.resolve;

import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.NativeLibraryBinary;

public class LibraryNativeDependencyResolver implements NativeDependencyResolver {
    private final LibraryBinaryLocator libraryBinaryLocator;
    private final Instantiator instantiator;

    public LibraryNativeDependencyResolver(final LibraryBinaryLocator locator, Instantiator instantiator) {
        libraryBinaryLocator = locator;
        this.instantiator = instantiator;
    }

    public void resolve(NativeBinaryResolveResult resolution) {
        for (NativeBinaryRequirementResolveResult requirementResolution : resolution.getPendingResolutions()) {
            DefaultLibraryResolver libraryResolver = new DefaultLibraryResolver(libraryBinaryLocator, instantiator, requirementResolution.getRequirement(), resolution.getTarget());
            NativeLibraryBinary libraryBinary = libraryResolver.resolveLibraryBinary();
            requirementResolution.setLibraryBinary(libraryBinary);
            requirementResolution.setNativeDependencySet(new DefaultNativeDependencySet(libraryBinary));
        }
    }
}
