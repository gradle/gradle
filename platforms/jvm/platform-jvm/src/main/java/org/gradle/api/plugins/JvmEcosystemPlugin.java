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
package org.gradle.api.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.JavaEcosystemSupport;
import org.gradle.api.internal.artifacts.dsl.ComponentMetadataHandlerInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.TargetJVMVersionOnLibraryTooNewFailureDescriber;
import org.gradle.api.internal.attributes.AttributeDescriberRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.component.external.model.JavaEcosystemVariantDerivationStrategy;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure;

import javax.inject.Inject;

/**
 * A base plugin for projects working in a JVM world. This plugin
 * will configure the JVM attributes schema, setup resolution rules
 * and create the source set container.
 *
 * @since 6.7
 * @see <a href="https://docs.gradle.org/current/userguide/java_plugin.html">Java plugin reference</a>
 */
public abstract class JvmEcosystemPlugin implements Plugin<Project> {
    private final ObjectFactory objectFactory;
    private final SourceSetContainer sourceSets;

    @Inject
    public JvmEcosystemPlugin(ObjectFactory objectFactory, SourceSetContainer sourceSets) {
        this.objectFactory = objectFactory;
        this.sourceSets = sourceSets;
    }

    @Override
    public void apply(Project project) {
        ProjectInternal p = (ProjectInternal) project;
        project.getExtensions().add(SourceSetContainer.class, "sourceSets", sourceSets);
        configureVariantDerivationStrategy(p);
        configureSchema(p);
    }

    private void configureVariantDerivationStrategy(ProjectInternal project) {
        ComponentMetadataHandlerInternal metadataHandler = (ComponentMetadataHandlerInternal) project.getDependencies().getComponents();
        metadataHandler.setVariantDerivationStrategy(JavaEcosystemVariantDerivationStrategy.getInstance());
    }

    private void configureSchema(ProjectInternal project) {
        AttributesSchemaInternal attributesSchema = (AttributesSchemaInternal) project.getDependencies().getAttributesSchema();

        ResolutionFailureHandler handler = project.getServices().get(ResolutionFailureHandler.class);
        AttributeDescriberRegistry attributeDescribers = project.getServices().get(AttributeDescriberRegistry.class);

        JavaEcosystemSupport.configureServices(attributesSchema, attributeDescribers, objectFactory);
        handler.addFailureDescriber(NoCompatibleVariantsFailure.class, TargetJVMVersionOnLibraryTooNewFailureDescriber.class);

        project.getDependencies().getArtifactTypes().create(ArtifactTypeDefinition.JAR_TYPE).getAttributes()
            .attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME))
            .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.JAR));
    }
}
