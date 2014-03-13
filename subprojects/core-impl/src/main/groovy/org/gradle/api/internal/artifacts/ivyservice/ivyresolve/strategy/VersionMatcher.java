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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;

import java.util.Comparator;

/**
 * Compares version selectors against candidate versions, indicating whether they match or not.
 *
 * <p>This interface was initially derived from {@code org.apache.ivy.plugins.version.VersionMatcher}.
 */
public interface VersionMatcher extends Comparator<String> {
    /**
     * Tells if this version matcher can handle the given version selector. If {@code false}
     * is returned, none of the other methods will be called for the given selector.
     */
    public boolean canHandle(String selector);

    /**
     * Indicates if the given version selector is dynamic.
     */
    public boolean isDynamic(String selector);

    /**
     * Indicates if module metadata is required to determine if the given version
     * selector matches a candidate version.
     */
    public boolean needModuleMetadata(String selector);

    /**
     * Indicates if the given version selector matches the given candidate version.
     * Only called if {@link #needModuleMetadata} returned {@code false} for the given selector.
     */
    public boolean accept(String selector, String candidate);

    /**
     * Indicates if the given version selector matches the given given candidate version
     * (whose metadata is provided). May also be called if {@link #isDynamic} returned
     * {@code false} for the given selector, in which case it should return the same result as
     * {@code accept(selector, candidate.getId().getVersion()}.
     */
    public boolean accept(String selector, ModuleVersionMetaData candidate);

    /**
     *
     * Compares a version selector with a candidate version to indicate which is greater. If there is
     * not enough information to tell which is greater, the version selector should be considered greater
     * and this method should return 0.
     */
    public int compare(String selector, String candidate);
}