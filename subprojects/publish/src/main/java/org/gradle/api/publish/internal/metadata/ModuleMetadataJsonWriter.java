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

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.gson.stream.JsonWriter;
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
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static org.gradle.util.GUtil.elvis;

class ModuleMetadataJsonWriter extends JsonWriterScope {

    private final String buildId;
    private final PublicationInternal<?> publication;
    private final SoftwareComponentInternal component;
    private final Map<SoftwareComponent, ComponentData> componentCoordinates;
    private final Map<SoftwareComponent, SoftwareComponent> owners;
    private final InvalidPublicationChecker checker;
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final ChecksumService checksumService;

    public ModuleMetadataJsonWriter(
        JsonWriter jsonWriter,
        InvalidPublicationChecker checker,
        ChecksumService checksumService,
        ProjectDependencyPublicationResolver projectDependencyResolver,
        String buildId,
        PublicationInternal<?> publication,
        SoftwareComponentInternal component,
        Map<SoftwareComponent, ComponentData> componentCoordinates,
        Map<SoftwareComponent, SoftwareComponent> owners
    ) {
        super(jsonWriter);
        this.buildId = buildId;
        this.publication = publication;
        this.component = component;
        this.componentCoordinates = componentCoordinates;
        this.owners = owners;
        this.checker = checker;
        this.projectDependencyResolver = projectDependencyResolver;
        this.checksumService = checksumService;
    }

    void write() throws IOException {
        SoftwareComponent owner = owners.get(component);
        ComponentData ownerData = owner == null ? null : componentCoordinates.get(owner);
        ComponentData componentData = componentCoordinates.get(component);
        writeObject(() -> {
            writeFormat();
            writeIdentity(componentData, ownerData);
            writeCreator();
            writeVariants();
        });
    }

    private void writeVersionConstraint(
        ImmutableVersionConstraint versionConstraint,
        String resolvedVersion
    ) throws IOException {
        checker.sawDependencyOrConstraint();
        if (resolvedVersion == null && isEmpty(versionConstraint)) {
            return;
        }
        checker.sawVersion();

        writeObject("version", () -> {
            boolean isStrict = !versionConstraint.getStrictVersion().isEmpty();
            String version = isStrict ? versionConstraint.getStrictVersion() : !versionConstraint.getRequiredVersion().isEmpty() ? versionConstraint.getRequiredVersion() : null;
            String preferred = !versionConstraint.getPreferredVersion().isEmpty() ? versionConstraint.getPreferredVersion() : null;
            if (resolvedVersion != null) {
                version = resolvedVersion;
                preferred = null;
            }
            if (version != null) {
                if (isStrict) {
                    write("strictly", version);
                }
                write("requires", version);
            }
            if (preferred != null) {
                write("prefers", preferred);
            }
            List<String> rejectedVersions = versionConstraint.getRejectedVersions();
            if (!rejectedVersions.isEmpty()) {
                writeArray("rejects", rejectedVersions);
            }
        });
    }

    private boolean isEmpty(ImmutableVersionConstraint versionConstraint) {
        return DefaultImmutableVersionConstraint.of().equals(versionConstraint);
    }

    private void writeIdentity(
        ComponentData component,
        @Nullable ComponentData owner
    ) throws IOException {
        if (owner != null) {
            String relativeUrl = relativeUrlTo(component.coordinates, owner.coordinates);
            writeComponentRef(owner, relativeUrl);
        } else {
            writeComponentRef(component, null);
        }
    }

    private void writeComponentRef(ComponentData data, @Nullable String relativeUrl) throws IOException {
        writeObject("component", () -> {
            if (relativeUrl != null) {
                write("url", relativeUrl);
            }
            writeCoordinates(data.coordinates);
            writeAttributes(data.attributes);
        });
    }

    private void writeVariants() throws IOException {
        boolean started = false;
        for (UsageContext usageContext : component.getUsages()) {
            checker.registerVariant(usageContext.getName(), usageContext.getAttributes(), usageContext.getCapabilities());
            if (!started) {
                beginArray("variants");
                started = true;
            }
            writeVariantHostedInThisModule(usageContext);
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
                        checker.registerVariant(usageContext.getName(), usageContext.getAttributes(), usageContext.getCapabilities());
                        if (!started) {
                            beginArray("variants");
                            started = true;
                        }
                        writeVariantHostedInAnotherModule(publication.getCoordinates(), childCoordinates, usageContext);
                    }
                }
            }
        }
        if (started) {
            endArray();
        }
    }

    private void writeCreator() throws IOException {
        writeObject("createdBy", () ->
            writeObject("gradle", () -> {
                write("version", GradleVersion.current().getVersion());
                if (publication.isPublishBuildId()) {
                    write("buildId", buildId);
                }
            })
        );
    }

    private void writeFormat() throws IOException {
        write("formatVersion", GradleModuleMetadataParser.FORMAT_VERSION);
    }

    private void writeVariantHostedInAnotherModule(ModuleVersionIdentifier coordinates, ModuleVersionIdentifier targetCoordinates, UsageContext variant) throws IOException {
        writeObject(() -> {
            write("name", variant.getName());
            writeAttributes(variant.getAttributes());
            writeAvailableAt(coordinates, targetCoordinates);
            writeCapabilities("capabilities", variant.getCapabilities());
        });
    }

    private void writeAvailableAt(ModuleVersionIdentifier coordinates, ModuleVersionIdentifier targetCoordinates) throws IOException {
        writeObject("available-at", () -> {
            write("url", relativeUrlTo(coordinates, targetCoordinates));
            writeCoordinates(targetCoordinates);
        });
    }

    private String relativeUrlTo(
        @SuppressWarnings("unused") ModuleVersionIdentifier from,
        ModuleVersionIdentifier to
    ) {
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

    private void writeVariantHostedInThisModule(UsageContext variant) throws IOException {
        writeObject(() -> {
            write("name", variant.getName());
            writeAttributes(variant.getAttributes());
            VersionMappingStrategyInternal versionMappingStrategy = publication.getVersionMappingStrategy();
            writeDependencies(variant, versionMappingStrategy);
            writeDependencyConstraints(variant, versionMappingStrategy);
            writeArtifacts(publication, variant);
            writeCapabilities("capabilities", variant.getCapabilities());
        });
    }

    private void writeCoordinates(ModuleVersionIdentifier coordinates) throws IOException {
        write("group", coordinates.getGroup());
        write("module", coordinates.getName());
        write("version", coordinates.getVersion());
    }

    private void writeAttributes(AttributeContainer attributes) throws IOException {
        if (attributes.isEmpty()) {
            return;
        }
        writeObject("attributes", () -> {
            for (Attribute<?> attribute : sorted(attributes).values()) {
                String name = attribute.getName();
                Object value = attributes.getAttribute(attribute);
                if (!writeAttribute(name, value)) {
                    throw new IllegalArgumentException(
                        format("Cannot write attribute %s with unsupported value %s of type %s.", name, value, value.getClass().getName())
                    );
                }
            }
        });
    }

    private boolean writeAttribute(String name, Object value) throws IOException {
        if (value instanceof Boolean) {
            write(name, (Boolean) value);
        } else if (value instanceof Integer) {
            write(name, (Integer) value);
        } else if (value instanceof String) {
            write(name, (String) value);
        } else if (value instanceof Named) {
            write(name, ((Named) value).getName());
        } else if (value instanceof Enum) {
            write(name, ((Enum<?>) value).name());
        } else {
            return false;
        }
        return true;
    }

    private Map<String, Attribute<?>> sorted(AttributeContainer attributes) {
        Map<String, Attribute<?>> sortedAttributes = new TreeMap<>();
        for (Attribute<?> attribute : attributes.keySet()) {
            sortedAttributes.put(attribute.getName(), attribute);
        }
        return sortedAttributes;
    }

    private void writeArtifacts(PublicationInternal<?> publication, UsageContext variant) throws IOException {
        if (variant.getArtifacts().isEmpty()) {
            return;
        }
        writeArray("files", () -> {
            for (PublishArtifact artifact : variant.getArtifacts()) {
                writeArtifact(publication, artifact);
            }
        });
    }

    private void writeArtifact(PublicationInternal<?> publication, PublishArtifact artifact) throws IOException {
        if (artifact instanceof PublishArtifactInternal) {
            if (!((PublishArtifactInternal) artifact).shouldBePublished()) {
                return;
            }
        }
        PublicationInternal.PublishedFile publishedFile = publication.getPublishedFile(artifact);
        File artifactFile = artifact.getFile();

        writeObject(() -> {
            write("name", publishedFile.getName());
            write("url", publishedFile.getUri());
            write("size", artifactFile.length());
            writeChecksumsOf(artifactFile);
        });
    }

    private void writeChecksumsOf(File artifactFile) throws IOException {
        write("sha512", checksumService.sha512(artifactFile).toString());
        write("sha256", checksumService.sha256(artifactFile).toString());
        write("sha1", checksumService.sha1(artifactFile).toString());
        write("md5", checksumService.md5(artifactFile).toString());
    }

    private void writeDependencies(UsageContext variant, VersionMappingStrategyInternal versionMappingStrategy) throws IOException {
        if (variant.getDependencies().isEmpty()) {
            return;
        }
        writeArray("dependencies", () -> {
            Set<ExcludeRule> additionalExcludes = variant.getGlobalExcludes();
            VariantVersionMappingStrategyInternal variantVersionMappingStrategy = findVariantVersionMappingStrategy(variant, versionMappingStrategy);
            for (ModuleDependency moduleDependency : variant.getDependencies()) {
                if (moduleDependency.getArtifacts().isEmpty()) {
                    writeDependency(moduleDependency, additionalExcludes, variantVersionMappingStrategy, null);
                } else {
                    for (DependencyArtifact dependencyArtifact : moduleDependency.getArtifacts()) {
                        writeDependency(moduleDependency, additionalExcludes, variantVersionMappingStrategy, dependencyArtifact);
                    }
                }
            }
        });
    }

    private VariantVersionMappingStrategyInternal findVariantVersionMappingStrategy(UsageContext variant, VersionMappingStrategyInternal versionMappingStrategy) {
        VariantVersionMappingStrategyInternal variantVersionMappingStrategy = null;
        if (versionMappingStrategy != null) {
            ImmutableAttributes attributes = ((AttributeContainerInternal) variant.getAttributes()).asImmutable();
            variantVersionMappingStrategy = versionMappingStrategy.findStrategyForVariant(attributes);
        }
        return variantVersionMappingStrategy;
    }

    private void writeDependency(ModuleDependency dependency, Set<ExcludeRule> additionalExcludes, VariantVersionMappingStrategyInternal variantVersionMappingStrategy, DependencyArtifact dependencyArtifact) throws IOException {
        writeObject(() -> {
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
                write("group", identifier.getGroup());
                write("module", identifier.getName());
                writeVersionConstraint(DefaultImmutableVersionConstraint.of(identifier.getVersion()), resolvedVersion);
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
                write("group", group);
                write("module", name);
                ImmutableVersionConstraint vc;
                if (dependency instanceof ExternalDependency) {
                    vc = DefaultImmutableVersionConstraint.of(((ExternalDependency) dependency).getVersionConstraint());
                } else {
                    vc = DefaultImmutableVersionConstraint.of(Strings.nullToEmpty(dependency.getVersion()));
                }
                writeVersionConstraint(vc, resolvedVersion);
            }
            writeExcludes(dependency, additionalExcludes);
            writeAttributes(dependency.getAttributes());
            writeCapabilities("requestedCapabilities", dependency.getRequestedCapabilities());

            boolean endorsing = dependency.isEndorsingStrictVersions();
            if (endorsing) {
                write("endorseStrictVersions", true);
            }
            String reason = dependency.getReason();
            if (isNotEmpty(reason)) {
                write("reason", reason);
            }
            if (dependencyArtifact != null) {
                writeDependencyArtifact(dependencyArtifact);
            }
        });
    }

    private void writeDependencyArtifact(DependencyArtifact dependencyArtifact) throws IOException {
        writeObject("thirdPartyCompatibility", () -> {
            writeObject("artifactSelector", () -> {
                write("name", dependencyArtifact.getName());
                write("type", dependencyArtifact.getType());
                if (!isNullOrEmpty(dependencyArtifact.getExtension())) {
                    write("extension", dependencyArtifact.getExtension());
                }
                if (!isNullOrEmpty(dependencyArtifact.getClassifier())) {
                    write("classifier", dependencyArtifact.getClassifier());
                }
            });
        });
    }

    private void writeDependencyConstraints(UsageContext variant, VersionMappingStrategyInternal versionMappingStrategy) throws IOException {
        if (variant.getDependencyConstraints().isEmpty()) {
            return;
        }
        writeArray("dependencyConstraints", () -> {
            VariantVersionMappingStrategyInternal mappingStrategy = findVariantVersionMappingStrategy(variant, versionMappingStrategy);
            for (DependencyConstraint dependencyConstraint : variant.getDependencyConstraints()) {
                writeDependencyConstraint(dependencyConstraint, mappingStrategy);
            }
        });
    }

    private void writeDependencyConstraint(DependencyConstraint dependencyConstraint, VariantVersionMappingStrategyInternal variantVersionMappingStrategy) throws IOException {
        writeObject(() -> {
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
            String effectiveGroup = resolvedVersionId != null ? resolvedVersionId.getGroup() : group;
            String effectiveModule = resolvedVersionId != null ? resolvedVersionId.getName() : module;
            String effectiveVersion = resolvedVersionId != null ? resolvedVersionId.getVersion() : resolvedVersion;
            write("group", effectiveGroup);
            write("module", effectiveModule);
            writeVersionConstraint(
                DefaultImmutableVersionConstraint.of(dependencyConstraint.getVersionConstraint()),
                effectiveVersion
            );
            writeAttributes(dependencyConstraint.getAttributes());
            String reason = dependencyConstraint.getReason();
            if (isNotEmpty(reason)) {
                write("reason", reason);
            }
        });
    }

    private void writeExcludes(ModuleDependency moduleDependency, Set<ExcludeRule> additionalExcludes) throws IOException {
        Set<ExcludeRule> excludeRules = excludedRulesFor(moduleDependency, additionalExcludes);
        if (excludeRules.isEmpty()) {
            return;
        }
        writeArray("excludes", () -> {
            for (ExcludeRule excludeRule : excludeRules) {
                writeObject(() -> {
                    write("group", elvis(excludeRule.getGroup(), "*"));
                    write("module", elvis(excludeRule.getModule(), "*"));
                });
            }
        });
    }

    private Set<ExcludeRule> excludedRulesFor(ModuleDependency moduleDependency, Set<ExcludeRule> additionalExcludes) {
        return moduleDependency.isTransitive()
            ? Sets.union(additionalExcludes, moduleDependency.getExcludeRules())
            : Collections.singleton(new DefaultExcludeRule(null, null));
    }

    private void writeCapabilities(String key, Collection<? extends Capability> capabilities) throws IOException {
        if (capabilities.isEmpty()) {
            return;
        }
        writeArray(key, () -> {
            for (Capability capability : capabilities) {
                writeObject(() -> {
                    write("group", capability.getGroup());
                    write("name", capability.getName());
                    if (isNotEmpty(capability.getVersion())) {
                        write("version", capability.getVersion());
                    }
                });
            }
        });
    }

}
