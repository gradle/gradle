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
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Nullable;
import org.gradle.api.internal.ClosureBackedAction;

import java.util.Set;

/**
 * The model of an Eclipse resource filter matcher.
 * <p>
 * The matcher decides when the containing filter (or containing matcher) applies.  The
 * matcher configures things like whether this ResourceFilter matches resources by
 * name, project relative path, location, last modified, etc.  Eclipse has many types
 * of built-in matchers and it is possible to specify the id and arguments for custom
 * matchers using this model.
 * <p>
 * A matcher must have an id.  It may have either a custom string argument or a set of nested
 * child matchers (e.g. an 'or' matcher will have several nested condition matchers).
 * <p>
 * For more documentation on usage with examples, see {@link ResourceFilter}.
 *
 * @since 3.5
 */
public final class ResourceFilterMatcher {
    private String id;
    private String arguments;
    private Set<ResourceFilterMatcher> children = Sets.newLinkedHashSet();

    public ResourceFilterMatcher() {
    }

    public ResourceFilterMatcher(String id, String arguments, Set<ResourceFilterMatcher> children) {
        this();
        this.id = id;
        this.arguments = arguments;
        this.children = children;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Nullable
    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public Set<ResourceFilterMatcher> getChildren() {
        return children;
    }

    public void setChildren(Set<ResourceFilterMatcher> children) {
        if (children == null) {
            throw new InvalidUserDataException("children must not be null");
        }
        this.children = children;
    }    

    /**
     * Adds a child/nested matcher to this matcher.
     *
     * @param configureClosure The closure to use to configure the matcher.
     */
    public ResourceFilterMatcher matcher(@DelegatesTo(value=ResourceFilterMatcher.class, strategy = Closure.DELEGATE_FIRST) Closure configureClosure) {
        return matcher(new ClosureBackedAction<ResourceFilterMatcher>(configureClosure));
    }

    /**
     * Adds a child/nested matcher to this matcher.
     *
     * @param configureAction The action to use to configure the matcher.
     */
    public ResourceFilterMatcher matcher(Action<? super ResourceFilterMatcher> configureAction) {
        ResourceFilterMatcher m = new ResourceFilterMatcher();
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
        ResourceFilterMatcher resourceFilterMatcher = (ResourceFilterMatcher) o;
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
