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

@SuppressWarnings("deprecation")
public class NativeBinaryRequirementResolveResult {
    private Object input;
    private org.gradle.nativeplatform.NativeLibraryRequirement requirement;
    private org.gradle.nativeplatform.NativeLibraryBinary libraryBinary;
    private org.gradle.nativeplatform.NativeDependencySet nativeDependencySet;

    public NativeBinaryRequirementResolveResult(Object input) {
        this.input = input;
    }

    public Object getInput() {
        return input;
    }

    public void setRequirement(org.gradle.nativeplatform.NativeLibraryRequirement requirement) {
        this.requirement = requirement;
    }

    public org.gradle.nativeplatform.NativeLibraryRequirement getRequirement() {
        return requirement;
    }

    public org.gradle.nativeplatform.NativeLibraryBinary getLibraryBinary() {
        return libraryBinary;
    }

    public void setLibraryBinary(org.gradle.nativeplatform.NativeLibraryBinary libraryBinary) {
        this.libraryBinary = libraryBinary;
    }

    public org.gradle.nativeplatform.NativeDependencySet getNativeDependencySet() {
        return nativeDependencySet;
    }

    public void setNativeDependencySet(org.gradle.nativeplatform.NativeDependencySet nativeDependencySet) {
        this.nativeDependencySet = nativeDependencySet;
    }

    public boolean isComplete() {
        return nativeDependencySet != null;
    }
}
