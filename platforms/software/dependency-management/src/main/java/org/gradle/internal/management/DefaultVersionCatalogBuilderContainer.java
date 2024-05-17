/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.management;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder;
import org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.gradle.api.reflect.TypeOf.typeOf;

public class DefaultVersionCatalogBuilderContainer extends AbstractNamedDomainObjectContainer<VersionCatalogBuilder> implements MutableVersionCatalogContainer {
    private static final String VALID_EXTENSION_NAME = "[a-z]([a-zA-Z0-9])+";
    private static final Pattern VALID_EXTENSION_PATTERN = Pattern.compile(VALID_EXTENSION_NAME);

    private final Interner<String> strings = Interners.newStrongInterner();
    private final Interner<ImmutableVersionConstraint> versions = Interners.newStrongInterner();
    private final Supplier<DependencyResolutionServices> dependencyResolutionServices;
    private final ObjectFactory objects;
    private final UserCodeApplicationContext context;

    @Inject
    public DefaultVersionCatalogBuilderContainer(Instantiator instantiator,
                                                 CollectionCallbackActionDecorator callbackActionDecorator,
                                                 ObjectFactory objects,
                                                 UserCodeApplicationContext context,
                                                 Supplier<DependencyResolutionServices> dependencyResolutionServices) {
        super(VersionCatalogBuilder.class, instantiator, callbackActionDecorator);
        this.objects = objects;
        this.context = context;
        this.dependencyResolutionServices = dependencyResolutionServices;
    }

    private static void validateName(String name) {
        if (!VALID_EXTENSION_PATTERN.matcher(name).matches()) {
            throw new InvalidUserDataException("Invalid model name '" + name + "': it must match the following regular expression: " + VALID_EXTENSION_NAME);
        }
    }

    @Override
    public VersionCatalogBuilder create(String name, Action<? super VersionCatalogBuilder> configureAction) throws InvalidUserDataException {
        validateName(name);
        return super.create(name, model -> {
            UserCodeApplicationContext.Application current = context.current();
            DefaultVersionCatalogBuilder builder = (DefaultVersionCatalogBuilder) model;
            builder.withContext(current == null ? "Settings" : current.getSource().getDisplayName().getDisplayName(), () -> configureAction.execute(model));
        });
    }

    private static ProjectFinder makeUnknownProjectFinder() {
        return new UnknownProjectFinder("Project dependencies are not allowed in shared dependency resolution services");
    }

    @Override
    protected VersionCatalogBuilder doCreate(String name) {
        return objects.newInstance(DefaultVersionCatalogBuilder.class, name, strings, versions, objects, dependencyResolutionServices);
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(MutableVersionCatalogContainer.class);
    }

}
