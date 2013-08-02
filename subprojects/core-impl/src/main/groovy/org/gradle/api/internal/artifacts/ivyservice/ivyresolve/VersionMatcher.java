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

import java.util.Comparator;

// initially derived from org.apache.ivy.plugins.version.VersionMatcher
public interface VersionMatcher {
    /**
     * Indicates if the given asked ModuleRevisionId should be considered as dynamic for the current
     * VersionMatcher or not.
     */
    public boolean isDynamic(String version);

    /**
     * Indicates if this version matcher considers that the module revision found matches the asked
     * one.
     */
    public boolean accept(String requestedVersion, String foundVersion);

    /**
     * Indicates if this VersionMatcher needs module descriptors to determine if a module revision
     * matches the asked one. Note that returning true in this method may imply big performance
     * issues.
     */
    public boolean needModuleMetadata(String requestedVersion, String foundVersion);

    /**
     * Indicates if this version matcher considers that the module found matches the asked one. This
     * method can be called even needModuleDescriptor(ModuleRevisionId askedMrid, ModuleRevisionId
     * foundMrid) returns false, so it is required to implement it in any case, a usual default
     * implementation being: return accept(requestedVersion, foundVersion.getId().getVersion());
     */
    public boolean accept(String requestedVersion, ModuleVersionMetaData foundVersion);

    /**
     * Compares a dynamic revision (requestedVersion) with a static one (foundVersion) to indicate which one
     * should be considered the greater. If there is not enough information to know which one is the
     * greater, the dynamic one should be considered greater and this method should return 0. This
     * method should never be called with a askdeMrid for which isDynamic returns false.
     */
    public int compare(String requestedVersion, String foundVersion, Comparator<String> staticComparator);
}