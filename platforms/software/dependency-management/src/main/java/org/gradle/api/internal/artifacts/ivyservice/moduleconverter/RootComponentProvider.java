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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.AnonymousModule;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;

/**
 * Provides component instances intended to sit at the root of a dependency graph.
 */
@ServiceScope(Scope.Project.class)
public class RootComponentProvider {

    private final LocalComponentRegistry localComponentRegistry;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final ImmutableAttributesSchemaFactory attributesSchemaFactory;
    private final LocalComponentGraphResolveStateFactory localResolveStateFactory;

    @Inject
    public RootComponentProvider(
        LocalComponentRegistry localComponentRegistry,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        ImmutableAttributesSchemaFactory attributesSchemaFactory,
        LocalComponentGraphResolveStateFactory localResolveStateFactory
    ) {
        this.localComponentRegistry = localComponentRegistry;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.attributesSchemaFactory = attributesSchemaFactory;
        this.localResolveStateFactory = localResolveStateFactory;
    }

    /**
     * Provides the component for the project with the given ID, to be used as a root component
     * for a resolution graph.
     *
     * @deprecated Eventually, all resolved configurations should be part of an
     * {@link #createAdhocRootComponent(AttributesSchemaInternal) adhoc root component},
     * and should not live within the project component.
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public LocalComponentGraphResolveState getRootComponentForProject(ProjectIdentity projectId) {
        return localComponentRegistry.getComponent(new DefaultProjectComponentIdentifier(projectId));
    }

    /**
     * Create an adhoc root component, intended to own the root variant of a dependency graph.
     */
    public LocalComponentGraphResolveState createAdhocRootComponent(
        AttributesSchemaInternal schema
    ) {
        Module module = new AnonymousModule();

        String status = module.getStatus();
        ModuleVersionIdentifier moduleVersionId = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
        ImmutableAttributesSchema immutableSchema = attributesSchemaFactory.create(schema);

        return localResolveStateFactory.adhocRootComponentState(
            status,
            moduleVersionId,
            immutableSchema
        );
    }

}
