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
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.plugins.ide.eclipse.model.ResourceFilterMatcher;
import org.gradle.util.ClosureBackedAction;

import javax.annotation.Nullable;
import java.util.Set;

public final class DefaultResourceFilterMatcher implements ResourceFilterMatcher {
    private String id;
    private String arguments;
    private Set<ResourceFilterMatcher> children = Sets.newLinkedHashSet();

    public DefaultResourceFilterMatcher() {
    }

    public DefaultResourceFilterMatcher(String id, String arguments, Set<ResourceFilterMatcher> children) {
        this();
        this.id = id;
        this.arguments = arguments;
        this.children = children;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    @Nullable
    public String getArguments() {
        return arguments;
    }

    @Override
    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    @Override
    public Set<ResourceFilterMatcher> getChildren() {
        return children;
    }

    public void setChildren(Set<ResourceFilterMatcher> children) {
        if (children == null) {
            throw new InvalidUserDataException("children must not be null");
        }
        this.children = children;
    }

    public ResourceFilterMatcher matcher(@DelegatesTo(value = ResourceFilterMatcher.class, strategy = Closure.DELEGATE_FIRST) Closure configureClosure) {
        return matcher(new ClosureBackedAction<ResourceFilterMatcher>(configureClosure));
    }

    @Override
    public ResourceFilterMatcher matcher(Action<? super ResourceFilterMatcher> configureAction) {
        ResourceFilterMatcher m = new DefaultResourceFilterMatcher();
        configureAction.execute(m);
        children.add(m);
        return m;
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
        DefaultResourceFilterMatcher resourceFilterMatcher = (DefaultResourceFilterMatcher) o;
        return Objects.equal(id, resourceFilterMatcher.id)
            && Objects.equal(arguments, resourceFilterMatcher.arguments)
            && Objects.equal(children, resourceFilterMatcher.children);
    }

    @Override
    public int hashCode() {
        int result;
        result = id != null ? id.hashCode() : 0;
        result = 31 * result + (arguments != null ? arguments.hashCode() : 0);
        result = 31 * result + (children != null ? children.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ResourceFilterMatcher{"
            + "id='" + id + '\''
            + ", arguments='" + arguments + '\''
            + ", children='" + children + '\''
            + '}';
    }
}
