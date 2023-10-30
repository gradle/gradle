/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model;

import org.gradle.api.Action;

/**
 * The gradle DSL model of an Eclipse resource filter.
 *
 * This allows specifying a filter with a custom matcher and configuring
 * whether it is an include/exclude filter that applies to files, folders,
 * or both.  The following example excludes the 'node_modules' folder.
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 *     id 'eclipse'
 * }
 *
 * eclipse {
 *   project {
 *     resourceFilter {
 *       appliesTo = 'FOLDERS'
 *       type = 'EXCLUDE_ALL'
 *       matcher {
 *         id = 'org.eclipse.ui.ide.multiFilter'
 *         // to find out which arguments to use, configure the desired
 *         // filter with Eclipse's UI and copy the arguments string over
 *         arguments = '1.0-name-matches-false-false-node_modules'
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * @since 3.5
 */
public interface ResourceFilter {
    /**
     * Indicates whether this ResourceFilter applies to files, folders, or both.  Default is FILES_AND_FOLDERS
     */
    ResourceFilterAppliesTo getAppliesTo();

    /**
     * Indicates whether this ResourceFilter applies to files, folders, or both.  Default is FILES_AND_FOLDERS
     *
     * @throws org.gradle.api.InvalidUserDataException if appliesTo is null.
     */
    void setAppliesTo(ResourceFilterAppliesTo appliesTo);

    /**
     * Specifies whether this ResourceFilter is including or excluding resources.  Default is EXCLUDE_ALL
     */
    ResourceFilterType getType();

    /**
     * Sets the ResourceFilterType
     *
     * @throws org.gradle.api.InvalidUserDataException if type is null.
     */
    void setType(ResourceFilterType type);

    /**
     * Indicates whether this ResourceFilter applies recursively to all children of the project it is created on.  Default is true.
     */
    boolean isRecursive();

    /**
     * Sets whether this ResourceFilter applies recursively or not.
     */
    void setRecursive(boolean recursive);

    /**
     * Gets the matcher of this ResourceFilter.
     */
    ResourceFilterMatcher getMatcher();

    /**
     * Configures the matcher of this resource filter.  Will create the matcher if it does not yet exist, or configure the existing matcher if it already exists.
     *
     * @param configureAction The action to use to configure the matcher.
     */
    ResourceFilterMatcher matcher(Action<? super ResourceFilterMatcher> configureAction);
}
