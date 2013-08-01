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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.util.Comparator;

// initially derived from org.apache.ivy.plugins.version.VersionMatcher
public interface VersionMatcher {
    /**
     * Indicates if the given asked ModuleRevisionId should be considered as dynamic for the current
     * VersionMatcher or not.
     */
    public boolean isDynamic(ModuleVersionIdentifier module);

    /**
     * Indicates if this version matcher considers that the module revision found matches the asked
     * one.
     */
    public boolean accept(ModuleVersionIdentifier requested, ModuleVersionIdentifier found);

    /**
     * Indicates if this VersionMatcher needs module descriptors to determine if a module revision
     * matches the asked one. Note that returning true in this method may imply big performance
     * issues.
     */
    public boolean needModuleMetadata(ModuleVersionIdentifier requested, ModuleVersionIdentifier found);

    /**
     * Indicates if this version matcher considers that the module found matches the asked one. This
     * method can be called even needModuleDescriptor(ModuleRevisionId askedMrid, ModuleRevisionId
     * foundMrid) returns false, so it is required to implement it in any case, a usual default
     * implementation being: return accept(askedMrid, foundMD.getResolvedModuleRevisionId());
     */
    public boolean accept(ModuleVersionIdentifier requested, ModuleVersionMetaData found);
}