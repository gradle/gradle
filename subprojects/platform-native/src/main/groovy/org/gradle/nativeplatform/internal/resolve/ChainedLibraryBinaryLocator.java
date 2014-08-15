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

import org.gradle.api.DomainObjectSet;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.NativeLibraryRequirement;

import java.util.ArrayList;
import java.util.List;

public class ChainedLibraryBinaryLocator implements LibraryBinaryLocator {
    private final List<LibraryBinaryLocator> locators = new ArrayList<LibraryBinaryLocator>();

    public ChainedLibraryBinaryLocator(List<? extends LibraryBinaryLocator> locators) {
        this.locators.addAll(locators);
    }

    public DomainObjectSet<NativeLibraryBinary> getBinaries(NativeLibraryRequirement requirement) {
        List<Exception> failures = new ArrayList<Exception>();
        for (LibraryBinaryLocator locator : locators) {
            try {
                return locator.getBinaries(requirement);
            } catch (Exception e) {
                failures.add(e);
            }
        }
        throw new LibraryResolveException(getFailureMessage(requirement), failures);
    }

    private String getFailureMessage(NativeLibraryRequirement requirement) {
        return requirement.getProjectPath() == null
                ? String.format("Could not locate library '%s'.", requirement.getLibraryName())
                : String.format("Could not locate library '%s' for project '%s'.", requirement.getLibraryName(), requirement.getProjectPath());
    }

}
