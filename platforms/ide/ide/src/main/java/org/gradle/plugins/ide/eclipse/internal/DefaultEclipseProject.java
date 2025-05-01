/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.internal;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.model.EclipseProject;
import org.gradle.plugins.ide.eclipse.model.Link;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;

/**
 * Enables fine-tuning project details (.project file) of the Eclipse plugin
 * [javadoc comments preserved...]
 */
@NullMarked
public abstract class DefaultEclipseProject extends EclipseProject {

    @Inject
    public DefaultEclipseProject(XmlFileContentMerger file) {
        super(file);
    }

    @Override
    @NonNull
    protected String getCommentImpl() {
        return getCommentProperty().getOrNull();
    }

    /**
     * A comment used for the eclipse project. By default it will be configured to <b>project.description</b>
     * [javadoc comments preserved...]
     * @since 9.0
     */
    @Incubating
    abstract public Property<String> getCommentProperty();

    @Override
    protected void setCommentImpl(String comment) {
        this.getCommentProperty().set(comment);
    }

    @Override
    protected Set<Link> getLinkedResourcesImpl() {
        return getlinkedResourcesProperty().getOrElse(ImmutableSet.of());
    }

    @Override
    protected void setLinkedResourcesImpl(Set<Link> linkedResources) {
        this.getlinkedResourcesProperty().set(linkedResources);
    }

    /**
     * A comment used for the eclipse project. By default it will be configured to <b>project.description</b>
     * [javadoc comments preserved...]
     * @since 9.0
     */
    @Incubating
    public abstract SetProperty<Link> getlinkedResourcesProperty();

    @Override
    protected void linkedResourceImpl(Map<String, String> args) {
        Set<String> illegalArgs = Sets.difference(args.keySet(), VALID_LINKED_RESOURCE_ARGS);
        if (!illegalArgs.isEmpty()) {
            throw new InvalidUserDataException("You provided illegal argument for a link: " + illegalArgs + ". Valid link args are: " + VALID_LINKED_RESOURCE_ARGS);
        }

        getlinkedResourcesProperty().add(new Link(args.get("name"), args.get("type"), args.get("location"), args.get("locationUri")));
    }

}
