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

import com.google.common.collect.Sets;
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
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.ComponentWithCoordinates;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.PublishArtifactInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.internal.mapping.ComponentDependencyResolver;
import org.gradle.api.publish.internal.mapping.DependencyCoordinateResolverFactory;
import org.gradle.api.publish.internal.mapping.ResolvedCoordinates;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * Builds a {@link ModuleMetadataSpec} from a {@link PublicationInternal} and its {@link SoftwareComponent}.
 *
 * <p>This builder extracts the variants, dependencies, artifacts, etc from the component to build
 * an independent representation of a GMM file that can be published without additional processing.</p>
 */
public class ModuleMetadataSpecBuilder {

    private final PublicationInternal<?> publication;
    private final ModuleVersionIdentifier publicationCoordinates;
    private final Provider<SoftwareComponentInternal> component;
    private final Collection<? extends PublicationInternal<?>> publications;
    private final Map<SoftwareComponent, ComponentData> componentCoordinates = new HashMap<>();
    private final DependencyCoordinateResolverFactory dependencyCoordinateResolverFactory;
    private final InvalidPublicationChecker checker;
    private final List<DependencyAttributesValidator> dependencyAttributeValidators;

    public ModuleMetadataSpecBuilder(
        PublicationInternal<?> publication,
        Collection<? extends PublicationInternal<?>> publications,
        InvalidPublicationChecker checker,
        DependencyCoordinateResolverFactory dependencyCoordinateResolverFactory,
        List<DependencyAttributesValidator> dependencyAttributeValidators
    ) {
        this.component = publication.getComponent();
        this.publicationCoordinates = publication.getCoordinates();
        this.publication = publication;
        this.publications = publications;
        this.checker = checker;
        this.dependencyCoordinateResolverFactory = dependencyCoordinateResolverFactory;
        this.dependencyAttributeValidators = dependencyAttributeValidators;
        // Collect a map from component to coordinates. This might be better to move to the component or some publications model
        collectCoordinates(componentCoordinates);
    }

    public ModuleMetadataSpec build() {
        return new ModuleMetadataSpec(identity(), variants(), publication.isPublishBuildId());
    }

    private ModuleMetadataSpec.Identity identity() {
        // Collect a map from component to its owning component. This might be better to move to the component or some publications model
        Map<SoftwareComponent, SoftwareComponent> owners = new HashMap<>();
        collectOwners(publications, owners);

        SoftwareComponent owner = owners.get(component.get());
        ComponentData ownerData = owner == null ? null : componentCoordinates.get(owner);
        ComponentData componentData = new ComponentData(publication.getCoordinates(), publication.getAttributes());

        return ownerData != null
            ? identityFor(ownerData, relativeUrlTo(componentData.coordinates, ownerData.coordinates))
            : identityFor(componentData, null);
    }

    private ModuleMetadataSpec.Identity identityFor(ComponentData componentData, String relativeUrl) {
        return new ModuleMetadataSpec.Identity(
            componentData.coordinates,
            attributesFor(componentData.attributes),
            relativeUrl
        );
    }

    private List<ModuleMetadataSpec.Variant> variants() {
        ArrayList<ModuleMetadataSpec.Variant> variants = new ArrayList<>();
        SoftwareComponentInternal softwareComponent = component.get();
        VersionMappingStrategyInternal versionMappingStrategy = publication.getVersionMappingStrategy();
        checker.checkComponent(softwareComponent);
        for (SoftwareComponentVariant variant : softwareComponent.getUsages()) {
            checkVariant(variant);
            variants.add(
                new ModuleMetadataSpec.LocalVariant(
                    variant.getName(),
                    attributesFor(variant.getAttributes()),
                    capabilitiesFor(variant.getCapabilities()),
                    dependenciesOf(variant, versionMappingStrategy),
                    dependencyConstraintsFor(variant, versionMappingStrategy),
                    artifactsOf(variant)
                )
            );
        }
        if (softwareComponent instanceof ComponentWithVariants) {
            for (SoftwareComponent childComponent : ((ComponentWithVariants) softwareComponent).getVariants()) {
                checker.checkComponent(childComponent);
                ModuleVersionIdentifier childCoordinates = coordinatesOf(childComponent);
                assert childCoordinates != null;
                if (childComponent instanceof SoftwareComponentInternal) {
                    for (SoftwareComponentVariant variant : ((SoftwareComponentInternal) childComponent).getUsages()) {
                        checkVariant(variant);
                        variants.add(
                            new ModuleMetadataSpec.RemoteVariant(
                                variant.getName(),
                                attributesFor(variant.getAttributes()),
                                availableAt(publicationCoordinates, childCoordinates),
                                capabilitiesFor(variant.getCapabilities())
                            )
                        );
                    }
                }
            }
        }
        return variants;
    }

    private List<ModuleMetadataSpec.Artifact> artifactsOf(SoftwareComponentVariant variant) {
        if (variant.getArtifacts().isEmpty()) {
            return emptyList();
        }
        ArrayList<ModuleMetadataSpec.Artifact> artifacts = new ArrayList<>();
        for (PublishArtifact artifact : variant.getArtifacts()) {
            ModuleMetadataSpec.Artifact metadataArtifact = artifactFor(artifact);
            if (metadataArtifact != null) {
                artifacts.add(metadataArtifact);
            }
        }
        return artifacts;
    }

    @Nullable
    private ModuleMetadataSpec.Artifact artifactFor(PublishArtifact artifact) {
        if (shouldNotBePublished(artifact)) {
            return null;
        }
        PublicationInternal.PublishedFile publishedFile = publication.getPublishedFile(artifact);
        return new ModuleMetadataSpec.Artifact(
            publishedFile.getName(),
            publishedFile.getUri(),
            artifact.getFile()
        );
    }

    private boolean shouldNotBePublished(PublishArtifact artifact) {
        return !PublishArtifactInternal.shouldBePublished(artifact);
    }

    private ModuleMetadataSpec.AvailableAt availableAt(ModuleVersionIdentifier coordinates, ModuleVersionIdentifier targetCoordinates) {
        if (coordinates.getModule().equals(targetCoordinates.getModule())) {
            throw new InvalidUserCodeException("Cannot have a remote variant with coordinates '" + targetCoordinates.getModule() + "' that are the same as the module itself.");
        }
        return new ModuleMetadataSpec.AvailableAt(
            relativeUrlTo(coordinates, targetCoordinates),
            targetCoordinates
        );
    }

    private ModuleMetadataSpec.Dependency dependencyFor(
        ModuleDependency dependency,
        Set<ExcludeRule> additionalExcludes,
        ComponentDependencyResolver dependencyResolver,
        DependencyArtifact dependencyArtifact,
        String variant) {
        return new ModuleMetadataSpec.Dependency(
            dependencyCoordinatesFor(dependency, dependencyResolver),
            excludedRulesFor(dependency, additionalExcludes),
            dependencyAttributesFor(variant, dependency.getGroup(), dependency.getName(), dependency.getAttributes()),
            capabilitiesFor(dependency.getRequestedCapabilities()),
            dependency.isEndorsingStrictVersions(),
            isNotEmpty(dependency.getReason()) ? dependency.getReason() : null,
            dependencyArtifact != null ? artifactSelectorFor(dependencyArtifact) : null
        );
    }

    private ModuleMetadataSpec.DependencyConstraint dependencyConstraintFor(
        DependencyConstraint dependencyConstraint,
        ComponentDependencyResolver dependencyResolver,
        String variant
    ) {
        return new ModuleMetadataSpec.DependencyConstraint(
            dependencyConstraintCoordinatesFor(dependencyConstraint, dependencyResolver),
            dependencyAttributesFor(variant, dependencyConstraint.getGroup(), dependencyConstraint.getName(), dependencyConstraint.getAttributes()),
            isNotEmpty(dependencyConstraint.getReason()) ? dependencyConstraint.getReason() : null
        );
    }

    private ModuleMetadataSpec.DependencyCoordinates dependencyCoordinatesFor(
        ModuleDependency dependency,
        ComponentDependencyResolver resolver
    ) {
        if (dependency instanceof ProjectDependency) {
            ResolvedCoordinates identifier = resolver.resolveComponentCoordinates((ProjectDependency) dependency);
            return projectDependencyCoordinatesFor(identifier);
        } else if (dependency instanceof ExternalDependency) {
            ResolvedCoordinates identifier = resolver.resolveComponentCoordinates((ExternalDependency) dependency);
            if (identifier == null) {
                identifier = ResolvedCoordinates.create(dependency.getGroup(), dependency.getName(), null);
            }
            return moduleDependencyCoordinatesFor(identifier, ((ExternalDependency) dependency).getVersionConstraint());
        } else {
            throw new UnsupportedOperationException("Unsupported dependency type: " + dependency.getClass().getName());
        }
    }

    private ModuleMetadataSpec.DependencyCoordinates dependencyConstraintCoordinatesFor(
        DependencyConstraint dependencyConstraint,
        ComponentDependencyResolver resolver
    ) {
        if (dependencyConstraint instanceof DefaultProjectDependencyConstraint) {
            ResolvedCoordinates identifier = resolver.resolveComponentCoordinates((DefaultProjectDependencyConstraint) dependencyConstraint);
            return projectDependencyCoordinatesFor(identifier);
        } else {
            ResolvedCoordinates identifier = resolver.resolveComponentCoordinates(dependencyConstraint);
            if (identifier == null) {
                identifier = ResolvedCoordinates.create(dependencyConstraint.getGroup(), dependencyConstraint.getName(), null);
            }
            return moduleDependencyCoordinatesFor(identifier, dependencyConstraint.getVersionConstraint());
        }
    }

    private ModuleMetadataSpec.DependencyCoordinates moduleDependencyCoordinatesFor(ResolvedCoordinates identifier, VersionConstraint dependencyConstraint) {
        ImmutableVersionConstraint constraint = DefaultImmutableVersionConstraint.of(dependencyConstraint);
        ModuleMetadataSpec.Version version = versionFor(constraint, identifier.getVersion());

        return new ModuleMetadataSpec.DependencyCoordinates(identifier.getGroup(), identifier.getName(), version);
    }

    private ModuleMetadataSpec.DependencyCoordinates projectDependencyCoordinatesFor(ResolvedCoordinates identifier) {
        ImmutableVersionConstraint constraint = DefaultImmutableVersionConstraint.of(identifier.getVersion());
        ModuleMetadataSpec.Version version = versionFor(constraint, identifier.getVersion());

        return new ModuleMetadataSpec.DependencyCoordinates(identifier.getGroup(), identifier.getName(), version);
    }

    private ModuleMetadataSpec.ArtifactSelector artifactSelectorFor(DependencyArtifact dependencyArtifact) {
        return new ModuleMetadataSpec.ArtifactSelector(
            dependencyArtifact.getName(),
            dependencyArtifact.getType(),
            isNullOrEmpty(dependencyArtifact.getExtension()) ? null : dependencyArtifact.getExtension(),
            isNullOrEmpty(dependencyArtifact.getClassifier()) ? null : dependencyArtifact.getClassifier()
        );
    }

    private List<ModuleMetadataSpec.Capability> capabilitiesFor(Collection<? extends Capability> capabilities) {
        if (capabilities.isEmpty()) {
            return emptyList();
        }

        ArrayList<ModuleMetadataSpec.Capability> metadataCapabilities = new ArrayList<>();
        for (Capability capability : capabilities) {
            metadataCapabilities.add(
                new ModuleMetadataSpec.Capability(
                    capability.getGroup(),
                    capability.getName(),
                    isNotEmpty(capability.getVersion()) ? capability.getVersion() : null
                )
            );
        }
        return metadataCapabilities;
    }

    private List<ModuleMetadataSpec.Attribute> attributesFor(AttributeContainer attributes) {
        if (attributes.isEmpty()) {
            return emptyList();
        }

        ArrayList<ModuleMetadataSpec.Attribute> metadataAttributes = new ArrayList<>();
        for (Attribute<?> attribute : sorted(attributes).values()) {
            String name = attribute.getName();
            Object value = attributes.getAttribute(attribute);
            Object effectiveValue = attributeValueFor(value);
            if (effectiveValue == null) {
                throw new IllegalArgumentException(
                    format("Cannot write attribute %s with unsupported value %s of type %s.", name, value, value.getClass().getName())
                );
            }
            metadataAttributes.add(
                new ModuleMetadataSpec.Attribute(name, effectiveValue)
            );
        }
        return metadataAttributes;
    }

    private List<ModuleMetadataSpec.Attribute> dependencyAttributesFor(String variant, String group, String name, AttributeContainer attributes) {
        for (DependencyAttributesValidator validator : dependencyAttributeValidators) {
            Optional<String> error = validator.validationErrorFor(group, name, attributes);
            error.ifPresent(s -> checker.addDependencyValidationError(variant, s, validator.getExplanation(), validator.getSuppressor()));
        }
        return attributesFor(attributes);
    }

    private Object attributeValueFor(Object value) {
        if (value instanceof Boolean || value instanceof Integer || value instanceof String) {
            return value;
        } else if (value instanceof Named) {
            return ((Named) value).getName();
        } else if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        } else {
            return null;
        }
    }

    private List<ModuleMetadataSpec.Dependency> dependenciesOf(SoftwareComponentVariant variant, VersionMappingStrategyInternal versionMappingStrategy) {
        if (variant.getDependencies().isEmpty()) {
            return emptyList();
        }
        ArrayList<ModuleMetadataSpec.Dependency> dependencies = new ArrayList<>();
        Set<ExcludeRule> additionalExcludes = variant.getGlobalExcludes();
        ComponentDependencyResolver dependencyResolver = dependencyCoordinateResolverFactory.createCoordinateResolvers(variant, versionMappingStrategy).getComponentResolver();
        for (ModuleDependency moduleDependency : variant.getDependencies()) {
            if (moduleDependency.getArtifacts().isEmpty()) {
                dependencies.add(
                    dependencyFor(
                        moduleDependency,
                        additionalExcludes,
                        dependencyResolver,
                        null,
                        variant.getName())
                );
            } else {
                for (DependencyArtifact dependencyArtifact : moduleDependency.getArtifacts()) {
                    dependencies.add(
                        dependencyFor(
                            moduleDependency,
                            additionalExcludes,
                            dependencyResolver,
                            dependencyArtifact,
                            variant.getName())
                    );
                }
            }
        }
        return dependencies;
    }

    private List<ModuleMetadataSpec.DependencyConstraint> dependencyConstraintsFor(SoftwareComponentVariant variant, VersionMappingStrategyInternal versionMappingStrategy) {
        if (variant.getDependencyConstraints().isEmpty()) {
            return emptyList();
        }
        ComponentDependencyResolver dependencyResolver = dependencyCoordinateResolverFactory.createCoordinateResolvers(variant, versionMappingStrategy).getComponentResolver();
        ArrayList<ModuleMetadataSpec.DependencyConstraint> dependencyConstraints = new ArrayList<>();
        for (DependencyConstraint dependencyConstraint : variant.getDependencyConstraints()) {
            dependencyConstraints.add(
                dependencyConstraintFor(dependencyConstraint, dependencyResolver, variant.getName())
            );
        }
        return dependencyConstraints;
    }

    @Nullable
    private ModuleMetadataSpec.Version versionFor(
        ImmutableVersionConstraint versionConstraint,
        @Nullable String resolvedVersion
    ) {
        checker.sawDependencyOrConstraint();
        if (resolvedVersion == null && isEmpty(versionConstraint)) {
            return null;
        }
        checker.sawVersion();

        boolean isStrict = !versionConstraint.getStrictVersion().isEmpty();
        String version;
        String preferred;
        if (resolvedVersion != null) {
            version = resolvedVersion;
            preferred = null;
        } else {
            version = isStrict
                ? versionConstraint.getStrictVersion()
                : !versionConstraint.getRequiredVersion().isEmpty()
                ? versionConstraint.getRequiredVersion()
                : null;
            preferred = !versionConstraint.getPreferredVersion().isEmpty()
                ? versionConstraint.getPreferredVersion()
                : null;
        }
        return new ModuleMetadataSpec.Version(
            version,
            isStrict ? version : null,
            preferred,
            versionConstraint.getRejectedVersions()
        );
    }

    private static void collectOwners(
        Collection<? extends PublicationInternal<?>> publications,
        Map<SoftwareComponent, SoftwareComponent> owners
    ) {
        for (PublicationInternal<?> publication : publications) {
            SoftwareComponent component = publication.getComponent().getOrNull();
            if (component instanceof ComponentWithVariants) {
                ComponentWithVariants componentWithVariants = (ComponentWithVariants) component;
                for (SoftwareComponent child : componentWithVariants.getVariants()) {
                    owners.put(child, component);
                }
            }
        }
    }

    private void collectCoordinates(Map<SoftwareComponent, ComponentData> coordinates) {
        for (PublicationInternal<?> publication : publications) {
            SoftwareComponentInternal component = publication.getComponent().getOrNull();
            if (component != null) {
                coordinates.put(
                    component,
                    new ComponentData(publication.getCoordinates(), publication.getAttributes())
                );
            }
        }
    }

    private void checkVariant(SoftwareComponentVariant variant) {
        checker.registerVariant(
            variant.getName(),
            variant.getAttributes(),
            variant.getCapabilities()
        );
    }

    private Set<ExcludeRule> excludedRulesFor(ModuleDependency moduleDependency, Set<ExcludeRule> additionalExcludes) {
        return moduleDependency.isTransitive()
            ? Sets.union(additionalExcludes, moduleDependency.getExcludeRules())
            : Collections.singleton(new DefaultExcludeRule(null, null));
    }

    private Map<String, Attribute<?>> sorted(AttributeContainer attributes) {
        Map<String, Attribute<?>> sortedAttributes = new TreeMap<>();
        for (Attribute<?> attribute : attributes.keySet()) {
            sortedAttributes.put(attribute.getName(), attribute);
        }
        return sortedAttributes;
    }

    private ModuleVersionIdentifier coordinatesOf(SoftwareComponent childComponent) {
        if (childComponent instanceof ComponentWithCoordinates) {
            return ((ComponentWithCoordinates) childComponent).getCoordinates();
        }
        ComponentData componentData = componentCoordinates.get(childComponent);
        if (componentData != null) {
            return componentData.coordinates;
        }
        return null;
    }

    private boolean isEmpty(ImmutableVersionConstraint versionConstraint) {
        return DefaultImmutableVersionConstraint.of().equals(versionConstraint);
    }

    public static String relativeUrlTo(
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
}
