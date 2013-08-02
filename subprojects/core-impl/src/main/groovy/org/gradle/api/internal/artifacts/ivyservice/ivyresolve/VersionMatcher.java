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

/**
 * Compares version selectors against candidate versions, indicating
 * whether they match or not.
 *
 * <p>This interface was initially derived from {@code org.apache.ivy.plugins.version.VersionMatcher}.
 */
public interface VersionMatcher {
    /**
     * Indicates if the given version selector is recognized as dynamic by this version matcher.
     */
    public boolean isDynamic(String selector);

    /**
     * Indicates if the given version selector matches the given candidate version.
     */
    public boolean accept(String selector, String candidate);

    /**
     * Indicates if module metadata is required to determine if the given version
     * selector matches the given candidate version.
     */
    public boolean needModuleMetadata(String selector, String candidate);

    /**
     * Indicates if the given version selector matches the given given candidate version
     * (whose metadata is provided). This method may be called even if {@link #needModuleMetadata}
     * returned false. A good default implementation is to delegate to {@link #accept(String, String)}.
     */
    public boolean accept(String selector, ModuleVersionMetaData candidate);

    /**
     * Compares a dynamic version selector with a candidate version to indicate which is greater. If there is
     * not enough information to know which is greater, the version selector should be considered greater
     * and this method should return 0. This method will never be called for a version selector for which
     * {@link #isDynamic} returned false.
     */
    public int compare(String selector, String candidate, Comparator<String> candidateComparator);
}