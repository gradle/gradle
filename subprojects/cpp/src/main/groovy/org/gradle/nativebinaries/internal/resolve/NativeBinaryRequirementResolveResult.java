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

import org.gradle.nativebinaries.LibraryBinary;
import org.gradle.nativebinaries.NativeDependencySet;
import org.gradle.nativebinaries.NativeLibraryRequirement;
import org.gradle.nativebinaries.internal.LibraryBinaryInternal;

public class NativeBinaryRequirementResolveResult {
    private Object input;
    private NativeLibraryRequirement requirement;
    private LibraryBinaryInternal libraryBinary;
    private NativeDependencySet nativeDependencySet;

    public NativeBinaryRequirementResolveResult(Object input) {
        this.input = input;
    }

    public Object getInput() {
        return input;
    }

    public void setRequirement(NativeLibraryRequirement requirement) {
        this.requirement = requirement;
    }

    public NativeLibraryRequirement getRequirement() {
        return requirement;
    }

    public LibraryBinary getLibraryBinary() {
        return libraryBinary;
    }

    public void setLibraryBinary(LibraryBinaryInternal libraryBinary) {
        this.libraryBinary = libraryBinary;
    }

    public NativeDependencySet getNativeDependencySet() {
        return nativeDependencySet;
    }

    public void setNativeDependencySet(NativeDependencySet nativeDependencySet) {
        this.nativeDependencySet = nativeDependencySet;
    }

    public boolean isComplete() {
        return nativeDependencySet != null;
    }
}
