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

    /**
     * Compares a dynamic revision (askedMrid) with a static one (foundMrid) to indicate which one
     * should be considered the greater. If there is not enough information to know which one is the
     * greater, the dynamic one should be considered greater and this method should return 0. This
     * method should never be called with a askdeMrid for which isDynamic returns false.
     *
     * @param askedMrid
     *            the dynamic revision to compare
     * @param foundMrid
     *            the static revision to compare
     * @param staticComparator
     *            a comparator which can be used to compare static revisions
     * @return 0 if it's not possible to know which one is greater, greater than 0 if askedMrid
     *         should be considered greater, lower than 0 if it can't be consider greater
     */
    public int compare(ModuleVersionIdentifier requested, ModuleVersionIdentifier found,
                       Comparator staticComparator);

    /**
     * Returns the version matcher name identifying this version matcher
     *
     * @return the version matcher name identifying this version matcher
     */
    public String getName();
}