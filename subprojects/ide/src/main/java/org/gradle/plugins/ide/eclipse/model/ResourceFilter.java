/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.base.Objects;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.ClosureBackedAction;

/**
 * The gradle DSL model of an Eclipse resource filter.
 *
 * This allows specifying a filter with a custom matcher and configuring
 * whether it is an include/exclude filter that applies to files, folders,
 * or both.  The following example excludes the 'node_modules' folder.
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'eclipse'
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
public final class ResourceFilter {
    private ResourceFilterAppliesTo appliesTo = ResourceFilterAppliesTo.FILES_AND_FOLDERS;
    private ResourceFilterType type = ResourceFilterType.EXCLUDE_ALL;
    private boolean recursive = true;
    private ResourceFilterMatcher matcher;

    public ResourceFilter() {
    }

    public ResourceFilter(ResourceFilterAppliesTo appliesTo, ResourceFilterType type, boolean recursive, ResourceFilterMatcher matcher) {
        this();
        setAppliesTo(appliesTo);
        setType(type);
        setRecursive(recursive);
        setMatcher(matcher);
    }

    /**
     * Indicates whether this ResourceFilter applies to files, folders, or both.  Default is FILES_AND_FOLDERS
     */
    public ResourceFilterAppliesTo getAppliesTo() {
        return appliesTo;
    }
    
    /**
     * Indicates whether this ResourceFilter applies to files, folders, or both.  Default is FILES_AND_FOLDERS
     * 
     * @throws InvalidUserDataException if appliesTo is null.
     */
    public void setAppliesTo(ResourceFilterAppliesTo appliesTo) {
        if (appliesTo == null) {
            throw new InvalidUserDataException("appliesTo must not be null");
        }
        this.appliesTo = appliesTo;
    }
    
    /**
     * Specifies whether this ResourceFilter is including or excluding resources.  Default is EXCLUDE_ALL
     */
    public ResourceFilterType getType() {
        return type;
    }

    /**
     * Sets the ResourceFilterType
     * 
     * @throws InvalidUserDataException if type is null.
     */
    public void setType(ResourceFilterType type) {
        if (type == null) {
            throw new InvalidUserDataException("type must not be null");
        }
        this.type = type;
    }

    /**
     * Indicates whether this ResourceFilter applies recursively to all children of the project it is created on.  Default is true.
     */
    public boolean getRecursive() {
        return recursive;
    }

    /**
     * Sets whether this ResourceFilter applies recursively or not.
     */
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    /**
     * Gets the matcher of this ResourceFilter.
     */
    public ResourceFilterMatcher getMatcher() {
        return matcher;
    }

    /**
     * Sets the matcher of this ResourceFilter.
     */
    public void setMatcher(ResourceFilterMatcher matcher) {
        this.matcher = matcher;
    }

    /**
     * Configures the matcher of this resource filter.  Will create the matcher if it does not yet exist, or configure the existing matcher if it already exists.
     *
     * @param configureClosure The closure to use to configure the matcher.
     */
    public ResourceFilterMatcher matcher(@DelegatesTo(value=ResourceFilterMatcher.class, strategy = Closure.DELEGATE_FIRST) Closure configureClosure) {
        return matcher(new ClosureBackedAction<ResourceFilterMatcher>(configureClosure));
    }

    /**
     * Configures the matcher of this resource filter.  Will create the matcher if it does not yet exist, or configure the existing matcher if it already exists.
     *
     * @param configureAction The action to use to configure the matcher.
     */
    public ResourceFilterMatcher matcher(Action<? super ResourceFilterMatcher> configureAction) {
        if (this.matcher == null) {
            this.matcher = new ResourceFilterMatcher();
        }
        configureAction.execute(this.matcher);
        return this.matcher;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }
        ResourceFilter resourceFilter = (ResourceFilter) o;
        return Objects.equal(appliesTo, resourceFilter.appliesTo)
            && Objects.equal(type, resourceFilter.type)
            && Objects.equal(recursive, resourceFilter.recursive)
            && Objects.equal(matcher, resourceFilter.matcher);
    }

    @Override
    public int hashCode() {
        int result;
        result = appliesTo != null ? appliesTo.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + Boolean.valueOf(recursive).hashCode();
        result = 31 * result + (matcher != null ? matcher.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ResourceFilter{"
            + "appliesTo='" + appliesTo + '\''
            + ", type='" + type + '\''
            + ", recursive='" + recursive + '\''
            + ", matcher='" + matcher + '\''            
            + '}';
    }
}
