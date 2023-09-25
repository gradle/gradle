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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.MavenVersionSelectorScheme;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.publish.internal.component.MavenPublishingAwareVariant;
import org.gradle.api.publish.internal.mapping.ComponentDependencyResolver;
import org.gradle.api.publish.internal.mapping.DependencyCoordinateResolverFactory;
import org.gradle.api.publish.internal.mapping.ResolvedCoordinates;
import org.gradle.api.publish.internal.mapping.VariantDependencyResolver;
import org.gradle.api.publish.internal.validation.PublicationWarningsCollector;
import org.gradle.api.publish.internal.validation.VariantWarningCollector;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenDependency;
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenPomDependencies;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependency;
import org.gradle.api.publish.maven.internal.dependencies.MavenPomDependencies;
import org.gradle.api.publish.maven.internal.dependencies.VersionRangeMapper;
import org.gradle.api.publish.maven.internal.validation.MavenPublicationErrorChecker;
import org.gradle.internal.typeconversion.NotationParser;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Encapsulates all logic required to extract data from a {@link SoftwareComponentInternal} in order to
 * transform it to a representation compatible with Maven.
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

    private final PlatformSupport platformSupport;
    private final VersionRangeMapper versionRangeMapper;
    private final DocumentationRegistry documentationRegistry;
    private final NotationParser<Object, MavenArtifact> mavenArtifactParser;
    private final DependencyCoordinateResolverFactory dependencyCoordinateResolverFactory;

    @Inject
    public MavenComponentParser(
        PlatformSupport platformSupport,
        VersionRangeMapper versionRangeMapper,
        DocumentationRegistry documentationRegistry,
        NotationParser<Object, MavenArtifact> mavenArtifactParser,
        DependencyCoordinateResolverFactory dependencyCoordinateResolverFactory
    ) {
        this.platformSupport = platformSupport;
        this.versionRangeMapper = versionRangeMapper;
        this.documentationRegistry = documentationRegistry;
        this.mavenArtifactParser = mavenArtifactParser;
        this.dependencyCoordinateResolverFactory = dependencyCoordinateResolverFactory;
    }

    public Set<MavenArtifact> parseArtifacts(SoftwareComponentInternal component) {
        // TODO Artifact names should be determined by the source variant. We shouldn't
        //      blindly "pass-through" the artifact file name.
        Set<ArtifactKey> seenArtifacts = Sets.newHashSet();
        return createSortedVariantsStream(component)
            .flatMap(variant -> variant.getArtifacts().stream())
            .filter(artifact -> {
                ArtifactKey key = new ArtifactKey(artifact.getFile(), artifact.getClassifier(), artifact.getExtension());
                return seenArtifacts.add(key);
            })
            .map(mavenArtifactParser::parseNotation)
            .collect(Collectors.toSet());
    }

    public ParsedDependencyResult parseDependencies(
        SoftwareComponentInternal component,
        VersionMappingStrategyInternal versionMappingStrategy,
        ModuleVersionIdentifier coordinates
    ) {
        MavenPublicationErrorChecker.checkForUnpublishableAttributes(component, documentationRegistry);

        PublicationWarningsCollector publicationWarningsCollector = new PublicationWarningsCollector(
            LOG, UNSUPPORTED_FEATURE, INCOMPATIBLE_FEATURE, PUBLICATION_WARNING_FOOTER, "suppressPomMetadataWarningsFor");

        Set<MavenDependencyKey> seenDependencies = Sets.newHashSet();
        Set<DependencyConstraint> seenConstraints = Sets.newHashSet();

        List<MavenDependency> dependencies = new ArrayList<>();
        List<MavenDependency> constraints = new ArrayList<>();
        List<MavenDependency> platforms = new ArrayList<>();

        createSortedVariantsStream(component).forEach(variant -> {
            VariantWarningCollector warnings = publicationWarningsCollector.warningCollectorFor(variant.getName());
            MavenPublishingAwareVariant.ScopeMapping scopeMapping = MavenPublishingAwareVariant.scopeForVariant(variant);
            String scope = scopeMapping.getScope();
            boolean optional = scopeMapping.isOptional();
            Set<ExcludeRule> globalExcludes = variant.getGlobalExcludes();

            DependencyCoordinateResolverFactory.DependencyResolvers resolvers = dependencyCoordinateResolverFactory.createCoordinateResolvers(variant, versionMappingStrategy);
            MavenDependencyFactory dependencyFactory = new MavenDependencyFactory(
                warnings,
                resolvers.getVariantResolver(),
                resolvers.getComponentResolver(),
                versionRangeMapper,
                scope,
                optional,
                globalExcludes
            );

            Consumer<MavenDependency> dependencyAdder = dep -> {
                if (seenDependencies.add(MavenDependencyKey.of(dep))) {
                    dependencies.add(dep);
                }
            };

            for (ModuleDependency dependency : variant.getDependencies()) {
                if (isDependencyWithDefaultArtifact(dependency) && dependencyMatchesProject(dependency, coordinates)) {
                    // We skip all self referencing dependency declarations, unless they have custom artifact information
                    continue;
                }
                if (platformSupport.isTargetingPlatform(dependency)) {
                    dependencyFactory.convertImportDependencyConstraint(dependency, platforms::add);
                } else {
                    dependencyFactory.convertDependency(dependency, dependencyAdder);
                }
            }

            for (DependencyConstraint dependency : variant.getDependencyConstraints()) {
                if (seenConstraints.add(dependency)) { // TODO: De-duplicate constraints like we do with MavenDependencyKey
                    if (dependency instanceof DefaultProjectDependencyConstraint || dependency.getVersion() != null) {
                        dependencyFactory.convertDependencyConstraint(dependency, constraints::add);
                    } else {
                        // Some dependency constraints, like those with rejectAll() have no version and do not map to Maven.
                        warnings.addIncompatible(String.format("constraint %s:%s declared with a Maven incompatible version notation", dependency.getGroup(), dependency.getName()));
                    }
                }
            }

            if (!variant.getCapabilities().isEmpty()) {
                for (Capability capability : variant.getCapabilities()) {
                    if (isNotDefaultCapability(capability, coordinates)) {
                        warnings.addVariantUnsupported(String.format("Declares capability %s:%s:%s which cannot be mapped to Maven", capability.getGroup(), capability.getName(), capability.getVersion()));
                    }
                }
            }
        });

        return new ParsedDependencyResult(
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

    private static Stream<? extends SoftwareComponentVariant> createSortedVariantsStream(SoftwareComponentInternal component) {
        return component.getUsages().stream()
            .sorted(Comparator.comparing(MavenPublishingAwareVariant::scopeForVariant));
    }

    /**
     * Converts the DSL representation of a variant's dependencies to one suitable for a POM.
     * Dependencies are transformed by querying the provided {@link VariantDependencyResolver}
     * for their resolved coordinates.
     */
    private static class MavenDependencyFactory {

        private final VariantWarningCollector warnings;
        private final VariantDependencyResolver variantDependencyResolver;
        private final ComponentDependencyResolver componentDependencyResolver;
        private final VersionRangeMapper versionRangeMapper;

        private final String scope;
        private final boolean optional;
        private final Set<ExcludeRule> globalExcludes;

        public MavenDependencyFactory(
            VariantWarningCollector warnings,
            VariantDependencyResolver variantDependencyResolver,
            ComponentDependencyResolver componentDependencyResolver,
            VersionRangeMapper versionRangeMapper,
            String scope,
            boolean optional,
            Set<ExcludeRule> globalExcludes
        ) {
            this.warnings = warnings;
            this.variantDependencyResolver = variantDependencyResolver;
            this.componentDependencyResolver = componentDependencyResolver;
            this.versionRangeMapper = versionRangeMapper;
            this.scope = scope;
            this.optional = optional;
            this.globalExcludes = globalExcludes;
        }

    private void convertDependency(ModuleDependency dependency, Consumer<MavenDependency> collector) {

            // TODO: These warnings are not very useful. There are cases where a dependency declared
            // with attributes or capabilities is correctly converted to maven coordinates -- even
            // when dependency mapping is disabled.
            // At the very least, we do not want these warnings when dependency mapping is enabled.
            if (!dependency.getAttributes().isEmpty()) {
                warnings.addUnsupported(String.format("%s:%s:%s declared with Gradle attributes", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
            }
            if (!dependency.getRequestedCapabilities().isEmpty()) {
                warnings.addUnsupported(String.format("%s:%s:%s declared with Gradle capabilities", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
            }

            Set<ExcludeRule> allExcludeRules = getExcludeRules(globalExcludes, dependency);
            ResolvedCoordinates coordinates = resolveDependency(dependency);

            if (dependency.getArtifacts().isEmpty()) {
                collector.accept(newDependency(coordinates, null, null, scope, allExcludeRules, optional));
                return;
            }

            for (DependencyArtifact artifact : dependency.getArtifacts()) {
                ResolvedCoordinates artifactCoordinates = coordinates;
                if (!artifact.getName().equals(dependency.getName())) {
                    // TODO: We should not allow the artifact name to change the coordinates.
                    //  Artifacts with name different from the coordinate name is not supported in Maven.
                    //  This behavior should be deprecated.
                    artifactCoordinates = ResolvedCoordinates.create(
                        coordinates.getGroup(),
                        artifact.getName(),
                        coordinates.getVersion()
                    );
                }

                collector.accept(newDependency(artifactCoordinates, artifact.getType(), artifact.getClassifier(), scope, allExcludeRules, optional));
            }
        }

        private void convertDependencyConstraint(DependencyConstraint dependency, Consumer<MavenDependency> collector) {
            // We use component-level precision for dependency constraints since it is hard to implement correctly.
            // To publish a dependency constraint to Maven, we would need to publish a constraint for _each_ coordinate
            // that the component could be resolved to. Resolution results do not support this type of query, so this
            // remains incomplete for now.

            ResolvedCoordinates identifier;
            if (dependency instanceof DefaultProjectDependencyConstraint) {
                identifier = componentDependencyResolver.resolveComponentCoordinates((DefaultProjectDependencyConstraint) dependency);
            } else {
                identifier = componentDependencyResolver.resolveComponentCoordinates(dependency);
                if (identifier == null) {
                    identifier = convertDeclaredCoordinates(dependency.getGroup(), dependency.getName(), dependency.getVersion());
                }
            }

            if (identifier.getVersion() == null) {
                return;
            }

            // Do not publish scope, as it has too different of semantics in Maven
            collector.accept(newDependency(identifier, null, null, null, Collections.emptySet(), false));
        }

        private void convertImportDependencyConstraint(ModuleDependency dependency, Consumer<MavenDependency> collector) {
            ResolvedCoordinates identifier = resolveDependency(dependency);
            collector.accept(newDependency(identifier, "pom", null, "import", Collections.emptySet(), false));
        }

        private ResolvedCoordinates resolveDependency(ModuleDependency dependency) {
            if (dependency instanceof ProjectDependency) {
                return variantDependencyResolver.resolveVariantCoordinates((ProjectDependency) dependency, warnings);
            } else if (dependency instanceof ExternalDependency) {
                ResolvedCoordinates identifier = variantDependencyResolver.resolveVariantCoordinates((ExternalDependency) dependency, warnings);
                if (identifier != null) {
                    return identifier;
                }

                return convertDeclaredCoordinates(dependency.getGroup(), dependency.getName(), dependency.getVersion());
            } else {
                throw new GradleException("Unsupported dependency type: " + dependency.getClass().getName());
            }
        }

        private ResolvedCoordinates convertDeclaredCoordinates(String groupId, String artifactId, @Nullable String version) {
            if (version == null) {
                return ResolvedCoordinates.create(groupId, artifactId, null);
            }

            // Attempt to convert Gradle's rich version notation to Maven's.
            if (DefaultVersionSelectorScheme.isSubVersion(version) ||
                (DefaultVersionSelectorScheme.isLatestVersion(version) && !MavenVersionSelectorScheme.isSubstituableLatest(version))
            ) {
                warnings.addIncompatible(String.format("%s:%s:%s declared with a Maven incompatible version notation", groupId, artifactId, version));
            }

            return ResolvedCoordinates.create(
                groupId, artifactId, versionRangeMapper.map(version)
            );
        }

        private static MavenDependency newDependency(
            ResolvedCoordinates coordinates,
            @Nullable String type,
            @Nullable String classifier,
            @Nullable String scope,
            Set<ExcludeRule> excludeRules,
            boolean optional
        ) {
            return new DefaultMavenDependency(
                coordinates.getGroup(), coordinates.getName(), coordinates.getVersion(),
                type, classifier, scope, excludeRules, optional
            );
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

    public static class ParsedDependencyResult {
        private final MavenPomDependencies dependencies;
        private final PublicationWarningsCollector warnings;

        public ParsedDependencyResult(
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
            return file.equals(other.file) && Objects.equals(classifier, other.classifier) && Objects.equals(extension, other.extension);
        }

        @Override
        public int hashCode() {
            return file.hashCode() ^ Objects.hash(classifier, extension);
        }
    }

    /**
     * This is used to de-duplicate dependencies based on relevant contents.
     * In particular, version and scope are ignored.
     */
    private static class MavenDependencyKey {
        private final String group;
        private final String name;
        private final String type;
        private final String classifier;
        private final boolean optional;

        private MavenDependencyKey(
            String group,
            String name,
            @Nullable String type,
            @Nullable String classifier,
            boolean optional
        ) {
            this.group = group;
            this.name = name;
            this.type = type;
            this.classifier = classifier;
            this.optional = optional;
        }

        static MavenDependencyKey of(MavenDependency dep) {
            return new MavenDependencyKey(
                dep.getGroupId(),
                dep.getArtifactId(),
                dep.getType(),
                dep.getClassifier(),
                dep.isOptional()
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
            MavenDependencyKey that = (MavenDependencyKey) o;
            return Objects.equals(group, that.group) &&
                Objects.equals(name, that.name) &&
                Objects.equals(type, that.type) &&
                Objects.equals(classifier, that.classifier) &&
                optional == that.optional;
        }

        @Override
        public int hashCode() {
            return Objects.hash(group, name, type, classifier, optional);
        }
    }
}
