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

package org.gradle.api.publish.internal;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Named;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.ComponentWithCoordinates;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.PublishArtifactInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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

    public void generateTo(PublicationInternal publication, Collection<? extends PublicationInternal> publications, Writer writer) throws IOException {
        InvalidPublicationChecker checker = new InvalidPublicationChecker(publication.getName());
        // Collect a map from component to coordinates. This might be better to move to the component or some publications model
        Map<SoftwareComponent, ComponentData> coordinates = new HashMap<SoftwareComponent, ComponentData>();
        collectCoordinates(publications, coordinates);

        // Collect a map from component to its owning component. This might be better to move to the component or some publications model
        Map<SoftwareComponent, SoftwareComponent> owners = new HashMap<SoftwareComponent, SoftwareComponent>();
        collectOwners(publications, owners);

        // Write the output
        JsonWriter jsonWriter = new JsonWriter(writer);
        jsonWriter.setHtmlSafe(false);
        jsonWriter.setIndent("  ");
        writeComponentWithVariants(publication, publication.getComponent(), coordinates, owners, jsonWriter, checker);
        jsonWriter.flush();
        writer.append('\n');
        checker.validate();
    }

    private void collectOwners(Collection<? extends PublicationInternal> publications, Map<SoftwareComponent, SoftwareComponent> owners) {
        for (PublicationInternal publication : publications) {
            if (publication.getComponent() instanceof ComponentWithVariants) {
                ComponentWithVariants componentWithVariants = (ComponentWithVariants) publication.getComponent();
                for (SoftwareComponent child : componentWithVariants.getVariants()) {
                    owners.put(child, publication.getComponent());
                }
            }
        }
    }

    private void collectCoordinates(Collection<? extends PublicationInternal> publications, Map<SoftwareComponent, ComponentData> coordinates) {
        for (PublicationInternal publication : publications) {
            if (publication.getComponent() != null) {
                ModuleVersionIdentifier moduleVersionIdentifier = publication.getCoordinates();
                ImmutableAttributes attributes = publication.getAttributes();
                coordinates.put(publication.getComponent(), new ComponentData(moduleVersionIdentifier, attributes));
            }
        }
    }

    private void writeComponentWithVariants(PublicationInternal publication, SoftwareComponent component, Map<SoftwareComponent, ComponentData> componentCoordinates, Map<SoftwareComponent, SoftwareComponent> owners, JsonWriter jsonWriter, InvalidPublicationChecker checker) throws IOException {
        jsonWriter.beginObject();
        writeFormat(jsonWriter);
        writeIdentity(publication.getCoordinates(), publication.getAttributes(), component, componentCoordinates, owners, jsonWriter);
        writeCreator(jsonWriter);
        writeVariants(publication, component, componentCoordinates, jsonWriter, checker);
        jsonWriter.endObject();
    }

    private void writeVersionConstraint(ImmutableVersionConstraint immutableVersionConstraint, String resolvedVersion, JsonWriter jsonWriter, InvalidPublicationChecker checker) throws IOException {
        checker.sawDependencyOrConstraint();
        if (resolvedVersion == null && DefaultImmutableVersionConstraint.of().equals(immutableVersionConstraint)) {
            return;
        }
        checker.sawVersion();

        jsonWriter.name("version");
        jsonWriter.beginObject();

        boolean isStrict = !immutableVersionConstraint.getStrictVersion().isEmpty();
        String version = isStrict ? immutableVersionConstraint.getStrictVersion() : !immutableVersionConstraint.getRequiredVersion().isEmpty() ? immutableVersionConstraint.getRequiredVersion() : null;
        String preferred = !immutableVersionConstraint.getPreferredVersion().isEmpty() ? immutableVersionConstraint.getPreferredVersion() : null;
        if (resolvedVersion != null) {
            version = resolvedVersion;
            preferred = null;
        }

        if (version != null) {
            if (isStrict) {
                jsonWriter.name("strictly");
                jsonWriter.value(version);
            }
            jsonWriter.name("requires");
            jsonWriter.value(version);
        }
        if (preferred != null) {
            jsonWriter.name("prefers");
            jsonWriter.value(preferred);
        }
        List<String> rejectedVersions = immutableVersionConstraint.getRejectedVersions();
        if (!rejectedVersions.isEmpty()) {
            jsonWriter.name("rejects");
            jsonWriter.beginArray();
            for (String reject : rejectedVersions) {
                jsonWriter.value(reject);
            }
            jsonWriter.endArray();
        }
        jsonWriter.endObject();
    }

    private void writeIdentity(ModuleVersionIdentifier coordinates, ImmutableAttributes attributes, SoftwareComponent component, Map<SoftwareComponent, ComponentData> componentCoordinates, Map<SoftwareComponent, SoftwareComponent> owners, JsonWriter jsonWriter) throws IOException {
        SoftwareComponent owner = owners.get(component);
        if (owner == null) {
            jsonWriter.name("component");
            jsonWriter.beginObject();
            jsonWriter.name("group");
            jsonWriter.value(coordinates.getGroup());
            jsonWriter.name("module");
            jsonWriter.value(coordinates.getName());
            jsonWriter.name("version");
            jsonWriter.value(coordinates.getVersion());
            writeAttributes(attributes, jsonWriter);
            jsonWriter.endObject();
        } else {
            ComponentData componentData = componentCoordinates.get(owner);
            ModuleVersionIdentifier ownerCoordinates = componentData.coordinates;
            jsonWriter.name("component");
            jsonWriter.beginObject();
            jsonWriter.name("url");
            jsonWriter.value(relativeUrlTo(coordinates, ownerCoordinates));
            jsonWriter.name("group");
            jsonWriter.value(ownerCoordinates.getGroup());
            jsonWriter.name("module");
            jsonWriter.value(ownerCoordinates.getName());
            jsonWriter.name("version");
            jsonWriter.value(ownerCoordinates.getVersion());
            writeAttributes(componentData.attributes, jsonWriter);
            jsonWriter.endObject();
        }
    }

    private void writeVariants(PublicationInternal publication, SoftwareComponent component, Map<SoftwareComponent, ComponentData> componentCoordinates, JsonWriter jsonWriter, InvalidPublicationChecker checker) throws IOException {
        boolean started = false;
        for (UsageContext usageContext : ((SoftwareComponentInternal) component).getUsages()) {
            checker.registerVariant(usageContext.getName(), usageContext.getAttributes(),  usageContext.getCapabilities());
            if (!started) {
                jsonWriter.name("variants");
                jsonWriter.beginArray();
                started = true;
            }
            writeVariantHostedInThisModule(publication, usageContext, jsonWriter, checker);
        }
        if (component instanceof ComponentWithVariants) {
            for (SoftwareComponent childComponent : ((ComponentWithVariants) component).getVariants()) {
                ModuleVersionIdentifier childCoordinates;
                if (childComponent instanceof ComponentWithCoordinates) {
                    childCoordinates = ((ComponentWithCoordinates) childComponent).getCoordinates();
                } else {
                    ComponentData componentData = componentCoordinates.get(childComponent);
                    childCoordinates = componentData == null ? null : componentData.coordinates;
                }

                assert childCoordinates != null;
                if (childComponent instanceof SoftwareComponentInternal) {
                    for (UsageContext usageContext : ((SoftwareComponentInternal) childComponent).getUsages()) {
                        checker.registerVariant(usageContext.getName(), usageContext.getAttributes(),  usageContext.getCapabilities());
                        if (!started) {
                            jsonWriter.name("variants");
                            jsonWriter.beginArray();
                            started = true;
                        }
                        writeVariantHostedInAnotherModule(publication.getCoordinates(), childCoordinates, usageContext, jsonWriter);
                    }
                }
            }
        }
        if (started) {
            jsonWriter.endArray();
        }
    }

    private void writeCreator(JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("createdBy");
        jsonWriter.beginObject();
        jsonWriter.name("gradle");
        jsonWriter.beginObject();
        jsonWriter.name("version");
        jsonWriter.value(GradleVersion.current().getVersion());
        jsonWriter.name("buildId");
        jsonWriter.value(buildInvocationScopeId.getId().asString());
        jsonWriter.endObject();
        jsonWriter.endObject();
    }

    private void writeFormat(JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("formatVersion");
        jsonWriter.value(GradleModuleMetadataParser.FORMAT_VERSION);
    }

    private void writeVariantHostedInAnotherModule(ModuleVersionIdentifier coordinates, ModuleVersionIdentifier targetCoordinates, UsageContext variant, JsonWriter jsonWriter) throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name("name");
        jsonWriter.value(variant.getName());
        writeAttributes(variant.getAttributes(), jsonWriter);
        writeAvailableAt(coordinates, targetCoordinates, jsonWriter);
        writeCapabilities("capabilities", variant.getCapabilities(), jsonWriter);
        jsonWriter.endObject();
    }

    private void writeAvailableAt(ModuleVersionIdentifier coordinates, ModuleVersionIdentifier targetCoordinates, JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("available-at");
        jsonWriter.beginObject();

        jsonWriter.name("url");
        jsonWriter.value(relativeUrlTo(coordinates, targetCoordinates));

        jsonWriter.name("group");
        jsonWriter.value(targetCoordinates.getGroup());
        jsonWriter.name("module");
        jsonWriter.value(targetCoordinates.getName());
        jsonWriter.name("version");
        jsonWriter.value(targetCoordinates.getVersion());
        jsonWriter.endObject();
    }

    private String relativeUrlTo(ModuleVersionIdentifier from, ModuleVersionIdentifier to) {
        // TODO - do not assume Maven layout
        StringBuilder path = new StringBuilder();
        path.append("../../");
        path.append(to.getName());
        path.append("/");
        path.append(to.getVersion());
        path.append("/");
        path.append(to.getName());
        path.append("-");
        path.append(to.getVersion());
        path.append(".module");
        return path.toString();
    }

    private void writeVariantHostedInThisModule(PublicationInternal publication, UsageContext variant, JsonWriter jsonWriter, InvalidPublicationChecker checker) throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name("name");
        jsonWriter.value(variant.getName());
        writeAttributes(variant.getAttributes(), jsonWriter);
        VersionMappingStrategyInternal versionMappingStrategy = publication.getVersionMappingStrategy();
        writeDependencies(variant, versionMappingStrategy, jsonWriter, checker);
        writeDependencyConstraints(variant, jsonWriter, versionMappingStrategy, checker);
        writeArtifacts(publication, variant, jsonWriter);
        writeCapabilities("capabilities", variant.getCapabilities(), jsonWriter);

        jsonWriter.endObject();
    }

    private void writeAttributes(AttributeContainer attributes, JsonWriter jsonWriter) throws IOException {
        if (attributes.isEmpty()) {
            return;
        }
        jsonWriter.name("attributes");
        jsonWriter.beginObject();
        Map<String, Attribute<?>> sortedAttributes = new TreeMap<String, Attribute<?>>();
        for (Attribute<?> attribute : attributes.keySet()) {
            sortedAttributes.put(attribute.getName(), attribute);
        }
        for (Attribute<?> attribute : sortedAttributes.values()) {
            jsonWriter.name(attribute.getName());
            Object value = attributes.getAttribute(attribute);
            if (value instanceof Boolean) {
                Boolean b = (Boolean) value;
                jsonWriter.value(b);
            } else if (value instanceof Integer) {
                Integer i = (Integer) value;
                jsonWriter.value(i);
            } else if (value instanceof String) {
                String s = (String) value;
                jsonWriter.value(s);
            } else if (value instanceof Named) {
                Named named = (Named) value;
                jsonWriter.value(named.getName());
            } else if (value instanceof Enum) {
                Enum<?> enumValue = (Enum<?>) value;
                jsonWriter.value(enumValue.name());
            } else {
                throw new IllegalArgumentException(String.format("Cannot write attribute %s with unsupported value %s of type %s.", attribute.getName(), value, value.getClass().getName()));
            }
        }
        jsonWriter.endObject();
    }

    private void writeArtifacts(PublicationInternal publication, UsageContext variant, JsonWriter jsonWriter) throws IOException {
        if (variant.getArtifacts().isEmpty()) {
            return;
        }
        jsonWriter.name("files");
        jsonWriter.beginArray();
        for (PublishArtifact artifact : variant.getArtifacts()) {
            writeArtifact(publication, artifact, jsonWriter);
        }
        jsonWriter.endArray();
    }

    private void writeArtifact(PublicationInternal publication, PublishArtifact artifact, JsonWriter jsonWriter) throws IOException {
        if (artifact instanceof PublishArtifactInternal) {
            if (!((PublishArtifactInternal) artifact).shouldBePublished()) {
                return;
            }
        }
        PublicationInternal.PublishedFile publishedFile = publication.getPublishedFile(artifact);

        jsonWriter.beginObject();
        jsonWriter.name("name");
        jsonWriter.value(publishedFile.getName());
        jsonWriter.name("url");
        jsonWriter.value(publishedFile.getUri());

        jsonWriter.name("size");
        jsonWriter.value(artifact.getFile().length());
        writeChecksums(artifact, jsonWriter);

        jsonWriter.endObject();
    }

    private void writeChecksums(PublishArtifact artifact, JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("sha512");
        jsonWriter.value(checksumService.sha512(artifact.getFile()).toString());
        jsonWriter.name("sha256");
        jsonWriter.value(checksumService.sha256(artifact.getFile()).toString());
        jsonWriter.name("sha1");
        jsonWriter.value(checksumService.sha1(artifact.getFile()).toString());
        jsonWriter.name("md5");
        jsonWriter.value(checksumService.md5(artifact.getFile()).toString());
    }

    private void writeDependencies(UsageContext variant, VersionMappingStrategyInternal versionMappingStrategy, JsonWriter jsonWriter, InvalidPublicationChecker checker) throws IOException {
        if (variant.getDependencies().isEmpty()) {
            return;
        }
        jsonWriter.name("dependencies");
        jsonWriter.beginArray();
        Set<ExcludeRule> additionalExcludes = variant.getGlobalExcludes();
        VariantVersionMappingStrategyInternal variantVersionMappingStrategy = findVariantVersionMappingStrategy(variant, versionMappingStrategy);
        for (ModuleDependency moduleDependency : variant.getDependencies()) {
            if (moduleDependency.getArtifacts().isEmpty()) {
                writeDependency(moduleDependency, additionalExcludes, jsonWriter, variantVersionMappingStrategy, null, checker);
            } else {
                for (DependencyArtifact dependencyArtifact : moduleDependency.getArtifacts()) {
                    writeDependency(moduleDependency, additionalExcludes, jsonWriter, variantVersionMappingStrategy, dependencyArtifact, checker);
                }
            }
        }
        jsonWriter.endArray();
    }

    private VariantVersionMappingStrategyInternal findVariantVersionMappingStrategy(UsageContext variant, VersionMappingStrategyInternal versionMappingStrategy) {
        VariantVersionMappingStrategyInternal variantVersionMappingStrategy = null;
        if (versionMappingStrategy != null) {
            ImmutableAttributes attributes = ((AttributeContainerInternal) variant.getAttributes()).asImmutable();
            variantVersionMappingStrategy = versionMappingStrategy.findStrategyForVariant(attributes);
        }
        return variantVersionMappingStrategy;
    }

    private void writeDependency(ModuleDependency dependency, Set<ExcludeRule> additionalExcludes, JsonWriter jsonWriter, VariantVersionMappingStrategyInternal variantVersionMappingStrategy, DependencyArtifact dependencyArtifact, InvalidPublicationChecker checker) throws IOException {
        jsonWriter.beginObject();
        String resolvedVersion = null;
        if (dependency instanceof ProjectDependency) {
            ProjectDependency projectDependency = (ProjectDependency) dependency;
            ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(ModuleVersionIdentifier.class, projectDependency);
            if (variantVersionMappingStrategy != null) {
                ModuleVersionIdentifier resolved = variantVersionMappingStrategy.maybeResolveVersion(identifier.getGroup(), identifier.getName());
                if (resolved != null) {
                    identifier = resolved;
                    resolvedVersion = identifier.getVersion();
                }
            }
            jsonWriter.name("group");
            jsonWriter.value(identifier.getGroup());
            jsonWriter.name("module");
            jsonWriter.value(identifier.getName());
            writeVersionConstraint(DefaultImmutableVersionConstraint.of(identifier.getVersion()), resolvedVersion, jsonWriter, checker);
        } else {
            String group = dependency.getGroup();
            String name = dependency.getName();
            if (variantVersionMappingStrategy != null) {
                ModuleVersionIdentifier resolvedVersionId = variantVersionMappingStrategy.maybeResolveVersion(group, name);
                if (resolvedVersionId != null) {
                    group = resolvedVersionId.getGroup();
                    name = resolvedVersionId.getName();
                    resolvedVersion = resolvedVersionId.getVersion();
                }
            }
            jsonWriter.name("group");
            jsonWriter.value(group);
            jsonWriter.name("module");
            jsonWriter.value(name);
            ImmutableVersionConstraint vc;
            if (dependency instanceof ExternalDependency) {
                vc = DefaultImmutableVersionConstraint.of(((ExternalDependency) dependency).getVersionConstraint());
            } else {
                vc = DefaultImmutableVersionConstraint.of(Strings.nullToEmpty(dependency.getVersion()));
            }
            writeVersionConstraint(vc, resolvedVersion, jsonWriter, checker);
        }
        writeExcludes(dependency, additionalExcludes, jsonWriter);
        writeAttributes(dependency.getAttributes(), jsonWriter);
        writeCapabilities("requestedCapabilities", dependency.getRequestedCapabilities(), jsonWriter);

        boolean endorsing = dependency.isEndorsingStrictVersions();
        if (endorsing) {
            jsonWriter.name("endorseStrictVersions");
            jsonWriter.value(true);
        }
        String reason = dependency.getReason();
        if (StringUtils.isNotEmpty(reason)) {
            jsonWriter.name("reason");
            jsonWriter.value(reason);
        }
        if (dependencyArtifact != null) {
            writeDependencyArtifact(dependencyArtifact, jsonWriter);
        }
        jsonWriter.endObject();
    }

    private void writeDependencyArtifact(DependencyArtifact dependencyArtifact, JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("thirdPartyCompatibility");
        jsonWriter.beginObject();

        jsonWriter.name("artifactSelector");
        jsonWriter.beginObject();
        jsonWriter.name("name");
        jsonWriter.value(dependencyArtifact.getName());
        jsonWriter.name("type");
        jsonWriter.value(dependencyArtifact.getType());
        if (!Strings.isNullOrEmpty(dependencyArtifact.getExtension())) {
            jsonWriter.name("extension");
            jsonWriter.value(dependencyArtifact.getExtension());
        }
        if (!Strings.isNullOrEmpty(dependencyArtifact.getClassifier())) {
            jsonWriter.name("classifier");
            jsonWriter.value(dependencyArtifact.getClassifier());
        }
        jsonWriter.endObject();

        jsonWriter.endObject();
    }

    private void writeDependencyConstraints(UsageContext variant, JsonWriter jsonWriter, VersionMappingStrategyInternal versionMappingStrategy, InvalidPublicationChecker checker) throws IOException {
        if (variant.getDependencyConstraints().isEmpty()) {
            return;
        }
        VariantVersionMappingStrategyInternal variantVersionMappingStrategy = findVariantVersionMappingStrategy(variant, versionMappingStrategy);
        jsonWriter.name("dependencyConstraints");
        jsonWriter.beginArray();
        for (DependencyConstraint dependencyConstraint : variant.getDependencyConstraints()) {
            writeDependencyConstraint(dependencyConstraint, variantVersionMappingStrategy, jsonWriter, checker);
        }
        jsonWriter.endArray();
    }

    private void writeDependencyConstraint(DependencyConstraint dependencyConstraint, VariantVersionMappingStrategyInternal variantVersionMappingStrategy, JsonWriter jsonWriter, InvalidPublicationChecker checker) throws IOException {
        jsonWriter.beginObject();
        String group;
        String module;
        String resolvedVersion = null;
        if (dependencyConstraint instanceof DefaultProjectDependencyConstraint) {
            DefaultProjectDependencyConstraint dependency = (DefaultProjectDependencyConstraint) dependencyConstraint;
            ProjectDependency projectDependency = dependency.getProjectDependency();
            ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(ModuleVersionIdentifier.class, projectDependency);
            group = identifier.getGroup();
            module = identifier.getName();
            resolvedVersion = identifier.getVersion();
        } else {
            group = dependencyConstraint.getGroup();
            module = dependencyConstraint.getName();
        }
        ModuleVersionIdentifier resolvedVersionId = variantVersionMappingStrategy != null ? variantVersionMappingStrategy.maybeResolveVersion(group, module) : null;
        jsonWriter.name("group");
        jsonWriter.value(resolvedVersionId != null ? resolvedVersionId.getGroup() : group);
        jsonWriter.name("module");
        jsonWriter.value(resolvedVersionId != null ? resolvedVersionId.getName() : module);
        writeVersionConstraint(DefaultImmutableVersionConstraint.of(dependencyConstraint.getVersionConstraint()), resolvedVersionId != null ? resolvedVersionId.getVersion() : resolvedVersion, jsonWriter, checker);
        writeAttributes(dependencyConstraint.getAttributes(), jsonWriter);
        String reason = dependencyConstraint.getReason();
        if (StringUtils.isNotEmpty(reason)) {
            jsonWriter.name("reason");
            jsonWriter.value(reason);
        }
        jsonWriter.endObject();
    }

    private void writeExcludes(ModuleDependency moduleDependency, Set<ExcludeRule> additionalExcludes, JsonWriter jsonWriter) throws IOException {
        Set<ExcludeRule> excludeRules;
        if (!moduleDependency.isTransitive()) {
            excludeRules = Collections.<ExcludeRule>singleton(new DefaultExcludeRule(null, null));
        } else {
            excludeRules = Sets.union(additionalExcludes, moduleDependency.getExcludeRules());
        }
        if (excludeRules.isEmpty()) {
            return;
        }

        jsonWriter.name("excludes");
        jsonWriter.beginArray();
        for (ExcludeRule excludeRule : excludeRules) {
            jsonWriter.beginObject();
            jsonWriter.name("group");
            String group = GUtil.elvis(excludeRule.getGroup(), "*");
            jsonWriter.value(group);
            jsonWriter.name("module");
            String module = GUtil.elvis(excludeRule.getModule(), "*");
            jsonWriter.value(module);

            jsonWriter.endObject();
        }
        jsonWriter.endArray();
    }

    private void writeCapabilities(String key, Collection<? extends Capability> capabilities, JsonWriter jsonWriter) throws IOException {
        if (!capabilities.isEmpty()) {
            jsonWriter.name(key);
            jsonWriter.beginArray();
            for (Capability capability : capabilities) {
                jsonWriter.beginObject();
                jsonWriter.name("group").value(capability.getGroup());
                jsonWriter.name("name").value(capability.getName());
                if (StringUtils.isNotEmpty(capability.getVersion())) {
                    jsonWriter.name("version").value(capability.getVersion());
                }
                jsonWriter.endObject();
            }
            jsonWriter.endArray();
        }
    }

    private static class ComponentData {
        private final ModuleVersionIdentifier coordinates;
        private final ImmutableAttributes attributes;

        private ComponentData(ModuleVersionIdentifier coordinates, ImmutableAttributes attributes) {
            this.coordinates = coordinates;
            this.attributes = attributes;
        }
    }

    @NotThreadSafe
    public static class InvalidPublicationChecker {

        private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();

        private final String publicationName;
        private final BiMap<String, VariantIdentity> variants = HashBiMap.create();
        private List<String> errors;
        private boolean publicationHasVersion = false;
        private boolean publicationHasDependencyOrConstraint = false;

        public InvalidPublicationChecker(String publicationName) {
            this.publicationName = publicationName;
        }

        public void registerVariant(String name, AttributeContainer attributes, Set<? extends Capability> capabilities) {
            if (attributes.isEmpty()) {
                failWith("Variant '" + name + "' must declare at least one attribute.");
            }
            if (variants.containsKey(name)) {
                failWith("It is invalid to have multiple variants with the same name ('" + name + "')");
            } else {
                VariantIdentity identity = new VariantIdentity(attributes, capabilities);
                if (variants.containsValue(identity)) {
                    String found = variants.inverse().get(identity);
                    failWith("Variants '" + found + "' and '" + name + "' have the same attributes and capabilities. Please make sure either attributes or capabilities are different.");
                } else {
                    variants.put(name, identity);
                }
            }
        }

        private void checkVariantDependencyVersions() {
            if (publicationHasDependencyOrConstraint && !publicationHasVersion) {
                // Previous variant did not declare any version
                failWith("Publication only contains dependencies and/or constraints without a version. You need to add minimal version information, publish resolved versions ("
                    + DOCUMENTATION_REGISTRY.getDocumentationFor("publishing_maven", "publishing_maven:resolved_dependencies") + ") or reference a platform ("
                    + DOCUMENTATION_REGISTRY.getDocumentationFor("platforms") + ")");
            }
        }

        public void validate() {
            if (variants.isEmpty()) {
                failWith("This publication must publish at least one variant");
            }
            checkVariantDependencyVersions();
            if (errors != null) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Invalid publication '" + publicationName + "'");
                formatter.startChildren();
                for (String error : errors) {
                    formatter.node(error);
                }
                formatter.endChildren();
                throw new InvalidUserCodeException(formatter.toString());
            }
        }

        private void failWith(String message) {
            if (errors == null) {
                errors = Lists.newArrayList();
            }
            errors.add(message);
        }

        public void sawVersion() {
            publicationHasVersion = true;
        }

        public void sawDependencyOrConstraint() {
            publicationHasDependencyOrConstraint = true;
        }

        private static final class VariantIdentity {
            private final AttributeContainer attributes;
            private final Set<? extends Capability> capabilities;

            private VariantIdentity(AttributeContainer attributes, Set<? extends Capability> capabilities) {
                this.attributes = attributes;
                this.capabilities = capabilities;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                VariantIdentity that = (VariantIdentity) o;
                return Objects.equal(attributes, that.attributes) &&
                    Objects.equal(capabilities, that.capabilities);
            }

            @Override
            public int hashCode() {
                return Objects.hashCode(attributes, capabilities);
            }
        }
    }
}
