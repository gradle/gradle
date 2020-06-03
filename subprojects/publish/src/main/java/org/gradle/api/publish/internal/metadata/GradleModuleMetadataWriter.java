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

package org.gradle.api.publish.internal.metadata;

import com.google.gson.stream.JsonWriter;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>The Gradle module metadata file generator is responsible for generating a JSON file
 * describing module metadata. In particular, this file format is capable of handling different
 * variants with different dependency sets.</p>
 *
 * <p>Whenever you change this class, make sure you also:</p>
 *
 * <ul>
 * <li>Update the corresponding {@link GradleModuleMetadataParser module metadata parser}</li>
 * <li>Update the module metadata specification (subprojects/docs/src/docs/design/gradle-module-metadata-specification.md)</li>
 * <li>Update {@link org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetadataSerializer the module metadata serializer} </li>
 * <li>Add a sample for the module metadata serializer test, to make sure that serialized metadata is idempotent</li>
 * </ul>
 */
public class GradleModuleMetadataWriter {
    private final BuildInvocationScopeId buildInvocationScopeId;
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final ChecksumService checksumService;

    public GradleModuleMetadataWriter(BuildInvocationScopeId buildInvocationScopeId, ProjectDependencyPublicationResolver projectDependencyResolver, ChecksumService checksumService) {
        this.buildInvocationScopeId = buildInvocationScopeId;
        this.projectDependencyResolver = projectDependencyResolver;
        this.checksumService = checksumService;
    }

    public void writeTo(Writer writer, PublicationInternal<?> publication, Collection<? extends PublicationInternal<?>> publications) throws IOException {
        // Collect a map from component to coordinates. This might be better to move to the component or some publications model
        Map<SoftwareComponent, ComponentData> coordinates = new HashMap<>();
        collectCoordinates(publications, coordinates);

        // Collect a map from component to its owning component. This might be better to move to the component or some publications model
        Map<SoftwareComponent, SoftwareComponent> owners = new HashMap<>();
        collectOwners(publications, owners);

        InvalidPublicationChecker checker = new InvalidPublicationChecker(publication.getName());

        // Write the output
        JsonWriter jsonWriter = new JsonWriter(writer);
        jsonWriter.setHtmlSafe(false);
        jsonWriter.setIndent("  ");

        new ModuleMetadataJsonWriter(
            jsonWriter,
            checker,
            checksumService,
            projectDependencyResolver,
            buildInvocationScopeId.getId().asString(),
            publication,
            publication.getComponent(),
            coordinates,
            owners
        ).write();

        jsonWriter.flush();
        writer.append('\n');

        checker.validate();
    }

    private void collectOwners(Collection<? extends PublicationInternal<?>> publications, Map<SoftwareComponent, SoftwareComponent> owners) {
        for (PublicationInternal<?> publication : publications) {
            if (publication.getComponent() instanceof ComponentWithVariants) {
                ComponentWithVariants componentWithVariants = (ComponentWithVariants) publication.getComponent();
                for (SoftwareComponent child : componentWithVariants.getVariants()) {
                    owners.put(child, publication.getComponent());
                }
            }
        }
    }

    private void collectCoordinates(Collection<? extends PublicationInternal<?>> publications, Map<SoftwareComponent, ComponentData> coordinates) {
        for (PublicationInternal<?> publication : publications) {
            SoftwareComponentInternal component = publication.getComponent();
            if (component != null) {
                coordinates.put(
                    component,
                    new ComponentData(publication.getCoordinates(), publication.getAttributes())
                );
            }
        }
    }
}
