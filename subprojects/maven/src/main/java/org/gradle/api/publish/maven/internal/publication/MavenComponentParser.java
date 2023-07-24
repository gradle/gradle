/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.publish.maven.internal.publication;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.MavenVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.publish.internal.component.MavenPublishingAwareVariant;
import org.gradle.api.publish.internal.validation.PublicationWarningsCollector;
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenDependency;
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenPomDependencies;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependency;
import org.gradle.api.publish.maven.internal.dependencies.MavenPomDependencies;
import org.gradle.api.publish.maven.internal.dependencies.VersionRangeMapper;
import org.gradle.api.publish.maven.internal.validation.MavenPublicationErrorChecker;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Encapsulates all logic required to map the dependencies declared in a {@link SoftwareComponentInternal}
 * to a representation compatible with a Maven POM.
 */
public class MavenComponentParser {
    @VisibleForTesting
    public static final String INCOMPATIBLE_FEATURE = " contains dependencies that will produce a pom file that cannot be consumed by a Maven client.";
    @VisibleForTesting
    public static final String UNSUPPORTED_FEATURE = " contains dependencies that cannot be represented in a published pom file.";
    @VisibleForTesting
    public static final String PUBLICATION_WARNING_FOOTER =
        "These issues indicate information that is lost in the published 'pom' metadata file, " +
        "which may be an issue if the published library is consumed by an old Gradle version or Apache Maven.\n" +
        "The 'module' metadata file, which is used by Gradle 6+ is not affected.";

    /*
     * Maven supports wildcards in exclusion rules according to:
     * http://www.smartjava.org/content/maven-and-wildcard-exclusions
     * https://issues.apache.org/jira/browse/MNG-3832
     * This should be used for non-transitive dependencies
     */
    private static final Set<ExcludeRule> EXCLUDE_ALL_RULE = Collections.singleton(new DefaultExcludeRule("*", "*"));

    private static final Logger LOG = Logging.getLogger(MavenComponentParser.class);

    private final PublicationWarningsCollector publicationWarningsCollector =
        new PublicationWarningsCollector(LOG, UNSUPPORTED_FEATURE, INCOMPATIBLE_FEATURE, PUBLICATION_WARNING_FOOTER, "suppressPomMetadataWarningsFor");

    private final PlatformSupport platformSupport;
    private final VersionRangeMapper versionRangeMapper;
    private final DocumentationRegistry documentationRegistry;
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final NotationParser<Object, MavenArtifact> mavenArtifactParser;

    public MavenComponentParser(
        PlatformSupport platformSupport,
        VersionRangeMapper versionRangeMapper,
        DocumentationRegistry documentationRegistry,
        ProjectDependencyPublicationResolver projectDependencyResolver,
        NotationParser<Object, MavenArtifact> mavenArtifactParser
    ) {
        this.platformSupport = platformSupport;
        this.versionRangeMapper = versionRangeMapper;
        this.documentationRegistry = documentationRegistry;
        this.projectDependencyResolver = projectDependencyResolver;
        this.mavenArtifactParser = mavenArtifactParser;
    }

    public Set<MavenArtifact> parseArtifacts(SoftwareComponentInternal component) {
        component.finalizeValue();

        // TODO Artifact names should be determined by the source variant. We shouldn't
        //      blindly "pass-through" the artifact file name.
        Set<ArtifactKey> seenArtifacts = Sets.newHashSet();
        return component.getUsages().stream()
            .sorted(Comparator.comparing(MavenPublishingAwareVariant::scopeForVariant))
            .flatMap(variant -> variant.getArtifacts().stream())
            .filter(artifact -> {
                ArtifactKey key = new ArtifactKey(artifact.getFile(), artifact.getClassifier(), artifact.getExtension());
                return seenArtifacts.add(key);
            })
            .map(mavenArtifactParser::parseNotation)
            .collect(Collectors.toSet());
    }

    public DependencyResult parseDependencies(
        SoftwareComponentInternal component,
        ModuleVersionIdentifier coordinates,
        VersionMappingStrategyInternal versionMappingStrategy
    ) {
        component.finalizeValue();
        MavenPublicationErrorChecker.checkForUnpublishableAttributes(component, documentationRegistry);

        Set<PublishedDependency> seenDependencies = Sets.newHashSet();
        Set<DependencyConstraint> seenConstraints = Sets.newHashSet();

        List<MavenDependency> dependencies = new ArrayList<>();
        List<MavenDependency> constraints = new ArrayList<>();
        List<MavenDependency> platforms = new ArrayList<>();

        for (SoftwareComponentVariant variant : getSortedVariants(component)) {
            publicationWarningsCollector.newContext(variant.getName());

            MavenPublishingAwareVariant.ScopeMapping scopeMapping = MavenPublishingAwareVariant.scopeForVariant(variant);
            String scope = scopeMapping.getScope();
            boolean optional = scopeMapping.isOptional();
            Set<ExcludeRule> globalExcludes = variant.getGlobalExcludes();

            ImmutableAttributes attributes = ((AttributeContainerInternal) variant.getAttributes()).asImmutable();
            MavenDependencyFactory dependencyFactory = new MavenDependencyFactory(
                projectDependencyResolver,
                versionRangeMapper,
                versionMappingStrategy.findStrategyForVariant(attributes),
                publicationWarningsCollector
            );

            for (ModuleDependency dependency : variant.getDependencies()) {
                if (seenDependencies.add(PublishedDependency.of(dependency))) {
                    if (isDependencyWithDefaultArtifact(dependency) && dependencyMatchesProject(dependency, coordinates)) {
                        // We skip all self referencing dependency declarations, unless they have custom artifact information
                        continue;
                    }
                    if (platformSupport.isTargetingPlatform(dependency)) {
                        if (dependency instanceof ProjectDependency) {
                            platforms.add(dependencyFactory.asImportDependencyConstraint((ProjectDependency) dependency));
                        } else {
                            platforms.add(dependencyFactory.asImportDependencyConstraint(dependency));
                        }
                    } else {
                        if (!dependency.getAttributes().isEmpty()) {
                            publicationWarningsCollector.addUnsupported(String.format("%s:%s:%s declared with Gradle attributes", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                        }
                        if (dependency instanceof ProjectDependency) {
                            dependencies.add(dependencyFactory.asDependency((ProjectDependency) dependency, scope, optional, globalExcludes));
                        } else {
                            dependencies.addAll(dependencyFactory.asDependencies(dependency, scope, optional, globalExcludes));
                        }
                    }
                }
            }

            for (DependencyConstraint dependency : variant.getDependencyConstraints()) {
                if (seenConstraints.add(dependency)) { // TODO: Why do we not use PublishedDependency here?
                    if (dependency instanceof DefaultProjectDependencyConstraint) {
                        constraints.add(dependencyFactory.asDependencyConstraint((DefaultProjectDependencyConstraint) dependency));
                    } else if (dependency.getVersion() != null) {
                        constraints.add(dependencyFactory.asDependencyConstraint(dependency));
                    } else {
                        // Some dependency constraints, like those with rejectAll() have no version and do not map to Maven.
                        publicationWarningsCollector.addIncompatible(String.format("constraint %s:%s declared with a Maven incompatible version notation", dependency.getGroup(), dependency.getName()));
                    }
                }
            }

            if (!variant.getCapabilities().isEmpty()) {
                for (Capability capability : variant.getCapabilities()) {
                    if (isNotDefaultCapability(capability, coordinates)) {
                        publicationWarningsCollector.addVariantUnsupported(String.format("Declares capability %s:%s:%s which cannot be mapped to Maven", capability.getGroup(), capability.getName(), capability.getVersion()));
                    }
                }
            }
        }

        return new DependencyResult(
            new DefaultMavenPomDependencies(
                ImmutableList.copyOf(dependencies),
                ImmutableList.<MavenDependency>builder().addAll(constraints).addAll(platforms).build()
            ),
            publicationWarningsCollector
        );
    }

    private static boolean isNotDefaultCapability(Capability capability, ModuleVersionIdentifier coordinates) {
        return !coordinates.getGroup().equals(capability.getGroup())
            || !coordinates.getName().equals(capability.getName())
            || !coordinates.getVersion().equals(capability.getVersion());
    }

    private static boolean isDependencyWithDefaultArtifact(ModuleDependency dependency) {
        if (dependency.getArtifacts().isEmpty()) {
            return true;
        }
        return dependency.getArtifacts().stream().allMatch(artifact -> Strings.nullToEmpty(artifact.getClassifier()).isEmpty());
    }

    private static boolean dependencyMatchesProject(ModuleDependency dependency, ModuleVersionIdentifier coordinates) {
        return coordinates.getModule().equals(DefaultModuleIdentifier.newId(dependency.getGroup(), dependency.getName()));
    }

    private static List<SoftwareComponentVariant> getSortedVariants(SoftwareComponentInternal component) {
        return component.getUsages().stream()
            .sorted(Comparator.comparing(MavenPublishingAwareVariant::scopeForVariant))
            .collect(Collectors.toList());
    }

    private static class MavenDependencyFactory {

        private final ProjectDependencyPublicationResolver projectDependencyResolver;
        private final VersionRangeMapper versionRangeMapper;
        private final VariantVersionMappingStrategyInternal versionMappingStrategy;
        private final PublicationWarningsCollector publicationWarningsCollector;

        public MavenDependencyFactory(
            ProjectDependencyPublicationResolver projectDependencyResolver,
            VersionRangeMapper versionRangeMapper,
            VariantVersionMappingStrategyInternal versionMappingStrategy,
            PublicationWarningsCollector publicationWarningsCollector
        ) {
            this.projectDependencyResolver = projectDependencyResolver;
            this.versionRangeMapper = versionRangeMapper;
            this.versionMappingStrategy = versionMappingStrategy;
            this.publicationWarningsCollector = publicationWarningsCollector;
        }

        private List<MavenDependency> asDependencies(ModuleDependency dependency, String scope, boolean optional, Set<ExcludeRule> globalExcludes) {
            Set<ExcludeRule> allExcludeRules = getExcludeRules(globalExcludes, dependency);

            if (dependency.getArtifacts().isEmpty()) {
                Coordinates coordinates = resolveCoordinates(dependency.getGroup(), dependency.getName(), dependency.getVersion(), null);
                return Collections.singletonList(newDependency(coordinates, null, null, scope, allExcludeRules, optional));
            }

            List<MavenDependency> dependencies = new ArrayList<>();
            for (DependencyArtifact artifact : dependency.getArtifacts()) {
                Coordinates coordinates = resolveCoordinates(
                    dependency.getGroup(),
                    artifact.getName(), // TODO: This seems wrong. We should not allow the artifact to change the dependency coordinates.
                    dependency.getVersion(),
                    null
                );

                dependencies.add(newDependency(coordinates, artifact.getType(), artifact.getClassifier(), scope, allExcludeRules, optional));
            }
            return dependencies;
        }

        private MavenDependency asDependency(ProjectDependency dependency, String scope, boolean optional, Set<ExcludeRule> globalExcludes) {
            Path identityPath = getIdentityPath(dependency);
            Coordinates coordinates = resolveCoordinates(identityPath);
            Set<ExcludeRule> allExcludeRules = getExcludeRules(globalExcludes, dependency);

            return newDependency(coordinates, null, null, scope, allExcludeRules, optional);
        }

        private MavenDependency asDependencyConstraint(DependencyConstraint dependency) {
            Coordinates coordinates = resolveCoordinates(dependency.getGroup(), dependency.getName(), dependency.getVersion(), null);

            // Do not publish scope, as it has too different of semantics in Maven
            return newDependency(coordinates, null, null, null, Collections.emptySet(), false);
        }

        private MavenDependency asDependencyConstraint(DefaultProjectDependencyConstraint dependency) {
            Path identityPath = getIdentityPath(dependency.getProjectDependency());
            Coordinates coordinates = resolveCoordinates(identityPath);

            // Do not publish scope, as it has too different of semantics in Maven
            return newDependency(coordinates, null, null, null, Collections.emptySet(), false);
        }

        private MavenDependency asImportDependencyConstraint(ModuleDependency dependency) {
            Coordinates coordinates = resolveCoordinates(dependency.getGroup(), dependency.getName(), dependency.getVersion(), null);
            return newDependency(coordinates, "pom", null, "import", Collections.emptySet(), false);
        }

        private MavenDependency asImportDependencyConstraint(ProjectDependency dependency) {
            Path identityPath = getIdentityPath(dependency);
            Coordinates coordinates = resolveCoordinates(identityPath);
            return newDependency(coordinates, "pom", null, "import", Collections.emptySet(), false);
        }

        private Coordinates resolveCoordinates(Path identityPath) {
            ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(ModuleVersionIdentifier.class, identityPath);
            return resolveCoordinates(identifier.getGroup(), identifier.getName(), identifier.getVersion(), identityPath);
        }

        private Coordinates resolveCoordinates(String groupId, String artifactId, @Nullable String version, @Nullable Path identityPath) {
            ModuleVersionIdentifier resolvedVersion = versionMappingStrategy.maybeResolveVersion(groupId, artifactId, identityPath);
            if (resolvedVersion != null) {
                return Coordinates.from(resolvedVersion);
            }

            // Version mapping is disabled or did not discover coordinates. Attempt to convert Gradle's rich version notation to Maven's.
            if (version == null) {
                return new Coordinates(groupId, artifactId, null);
            }

            if (DefaultVersionSelectorScheme.isSubVersion(version) ||
                (DefaultVersionSelectorScheme.isLatestVersion(version) && !MavenVersionSelectorScheme.isSubstituableLatest(version))
            ) {
                publicationWarningsCollector.addIncompatible(String.format("%s:%s:%s declared with a Maven incompatible version notation", groupId, artifactId, version));
            }

            return new Coordinates(groupId, artifactId, versionRangeMapper.map(version));
        }

        private static MavenDependency newDependency(
            Coordinates coordinates,
            @Nullable String type,
            @Nullable String classifier,
            @Nullable String scope,
            Set<ExcludeRule> excludeRules,
            boolean optional
        ) {
            return new DefaultMavenDependency(
                coordinates.group, coordinates.name, coordinates.version,
                type, classifier, scope, excludeRules, optional
            );
        }

        private static Path getIdentityPath(ProjectDependency dependency) {
            return ((ProjectDependencyInternal) dependency).getIdentityPath();
        }

        private static Set<ExcludeRule> getExcludeRules(Set<ExcludeRule> globalExcludes, ModuleDependency dependency) {
            if (!dependency.isTransitive()) {
                return EXCLUDE_ALL_RULE;
            }

            Set<ExcludeRule> excludeRules = dependency.getExcludeRules();
            if (excludeRules.isEmpty()) {
                return globalExcludes;
            }

            if (globalExcludes.isEmpty()) {
                return excludeRules;
            }

            return Sets.union(globalExcludes, excludeRules);
        }
    }

    public static class DependencyResult {
        private final MavenPomDependencies dependencies;
        private final PublicationWarningsCollector warnings;

        public DependencyResult(
            MavenPomDependencies dependencies,
            PublicationWarningsCollector warnings
        ) {
            this.warnings = warnings;
            this.dependencies = dependencies;
        }

        public MavenPomDependencies getDependencies() {
            return dependencies;
        }

        public PublicationWarningsCollector getWarnings() {
            return warnings;
        }
    }

    /**
     * Similar to {@link ModuleVersionIdentifier}, but allows a null version.
     */
    private static class Coordinates {
        public final String group;
        public final String name;
        public final String version;

        public Coordinates(String group, String name, @Nullable String version) {
            this.group = group;
            this.name = name;
            this.version = version;
        }

        public static Coordinates from(ModuleVersionIdentifier identifier) {
            return new Coordinates(identifier.getGroup(), identifier.getName(), identifier.getVersion());
        }
    }

    private static class ArtifactKey {
        final File file;
        final String classifier;
        final String extension;

        public ArtifactKey(File file, @Nullable String classifier, @Nullable String extension) {
            this.file = file;
            this.classifier = classifier;
            this.extension = extension;
        }

        @Override
        public boolean equals(Object obj) {
            ArtifactKey other = (ArtifactKey) obj;
            return file.equals(other.file) && Objects.equal(classifier, other.classifier) && Objects.equal(extension, other.extension);
        }

        @Override
        public int hashCode() {
            return file.hashCode() ^ Objects.hashCode(classifier, extension);
        }
    }

    /**
     * This is used to de-duplicate dependencies based on relevant contents.
     * In particular, versions are ignored.
     */
    private static class PublishedDependency {
        private final String group;
        private final String name;
        private final String targetConfiguration;
        private final AttributeContainer attributes;
        private final Set<DependencyArtifact> artifacts;
        private final Set<ExcludeRule> excludeRules;
        private final List<Capability> requestedCapabilities;

        private PublishedDependency(String group, String name, String targetConfiguration, AttributeContainer attributes, Set<DependencyArtifact> artifacts, Set<ExcludeRule> excludeRules, List<Capability> requestedCapabilities) {
            this.group = group;
            this.name = name;
            this.targetConfiguration = targetConfiguration;
            this.attributes = attributes;
            this.artifacts = artifacts;
            this.excludeRules = excludeRules;
            this.requestedCapabilities = requestedCapabilities;
        }

        static PublishedDependency of(ModuleDependency dep) {
            return new PublishedDependency(
                dep.getGroup(),
                dep.getName(),
                dep.getTargetConfiguration(),
                dep.getAttributes(),
                dep.getArtifacts(),
                dep.getExcludeRules(),
                dep.getRequestedCapabilities()
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PublishedDependency that = (PublishedDependency) o;
            return Objects.equal(group, that.group) &&
                Objects.equal(name, that.name) &&
                Objects.equal(targetConfiguration, that.targetConfiguration) &&
                Objects.equal(attributes, that.attributes) &&
                Objects.equal(artifacts, that.artifacts) &&
                Objects.equal(excludeRules, that.excludeRules) &&
                Objects.equal(requestedCapabilities, that.requestedCapabilities);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(group, name, targetConfiguration, attributes, artifacts, excludeRules, requestedCapabilities);
        }
    }
}
