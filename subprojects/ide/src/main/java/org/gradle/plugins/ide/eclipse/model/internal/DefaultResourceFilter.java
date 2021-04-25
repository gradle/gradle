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
package org.gradle.plugins.ide.eclipse.model.internal;

import com.google.common.base.Objects;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.plugins.ide.eclipse.model.ResourceFilter;
import org.gradle.plugins.ide.eclipse.model.ResourceFilterAppliesTo;
import org.gradle.plugins.ide.eclipse.model.ResourceFilterMatcher;
import org.gradle.plugins.ide.eclipse.model.ResourceFilterType;
import org.gradle.util.internal.ClosureBackedAction;

public final class DefaultResourceFilter implements ResourceFilter {
    private ResourceFilterAppliesTo appliesTo = ResourceFilterAppliesTo.FILES_AND_FOLDERS;
    private ResourceFilterType type = ResourceFilterType.EXCLUDE_ALL;
    private boolean recursive = true;
    private ResourceFilterMatcher matcher;

    public DefaultResourceFilter() {
    }

    public DefaultResourceFilter(ResourceFilterAppliesTo appliesTo, ResourceFilterType type, boolean recursive, ResourceFilterMatcher matcher) {
        this();
        setAppliesTo(appliesTo);
        setType(type);
        setRecursive(recursive);
        setMatcher(matcher);
    }

    @Override
    public ResourceFilterAppliesTo getAppliesTo() {
        return appliesTo;
    }

    @Override
    public void setAppliesTo(ResourceFilterAppliesTo appliesTo) {
        if (appliesTo == null) {
            throw new InvalidUserDataException("appliesTo must not be null");
        }
        this.appliesTo = appliesTo;
    }

    @Override
    public ResourceFilterType getType() {
        return type;
    }

    @Override
    public void setType(ResourceFilterType type) {
        if (type == null) {
            throw new InvalidUserDataException("type must not be null");
        }
        this.type = type;
    }

    @Override
    public boolean isRecursive() {
        return recursive;
    }

    @Override
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    @Override
    public ResourceFilterMatcher getMatcher() {
        return matcher;
    }

    public void setMatcher(ResourceFilterMatcher matcher) {
        this.matcher = matcher;
    }

    public ResourceFilterMatcher matcher(@DelegatesTo(value = ResourceFilterMatcher.class, strategy = Closure.DELEGATE_FIRST) Closure configureClosure) {
        return matcher(new ClosureBackedAction<ResourceFilterMatcher>(configureClosure));
    }

    @Override
    public ResourceFilterMatcher matcher(Action<? super ResourceFilterMatcher> configureAction) {
        if (this.matcher == null) {
            this.matcher = new DefaultResourceFilterMatcher();
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
        DefaultResourceFilter resourceFilter = (DefaultResourceFilter) o;
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
