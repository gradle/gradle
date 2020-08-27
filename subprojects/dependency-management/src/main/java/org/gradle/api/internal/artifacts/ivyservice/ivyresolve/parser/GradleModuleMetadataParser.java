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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Category;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExcludeRuleConverter;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenImmutableAttributesFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.component.external.model.AvailableAtUrlBackedArtifactMetadata;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultShadowedCapability;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.component.external.model.MutableComponentVariant;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.UrlBackedArtifactMetadata;
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.gson.stream.JsonToken.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang.StringUtils.capitalize;

public class GradleModuleMetadataParser {
    private final static Logger LOGGER = Logging.getLogger(GradleModuleMetadataParser.class);

    public static final String FORMAT_VERSION = "1.1";
    private final ImmutableAttributesFactory attributesFactory;
    private final NamedObjectInstantiator instantiator;
    private final ExcludeRuleConverter excludeRuleConverter;

    public GradleModuleMetadataParser(ImmutableAttributesFactory attributesFactory, ImmutableModuleIdentifierFactory moduleIdentifierFactory, NamedObjectInstantiator instantiator) {
        this.attributesFactory = attributesFactory;
        this.instantiator = instantiator;
        this.excludeRuleConverter = new DefaultExcludeRuleConverter(moduleIdentifierFactory);
    }

    public ImmutableAttributesFactory getAttributesFactory() {
        return attributesFactory;
    }

    public NamedObjectInstantiator getInstantiator() {
        return instantiator;
    }

    public void parse(final LocallyAvailableExternalResource resource, final MutableModuleComponentResolveMetadata metadata, ExternalResourceArtifactResolver externalResourceArtifactResolver, MutableModuleMetadataFactory<? extends MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory) {
        resource.withContent(inputStream -> {
            String version = null;
            try {
                JsonReader reader = new JsonReader(new InputStreamReader(inputStream, UTF_8));
                reader.beginObject();
                if (reader.peek() != JsonToken.NAME) {
                    throw new RuntimeException("Module metadata should contain a 'formatVersion' value.");
                }
                String name = reader.nextName();
                if (!name.equals("formatVersion")) {
                    throw new RuntimeException(String.format("The 'formatVersion' value should be the first value in a module metadata. Found '%s' instead.", name));
                }
                if (reader.peek() != JsonToken.STRING) {
                    throw new RuntimeException("The 'formatVersion' value should have a string value.");
                }
                version = reader.nextString();
                consumeTopLevelElements(reader, metadata, externalResourceArtifactResolver, mutableModuleMetadataFactory);
                File file = resource.getFile();
                if (!FORMAT_VERSION.equals(version)) {
                    LOGGER.debug("Unrecognized metadata format version '{}' found in '{}'. Parsing succeeded but it may lead to unexpected resolution results. Try upgrading to a newer version of Gradle", version, file);
                }
                return null;
            } catch (Exception e) {
                if (version != null && !FORMAT_VERSION.equals(version)) {
                    throw new MetaDataParseException("module metadata", resource, String.format("unsupported format version '%s' specified in module metadata. This version of Gradle supports format version %s.", version, FORMAT_VERSION), e);
                }
                throw new MetaDataParseException("module metadata", resource, e);
            }
        });
        maybeAddEnforcedPlatformVariant(metadata);
    }

    private void maybeAddEnforcedPlatformVariant(MutableModuleComponentResolveMetadata metadata) {
        List<? extends MutableComponentVariant> variants = metadata.getMutableVariants();
        if (variants == null || variants.isEmpty()) {
            return;
        }
        for (MutableComponentVariant variant : ImmutableList.copyOf(variants)) {
            AttributeValue<String> entry = variant.getAttributes().findEntry(MavenImmutableAttributesFactory.CATEGORY_ATTRIBUTE);
            if (entry.isPresent() && Category.REGULAR_PLATFORM.equals(entry.get()) && variant.getCapabilities().isEmpty()) {
                // This generates a synthetic enforced platform variant with the same dependencies, similar to what the Maven variant derivation strategy does
                ImmutableAttributes enforcedAttributes = attributesFactory.concat(variant.getAttributes(), MavenImmutableAttributesFactory.CATEGORY_ATTRIBUTE, new CoercingStringValueSnapshot(Category.ENFORCED_PLATFORM, instantiator));
                Capability enforcedCapability = buildShadowPlatformCapability(metadata.getId());
                metadata.addVariant(variant.copy("enforced" + capitalize(variant.getName()), enforcedAttributes, enforcedCapability));
            }
        }
    }

    private Capability buildShadowPlatformCapability(ModuleComponentIdentifier componentId) {
        return new DefaultShadowedCapability(new ImmutableCapability(
            componentId.getGroup(),
            componentId.getModule(),
            componentId.getVersion()
        ), "-derived-enforced-platform");
    }

    private void consumeTopLevelElements(JsonReader reader, MutableModuleComponentResolveMetadata metadata, ExternalResourceArtifactResolver externalResourceArtifactResolver, MutableModuleMetadataFactory<? extends MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory) throws IOException {
        while (reader.peek() != END_OBJECT) {
            String name = reader.nextName();
            switch (name) {
                case "variants":
                    consumeVariants(reader, metadata, externalResourceArtifactResolver, mutableModuleMetadataFactory);
                    break;
                case "component":
                    consumeComponent(reader, metadata);
                    break;
                default:
                    consumeAny(reader);
                    break;
            }
        }
    }

    private void consumeComponent(JsonReader reader, MutableModuleComponentResolveMetadata metadata) throws IOException {
        reader.beginObject();
        while (reader.peek() != END_OBJECT) {
            String name = reader.nextName();
            //noinspection SwitchStatementWithTooFewBranches
            switch (name) {
                case "attributes":
                    metadata.setAttributes(consumeAttributes(reader));
                    break;
                default:
                    consumeAny(reader);
                    break;
            }
        }
        reader.endObject();
    }

    private void consumeVariants(JsonReader reader, MutableModuleComponentResolveMetadata metadata, ExternalResourceArtifactResolver externalResourceArtifactResolver, MutableModuleMetadataFactory<? extends MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory) throws IOException {
        reader.beginArray();
        while (reader.peek() != JsonToken.END_ARRAY) {
            consumeVariant(reader, metadata, externalResourceArtifactResolver, mutableModuleMetadataFactory);
        }
        reader.endArray();
    }

    private void consumeVariant(JsonReader reader, MutableModuleComponentResolveMetadata metadata, ExternalResourceArtifactResolver externalResourceArtifactResolver, MutableModuleMetadataFactory<? extends MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory) throws IOException {
        String variantName = null;
        ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
        List<ModuleFile> files = Collections.emptyList();
        List<ModuleDependency> dependencies = Collections.emptyList();
        List<ModuleDependencyConstraint> dependencyConstraints = Collections.emptyList();
        List<VariantCapability> capabilities = Collections.emptyList();
        String relativeUri = null;
        MutableModuleComponentResolveMetadata referencedComponent = null;

        reader.beginObject();
        while (reader.peek() != END_OBJECT) {
            String name = reader.nextName();
            switch (name) {
                case "name":
                    variantName = reader.nextString();
                    break;
                case "attributes":
                    attributes = consumeAttributes(reader);
                    break;
                case "files":
                    files = consumeFiles(reader);
                    break;
                case "dependencies":
                    dependencies = consumeDependencies(reader);
                    break;
                case "dependencyConstraints":
                    dependencyConstraints = consumeDependencyConstraints(reader);
                    break;
                case "capabilities":
                    capabilities = consumeCapabilities(reader, true);
                    break;
                case "available-at":
                    ExternalVariant maybeComponent = findReferencedComponent(metadata.getModuleVersionId(), reader, externalResourceArtifactResolver, mutableModuleMetadataFactory);
                    dependencies = maybeComponent.dependencies;
                    referencedComponent = maybeComponent.resolvedMetadata;
                    relativeUri = maybeComponent.relativeUri;
                    break;
                default:
                    consumeAny(reader);
                    break;
            }
        }
        assertDefined(reader, "name", variantName);
        reader.endObject();

        if (referencedComponent != null) {
            MutableComponentVariant foundVariant = findReferencedVariant(attributes, referencedComponent);
            if (foundVariant != null) {
                dependencies = convertDependencies(foundVariant.getDependencies());
                dependencyConstraints = convertDependencyConstraints(foundVariant.getDependencyConstraints());
                files = convertFiles(relativeUri, foundVariant.getFiles());
            } else {
                assert dependencies != null;
                LOGGER.debug("Component references a variant available at " + relativeUri + " but it doesn't declare the expected variant. Falling back to legacy behavior of adding referenced variant as a dependency.");
            }
        }

        MutableComponentVariant variant = metadata.addVariant(variantName, attributes);
        populateVariant(files, dependencies, dependencyConstraints, capabilities, variant);
    }

    private MutableComponentVariant findReferencedVariant(ImmutableAttributes attributes, MutableModuleComponentResolveMetadata referencedComponent) {
        List<? extends MutableComponentVariant> variants = referencedComponent.getMutableVariants();
        MutableComponentVariant foundVariant = null;
        for (MutableComponentVariant variant : variants) {
            if (variant.getAttributes().equals(attributes)) {
                return variant;
            }
        }
        // For backwards compatibility, Kotlin MPP relies on the fact that the target
        // variants _may_ have additional attributes, in particular they are published with
        // an `artifactType` attribute (they shouldn't!) so we're doing a second pass with
        // a "containsAll" strategy
        ImmutableSet<Attribute<?>> requestedAttributeKeys = attributes.keySet();
        for (MutableComponentVariant variant : variants) {
            ImmutableAttributes variantAttributes = variant.getAttributes();
            ImmutableSet<Attribute<?>> variantAttributeKeys = variantAttributes.keySet();
            if (variantAttributeKeys.containsAll(requestedAttributeKeys)) {
                foundVariant = variant;
                for (Attribute<?> requestedAttributeKey : requestedAttributeKeys) {
                    Object requested = attributes.getAttribute(requestedAttributeKey);
                    Object actual = variantAttributes.getAttribute(requestedAttributeKey);
                    if (!requested.equals(actual)) {
                        foundVariant = null;
                        break;
                    }
                }
            }
            if (foundVariant != null) {
                break;
            }
        }
        return foundVariant;
    }

    private List<ModuleFile> convertFiles(String relativeUri, List<? extends ComponentVariant.File> files) {
        if (files.isEmpty()) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<ModuleFile> builder = ImmutableList.builderWithExpectedSize(files.size());
        for (ComponentVariant.File file : files) {
            String uri = relativeUri + file.getUri();
            builder.add(new ModuleFile(
                file.getName(),
                uri
            ));
        }
        return builder.build();
    }

    private List<ModuleDependencyConstraint> convertDependencyConstraints(List<ComponentVariant.DependencyConstraint> dependencyConstraints) {
        if (dependencyConstraints.isEmpty()) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<ModuleDependencyConstraint> builder = ImmutableList.builderWithExpectedSize(dependencyConstraints.size());
        for (ComponentVariant.DependencyConstraint dependency : dependencyConstraints) {
            builder.add(new ModuleDependencyConstraint(
                dependency.getGroup(),
                dependency.getModule(),
                dependency.getVersionConstraint(),
                dependency.getReason(),
                dependency.getAttributes()));
        }
        return builder.build();
    }

    private List<ModuleDependency> convertDependencies(List<ComponentVariant.Dependency> dependencies) {
        if (dependencies.isEmpty()) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<ModuleDependency> builder = ImmutableList.builderWithExpectedSize(dependencies.size());
        for (ComponentVariant.Dependency dependency : dependencies) {
            builder.add(new ModuleDependency(dependency.getGroup(),
                dependency.getModule(),
                dependency.getVersionConstraint(),
                dependency.getExcludes(),
                dependency.getReason(),
                dependency.getAttributes(),
                dependency.getRequestedCapabilities(),
                dependency.isEndorsingStrictVersions(),
                dependency.getDependencyArtifact()));
        }
        return builder.build();
    }


    private void populateVariant(List<ModuleFile> files, List<ModuleDependency> dependencies, List<ModuleDependencyConstraint> dependencyConstraints, List<VariantCapability> capabilities, MutableComponentVariant variant) {
        for (ModuleFile file : files) {
            variant.addFile(file.name, file.uri);
        }
        for (ModuleDependency dependency : dependencies) {
            variant.addDependency(dependency.group, dependency.module, dependency.versionConstraint, dependency.excludes, dependency.reason, dependency.attributes, dependency.requestedCapabilities, dependency.endorsing, dependency.artifact);
        }
        for (ModuleDependencyConstraint dependencyConstraint : dependencyConstraints) {
            variant.addDependencyConstraint(dependencyConstraint.group, dependencyConstraint.module, dependencyConstraint.versionConstraint, dependencyConstraint.reason, dependencyConstraint.attributes);
        }
        for (VariantCapability capability : capabilities) {
            variant.addCapability(capability.group, capability.name, capability.version);
        }
    }

    private ExternalVariant findReferencedComponent(ModuleVersionIdentifier id, JsonReader reader, ExternalResourceArtifactResolver externalArtifactResolver, MutableModuleMetadataFactory<? extends MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory) throws IOException {
        String url = null;
        String group = null;
        String module = null;
        String version = null;

        reader.beginObject();
        while (reader.peek() != END_OBJECT) {
            String name = reader.nextName();
            switch (name) {
                case "url":
                    url = reader.nextString();
                    break;
                case "group":
                    group = reader.nextString();
                    break;
                case "module":
                    module = reader.nextString();
                    break;
                case "version":
                    version = reader.nextString();
                    break;
                default:
                    consumeAny(reader);
                    break;
            }
        }
        assertDefined(reader, "url", url);
        assertDefined(reader, "group", group);
        assertDefined(reader, "module", module);
        assertDefined(reader, "version", version);
        reader.endObject();

        DefaultBuildableComponentResolveResult result = new DefaultBuildableComponentResolveResult();
        ModuleComponentIdentifier targetId = DefaultModuleComponentIdentifier.newId(
            DefaultModuleIdentifier.newId(group, module), version
        );
        UrlBackedArtifactMetadata artifact = new AvailableAtUrlBackedArtifactMetadata(
            targetId,
            module + "-" + version + ".module",
            url
        );
        LocallyAvailableExternalResource resource = externalArtifactResolver.resolveArtifact(
            artifact, result
        );
        ImmutableList<ModuleDependency> fallbackDependencies = ImmutableList.of(new ModuleDependency(group, module, new DefaultImmutableVersionConstraint(version), ImmutableList.of(), null, ImmutableAttributes.EMPTY, Collections.emptyList(), false, null));
        if (resource != null) {
            MutableModuleComponentResolveMetadata targetComponent = mutableModuleMetadataFactory.createForGradleModuleMetadata(targetId);
            parse(resource, targetComponent, externalArtifactResolver, mutableModuleMetadataFactory);
            if (targetComponent instanceof MutableMavenModuleResolveMetadata) {
                MavenResolver.processMetaData((MutableMavenModuleResolveMetadata) targetComponent);
            }
            return ExternalVariant.of(url, targetComponent, fallbackDependencies);
        } else {
            throw invalidReferenceToExternalModule(id, "it references a variant from a module which isn't published in the same repository.");
        }
    }

    private GradleException invalidReferenceToExternalModule(ModuleVersionIdentifier id, String reason) {
        return new GradleException("Module " + id + " is invalid because " + reason);
    }

    private List<ModuleDependency> consumeDependencies(JsonReader reader) throws IOException {
        List<ModuleDependency> dependencies = new ArrayList<>();
        reader.beginArray();
        while (reader.peek() != END_ARRAY) {
            String group = null;
            String module = null;
            String reason = null;
            ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
            VersionConstraint version = DefaultImmutableVersionConstraint.of();
            ImmutableList<ExcludeMetadata> excludes = ImmutableList.of();
            List<VariantCapability> requestedCapabilities = ImmutableList.of();
            IvyArtifactName artifactSelector = null;
            boolean endorseStrictVersions = false;

            reader.beginObject();
            while (reader.peek() != END_OBJECT) {
                String name = reader.nextName();
                switch (name) {
                    case "group":
                        group = reader.nextString();
                        break;
                    case "module":
                        module = reader.nextString();
                        break;
                    case "version":
                        version = consumeVersion(reader);
                        break;
                    case "excludes":
                        excludes = consumeExcludes(reader);
                        break;
                    case "reason":
                        reason = reader.nextString();
                        break;
                    case "attributes":
                        attributes = consumeAttributes(reader);
                        break;
                    case "requestedCapabilities":
                        requestedCapabilities = consumeCapabilities(reader, false);
                        break;
                    case "endorseStrictVersions":
                        endorseStrictVersions = reader.nextBoolean();
                        break;
                    case "thirdPartyCompatibility":
                        reader.beginObject();
                        while (reader.peek() != END_OBJECT) {
                            String compatibilityFeatureName = reader.nextName();
                            if (compatibilityFeatureName.equals("artifactSelector")) {
                                artifactSelector = consumeArtifactSelector(reader);
                            } else {
                                consumeAny(reader);
                            }
                        }
                        reader.endObject();
                        break;
                    default:
                        consumeAny(reader);
                        break;
                }
            }
            assertDefined(reader, "group", group);
            assertDefined(reader, "module", module);
            reader.endObject();

            dependencies.add(new ModuleDependency(group, module, version, excludes, reason, attributes, requestedCapabilities, endorseStrictVersions, artifactSelector));
        }
        reader.endArray();
        return dependencies;
    }

    private IvyArtifactName consumeArtifactSelector(JsonReader reader) throws IOException {
        reader.beginObject();
        String artifactName = null;
        String type = null;
        String extension = null;
        String classifier = null;
        while (reader.peek() != END_OBJECT) {
            String name = reader.nextName();
            switch (name) {
                case "name":
                    artifactName = reader.nextString();
                    break;
                case "type":
                    type = reader.nextString();
                    break;
                case "extension":
                    extension = reader.nextString();
                    break;
                case "classifier":
                    classifier = reader.nextString();
                    break;
                default:
                    consumeAny(reader);
                    break;
            }
        }
        assertDefined(reader, "name", artifactName);
        assertDefined(reader, "type", type);
        reader.endObject();
        return new DefaultIvyArtifactName(artifactName, type, extension, classifier);
    }

    private List<VariantCapability> consumeCapabilities(JsonReader reader, boolean versionRequired) throws IOException {
        ImmutableList.Builder<VariantCapability> capabilities = ImmutableList.builder();
        reader.beginArray();
        while (reader.peek() != END_ARRAY) {
            String group = null;
            String name = null;
            String version = null;

            reader.beginObject();
            while (reader.peek() != END_OBJECT) {
                String val = reader.nextName();
                switch (val) {
                    case "group":
                        group = reader.nextString();
                        break;
                    case "name":
                        name = reader.nextString();
                        break;
                    case "version":
                        if (reader.peek() == NULL) {
                            reader.nextNull();
                        } else {
                            version = reader.nextString();
                        }
                        break;
                }
            }
            assertDefined(reader, "group", group);
            assertDefined(reader, "name", name);
            if (versionRequired) {
                assertDefined(reader, "version", version);
            }
            reader.endObject();

            capabilities.add(new VariantCapability(group, name, version));
        }
        reader.endArray();
        return capabilities.build();
    }

    private List<ModuleDependencyConstraint> consumeDependencyConstraints(JsonReader reader) throws IOException {
        List<ModuleDependencyConstraint> dependencies = new ArrayList<>();
        reader.beginArray();
        while (reader.peek() != END_ARRAY) {
            String group = null;
            String module = null;
            String reason = null;
            VersionConstraint version = null;
            ImmutableAttributes attributes = ImmutableAttributes.EMPTY;

            reader.beginObject();
            while (reader.peek() != END_OBJECT) {
                String name = reader.nextName();
                switch (name) {
                    case "group":
                        group = reader.nextString();
                        break;
                    case "module":
                        module = reader.nextString();
                        break;
                    case "version":
                        version = consumeVersion(reader);
                        break;
                    case "reason":
                        reason = reader.nextString();
                        break;
                    case "attributes":
                        attributes = consumeAttributes(reader);
                        break;
                    default:
                        consumeAny(reader);
                        break;
                }
            }
            assertDefined(reader, "group", group);
            assertDefined(reader, "module", module);
            reader.endObject();

            dependencies.add(new ModuleDependencyConstraint(group, module, version, reason, attributes));
        }
        reader.endArray();
        return dependencies;
    }

    private ImmutableVersionConstraint consumeVersion(JsonReader reader) throws IOException {
        String requiredVersion = "";
        String preferredVersion = "";
        String strictVersion = "";
        List<String> rejects = Lists.newArrayList();

        reader.beginObject();
        while (reader.peek() != END_OBJECT) {
            // At this stage, 'strictly' implies 'requires'.
            String cst = reader.nextName();
            switch (cst) {
                case "prefers":
                    preferredVersion = reader.nextString();
                    break;
                case "requires":
                    requiredVersion = reader.nextString();
                    break;
                case "strictly":
                    strictVersion = reader.nextString();
                    break;
                case "rejects":
                    reader.beginArray();
                    while (reader.peek() != END_ARRAY) {
                        rejects.add(reader.nextString());
                    }
                    reader.endArray();
                    break;
                default:
                    consumeAny(reader);
                    break;
            }
        }
        reader.endObject();
        return DefaultImmutableVersionConstraint.of(preferredVersion, requiredVersion, strictVersion, rejects);
    }

    private ImmutableList<ExcludeMetadata> consumeExcludes(JsonReader reader) throws IOException {
        ImmutableList.Builder<ExcludeMetadata> builder = new ImmutableList.Builder<>();
        reader.beginArray();
        while (reader.peek() != END_ARRAY) {
            String group = null;
            String module = null;

            reader.beginObject();
            while (reader.peek() != END_OBJECT) {
                String name = reader.nextName();
                switch (name) {
                    case "group":
                        group = reader.nextString();
                        break;
                    case "module":
                        module = reader.nextString();
                        break;
                    default:
                        consumeAny(reader);
                        break;
                }
            }
            reader.endObject();

            ExcludeMetadata exclude = excludeRuleConverter.createExcludeRule(group, module);
            builder.add(exclude);
        }
        reader.endArray();
        return builder.build();
    }

    private List<ModuleFile> consumeFiles(JsonReader reader) throws IOException {
        List<ModuleFile> files = new ArrayList<>();
        reader.beginArray();
        while (reader.peek() != END_ARRAY) {
            String fileName = null;
            String fileUrl = null;

            reader.beginObject();
            while (reader.peek() != END_OBJECT) {
                String name = reader.nextName();
                switch (name) {
                    case "name":
                        fileName = reader.nextString();
                        break;
                    case "url":
                        fileUrl = reader.nextString();
                        break;
                    default:
                        consumeAny(reader);
                        break;
                }
            }
            assertDefined(reader, "name", fileName);
            assertDefined(reader, "url", fileUrl);
            reader.endObject();

            files.add(new ModuleFile(fileName, fileUrl));
        }
        reader.endArray();
        return files;
    }

    private ImmutableAttributes consumeAttributes(JsonReader reader) throws IOException {
        ImmutableAttributes attributes = ImmutableAttributes.EMPTY;

        reader.beginObject();
        while (reader.peek() != END_OBJECT) {
            String attrName = reader.nextName();
            if (reader.peek() == BOOLEAN) {
                boolean attrValue = reader.nextBoolean();
                attributes = attributesFactory.concat(attributes, Attribute.of(attrName, Boolean.class), attrValue);
            } else if (reader.peek() == NUMBER) {
                Integer attrValue = reader.nextInt();
                attributes = attributesFactory.concat(attributes, Attribute.of(attrName, Integer.class), attrValue);
            } else {
                String attrValue = reader.nextString();
                attributes = attributesFactory.concat(attributes, Attribute.of(attrName, String.class), new CoercingStringValueSnapshot(attrValue, instantiator));
            }
        }
        reader.endObject();

        return attributes;
    }

    private void consumeAny(JsonReader reader) throws IOException {
        reader.skipValue();
    }

    private void assertDefined(JsonReader reader, String attribute, String value) {
        if (StringUtils.isEmpty(value)) {
            String path = reader.getPath();
            // remove leading '$', remove last child segment, use '/' as separator
            throw new RuntimeException("missing '" + attribute + "' at " + path.substring(1, path.lastIndexOf('.')).replace('.', '/'));
        }
    }

    private static class ModuleFile {
        final String name;
        final String uri;

        ModuleFile(String name, String uri) {
            this.name = name;
            this.uri = uri;
        }
    }

    private static class ModuleDependency {
        final String group;
        final String module;
        final VersionConstraint versionConstraint;
        final ImmutableList<ExcludeMetadata> excludes;
        final String reason;
        final ImmutableAttributes attributes;
        final List<? extends Capability> requestedCapabilities;
        final boolean endorsing;
        final IvyArtifactName artifact;

        ModuleDependency(String group, String module, VersionConstraint versionConstraint, ImmutableList<ExcludeMetadata> excludes, String reason, ImmutableAttributes attributes, List<? extends Capability> requestedCapabilities, boolean endorsing, IvyArtifactName artifact) {
            this.group = group;
            this.module = module;
            this.versionConstraint = versionConstraint;
            this.excludes = excludes;
            this.reason = reason;
            this.attributes = attributes;
            this.requestedCapabilities = requestedCapabilities;
            this.endorsing = endorsing;
            this.artifact = artifact;
        }
    }

    private static class ModuleDependencyConstraint {
        final String group;
        final String module;
        final VersionConstraint versionConstraint;
        final String reason;
        final ImmutableAttributes attributes;

        ModuleDependencyConstraint(String group, String module, VersionConstraint versionConstraint, String reason, ImmutableAttributes attributes) {
            this.group = group;
            this.module = module;
            this.versionConstraint = versionConstraint;
            this.reason = reason;
            this.attributes = attributes;
        }
    }

    private static class VariantCapability implements Capability {
        final String group;
        final String name;
        final String version;

        private VariantCapability(String group, String name, String version) {
            this.group = group;
            this.name = name;
            this.version = version;
        }

        @Override
        public String getGroup() {
            return group;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getVersion() {
            return version;
        }
    }

    private static class ExternalVariant {
        private final String relativeUri;
        private final MutableModuleComponentResolveMetadata resolvedMetadata;
        private final List<ModuleDependency> dependencies;

        private ExternalVariant(String moduleUri, MutableModuleComponentResolveMetadata resolvedMetadata, List<ModuleDependency> dependencies) {
            this.relativeUri = moduleUri.substring(0, moduleUri.lastIndexOf("/") + 1);
            this.resolvedMetadata = resolvedMetadata;
            this.dependencies = dependencies;
        }

        public static ExternalVariant of(String relativeUri, MutableModuleComponentResolveMetadata targetComponent, List<ModuleDependency> dependencies) {
            return new ExternalVariant(relativeUri, targetComponent, dependencies);
        }

    }
}
