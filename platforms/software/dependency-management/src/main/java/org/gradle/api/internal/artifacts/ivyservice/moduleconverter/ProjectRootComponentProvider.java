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
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveMetadata;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

/**
 * Provides the component that owns the root variant of a resolved configuration within a project.
 * <p>
 * TODO #1629: This should be replaced with {@link AdhocRootComponentProvider} and
 *  all resolved configurations should live within an adhoc root component.
 */
@ServiceScope(Scope.Project.class)
public class ProjectRootComponentProvider implements RootComponentProvider {

    // Services
    private final ProjectState owner;
    private final DependencyMetaDataProvider componentIdentity;
    private final AttributesSchemaInternal schema;
    private final ConfigurationsProvider configurationsProvider;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final LocalComponentGraphResolveStateFactory localResolveStateFactory;
    private final ImmutableAttributesSchemaFactory attributesSchemaFactory;
    private final AdhocRootComponentProvider adhocRootComponentProvider;

    // State
    private @Nullable LocalComponentGraphResolveState cachedValue;

    public ProjectRootComponentProvider(
        ProjectState owner,
        DependencyMetaDataProvider componentIdentity,
        AttributesSchemaInternal schema,
        ConfigurationsProvider configurationsProvider,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        LocalComponentGraphResolveStateFactory localResolveStateFactory,
        ImmutableAttributesSchemaFactory attributesSchemaFactory,
        AdhocRootComponentProvider adhocRootComponentProvider
    ) {
        this.owner = owner;
        this.componentIdentity = componentIdentity;
        this.schema = schema;
        this.configurationsProvider = configurationsProvider;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.localResolveStateFactory = localResolveStateFactory;
        this.attributesSchemaFactory = attributesSchemaFactory;
        this.adhocRootComponentProvider = adhocRootComponentProvider;
    }

    @Override
    public LocalComponentGraphResolveState getRootComponent(boolean detached) {
        if (detached) {
            return adhocRootComponentProvider.getRootComponent(true);
        }

        if (cachedValue == null) {
            this.cachedValue = createProjectRootComponent();
        }

        return cachedValue;
    }

    private LocalComponentGraphResolveState createProjectRootComponent() {
        Module module = componentIdentity.getModule();

        ModuleVersionIdentifier moduleVersionId = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
        ComponentIdentifier componentIdentifier = owner.getComponentIdentifier();
        String status = module.getStatus();
        ImmutableAttributesSchema immutableSchema = attributesSchemaFactory.create(schema);

        LocalComponentGraphResolveMetadata metadata = new LocalComponentGraphResolveMetadata(
            moduleVersionId,
            componentIdentifier,
            status,
            immutableSchema
        );

        return localResolveStateFactory.stateFor(owner, metadata, configurationsProvider);
    }

}
