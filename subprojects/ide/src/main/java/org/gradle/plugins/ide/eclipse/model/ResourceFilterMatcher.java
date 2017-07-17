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
import org.gradle.api.Incubating;

import javax.annotation.Nullable;
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
@Incubating
public interface ResourceFilterMatcher {
    /**
     * The id of the matcher type, as defined by its Eclipse extension.
     *
     * Can be null on a newly created matcher, but must be set in order to be considered valid.
     */
    @Nullable
    String getId();

    /**
     * Sets the id of the matcher type.
     *
     * @param id the id, cannot be null
     */
    void setId(String id);

    /**
     * The arguments of the matcher or null if it has children.
     */
    @Nullable
    String getArguments();

    /**
     * Sets the arguments of the matcher.
     *
     * @param arguments the arguments or null if the matcher should have child matchers instead
     */
    void setArguments(String arguments);

    /**
     * The child matchers of this matcher, e.g. when this is an OR-matcher.
     */
    Set<ResourceFilterMatcher> getChildren();

    /**
     * Adds a child/nested matcher to this matcher.
     *
     * @param configureAction The action to use to configure the matcher.
     */
    ResourceFilterMatcher matcher(Action<? super ResourceFilterMatcher> configureAction);
}
