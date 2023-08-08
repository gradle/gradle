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

package org.gradle.api.publish.ivy.internal.publication;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.publish.internal.component.IvyPublishingAwareVariant;
import org.gradle.api.publish.internal.validation.PublicationErrorChecker;
import org.gradle.api.publish.internal.validation.PublicationWarningsCollector;
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyConfigurationContainer;
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependency;
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependencySet;
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyExcludeRule;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependency;
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Strings.nullToEmpty;

/**
 * Encapsulates all logic required to extract data from a {@link SoftwareComponentInternal} in order to
 * transform it to a representation compatible with Ivy.
 */
public class IvyComponentParser {

    @VisibleForTesting
    public static final String UNSUPPORTED_FEATURE = " contains dependencies that cannot be represented in a published ivy descriptor.";
    @VisibleForTesting
    public static final String PUBLICATION_WARNING_FOOTER =
        "These issues indicate information that is lost in the published 'ivy.xml' metadata file, " +
            "which may be an issue if the published library is consumed by an old Gradle version or Apache Ivy.\n" +
            "The 'module' metadata file, which is used by Gradle 6+ is not affected.";

    private final static Logger LOG = Logging.getLogger(IvyComponentParser.class);

    private static final String API_VARIANT = "api";
    private static final String API_ELEMENTS_VARIANT = "apiElements";
    private static final String RUNTIME_VARIANT = "runtime";
    private static final String RUNTIME_ELEMENTS_VARIANT = "runtimeElements";

    private final Instantiator instantiator;
    private final PlatformSupport platformSupport;
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final NotationParser<Object, IvyArtifact> ivyArtifactParser;
    private final DocumentationRegistry documentationRegistry;
    private final CollectionCallbackActionDecorator collectionCallbackActionDecorator;

    private final PublicationWarningsCollector publicationWarningsCollector =
        new PublicationWarningsCollector(LOG, UNSUPPORTED_FEATURE, "", PUBLICATION_WARNING_FOOTER, "suppressIvyMetadataWarningsFor");

    public IvyComponentParser(
        Instantiator instantiator,
        PlatformSupport platformSupport,
        ProjectDependencyPublicationResolver projectDependencyResolver,
        NotationParser<Object, IvyArtifact> ivyArtifactParser,
        DocumentationRegistry documentationRegistry,
        CollectionCallbackActionDecorator collectionCallbackActionDecorator
    ) {
        this.instantiator = instantiator;
        this.platformSupport = platformSupport;
        this.projectDependencyResolver = projectDependencyResolver;
        this.ivyArtifactParser = ivyArtifactParser;
        this.documentationRegistry = documentationRegistry;
        this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
    }

    public IvyConfigurationContainer parseConfigurations(SoftwareComponentInternal component) {
        IvyConfigurationContainer configurations
            = instantiator.newInstance(DefaultIvyConfigurationContainer.class, instantiator, collectionCallbackActionDecorator);

        IvyConfiguration defaultConfiguration = configurations.maybeCreate("default");
        for (SoftwareComponentVariant variant : component.getUsages()) {
            String conf = mapVariantNameToIvyConfiguration(variant.getName());
            configurations.maybeCreate(conf);
            if (defaultShouldExtend(variant)) {
                defaultConfiguration.extend(conf);
            }
        }

        return configurations;
    }

    /**
     * In general, default extends all configurations such that you get 'everything' when depending on default.
     * If a variant is optional, however it is not included.
     * If a variant represents the Java API variant, it is also not included, because the Java Runtime variant already includes everything
     * (including both also works but would lead to some duplication, that might break backwards compatibility in certain cases).
     */
    private static boolean defaultShouldExtend(SoftwareComponentVariant variant) {
        if (!(variant instanceof IvyPublishingAwareVariant)) {
            return true;
        }
        if (((IvyPublishingAwareVariant) variant).isOptional()) {
            return false;
        }
        return !isJavaApiVariant(variant.getName());
    }

    private static boolean isJavaRuntimeVariant(String variantName) {
        return RUNTIME_VARIANT.equals(variantName) || RUNTIME_ELEMENTS_VARIANT.equals(variantName);
    }

    private static boolean isJavaApiVariant(String variantName) {
        return API_VARIANT.equals(variantName) || API_ELEMENTS_VARIANT.equals(variantName);
    }

    public Set<IvyArtifact> parseArtifacts(SoftwareComponentInternal component) {
        Set<IvyArtifact> artifacts = new LinkedHashSet<>();

        Map<String, IvyArtifact> seenArtifacts = Maps.newHashMap();
        for (SoftwareComponentVariant variant : component.getUsages()) {
            String conf = mapVariantNameToIvyConfiguration(variant.getName());
            for (PublishArtifact publishArtifact : variant.getArtifacts()) {
                String key = artifactKey(publishArtifact);
                IvyArtifact ivyArtifact = seenArtifacts.get(key);
                if (ivyArtifact == null) {
                    ivyArtifact = ivyArtifactParser.parseNotation(publishArtifact);
                    ivyArtifact.setConf(conf);
                    seenArtifacts.put(key, ivyArtifact);
                    artifacts.add(ivyArtifact);
                } else {
                    ivyArtifact.setConf(ivyArtifact.getConf() + "," + conf);
                }
            }
        }

        return artifacts;
    }

    private static String artifactKey(PublishArtifact publishArtifact) {
        return publishArtifact.getName() + ":" + publishArtifact.getType() + ":" + publishArtifact.getExtension() + ":" + publishArtifact.getClassifier();
    }

    public DependencyResult parseDependencies(
        SoftwareComponentInternal component,
        VersionMappingStrategyInternal versionMappingStrategy
    ) {
        PublicationErrorChecker.checkForUnpublishableAttributes(component, documentationRegistry);

        DefaultIvyDependencySet ivyDependencies = instantiator.newInstance(DefaultIvyDependencySet.class, collectionCallbackActionDecorator);

        for (SoftwareComponentVariant variant : component.getUsages()) {
            publicationWarningsCollector.newContext(variant.getName());

            ImmutableAttributes attributes = ((AttributeContainerInternal) variant.getAttributes()).asImmutable();
            VariantVersionMappingStrategyInternal variantVersionMappingStrategy = versionMappingStrategy.findStrategyForVariant(attributes);
            VariantDependencyFactory dependencyFactory = new VariantDependencyFactory(projectDependencyResolver, variantVersionMappingStrategy, publicationWarningsCollector);

            for (ModuleDependency dependency : variant.getDependencies()) {
                String confMapping = confMappingFor(variant, dependency);
                if (!dependency.getAttributes().isEmpty()) {
                    publicationWarningsCollector.addUnsupported(String.format("%s:%s:%s declared with Gradle attributes", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                }
                if (dependency instanceof ProjectDependency) {
                    ivyDependencies.add(dependencyFactory.asProjectDependency((ProjectDependency) dependency, confMapping));
                } else {
                    ExternalDependency externalDependency = (ExternalDependency) dependency;
                    if (platformSupport.isTargetingPlatform(dependency)) {
                        publicationWarningsCollector.addUnsupported(String.format("%s:%s:%s declared as platform", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                    }
                    ivyDependencies.add(dependencyFactory.asExternalDependency(externalDependency, confMapping));
                }
            }

            if (!variant.getDependencyConstraints().isEmpty()) {
                for (DependencyConstraint constraint : variant.getDependencyConstraints()) {
                    publicationWarningsCollector.addUnsupported(String.format("%s:%s:%s declared as a dependency constraint", constraint.getGroup(), constraint.getName(), constraint.getVersion()));
                }
            }
            if (!variant.getCapabilities().isEmpty()) {
                for (Capability capability : variant.getCapabilities()) {
                    publicationWarningsCollector.addVariantUnsupported(String.format("Declares capability %s:%s:%s which cannot be mapped to Ivy", capability.getGroup(), capability.getName(), capability.getVersion()));
                }
            }
        }

        return new DependencyResult(
            ivyDependencies,
            publicationWarningsCollector
        );
    }

    public Set<IvyExcludeRule> parseGlobalExcludes(SoftwareComponentInternal component) {
        Set<IvyExcludeRule> globalExcludes = new LinkedHashSet<>();

        for (SoftwareComponentVariant variant : component.getUsages()) {
            String conf = mapVariantNameToIvyConfiguration(variant.getName());
            for (ExcludeRule excludeRule : variant.getGlobalExcludes()) {
                globalExcludes.add(new DefaultIvyExcludeRule(excludeRule, conf));
            }
        }

        return globalExcludes;
    }

    private static String confMappingFor(SoftwareComponentVariant variant, ModuleDependency dependency) {
        String conf = mapVariantNameToIvyConfiguration(variant.getName());
        String targetConfiguration = dependency.getTargetConfiguration();
        String confMappingTarget = targetConfiguration == null ?
            Dependency.DEFAULT_CONFIGURATION :
            mapVariantNameToIvyConfiguration(dependency.getTargetConfiguration());

        // If the following code is activated implementation/runtime separation will be published to ivy. This however is a breaking change.
        //
        // if (confMappingTarget == null) {
        //     if (variant instanceof MavenPublishingAwareVariant) {
        //         MavenPublishingAwareContext.ScopeMapping mapping = ((MavenPublishingAwareVariant) variant).getScopeMapping();
        //         if (mapping == runtime || mapping == runtime_optional) {
        //             confMappingTarget = "runtime";
        //         }
        //         if (mapping == compile || mapping == compile_optional) {
        //             confMappingTarget = "compile";
        //         }
        //     }
        // }

        return conf + "->" + confMappingTarget;
    }

    /**
     * The variant name usually corresponds to the name of the Gradle configuration on which the variant is based on.
     * For backward compatibility, the 'apiElements' and 'runtimeElements' configurations/variants of the Java ecosystem are named 'compile' and 'runtime' in the publication.
     */
    private static String mapVariantNameToIvyConfiguration(String variantName) {
        if (isJavaApiVariant(variantName)) {
            return "compile";
        }
        if (isJavaRuntimeVariant(variantName)) {
            return "runtime";
        }
        return variantName;
    }

    private static class VariantDependencyFactory {

        private final ProjectDependencyPublicationResolver projectDependencyResolver;
        private final VariantVersionMappingStrategyInternal versionMappingStrategy;
        private final PublicationWarningsCollector publicationWarningsCollector;

        public VariantDependencyFactory(
            ProjectDependencyPublicationResolver projectDependencyResolver,
            VariantVersionMappingStrategyInternal versionMappingStrategy,
            PublicationWarningsCollector publicationWarningsCollector
        ) {
            this.projectDependencyResolver = projectDependencyResolver;
            this.versionMappingStrategy = versionMappingStrategy;
            this.publicationWarningsCollector = publicationWarningsCollector;
        }

        private IvyDependency asProjectDependency(ProjectDependency dependency, String confMapping) {
            Path identityPath = ((ProjectDependencyInternal) dependency).getIdentityPath();
            ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(ModuleVersionIdentifier.class, identityPath);
            return new DefaultIvyDependency(
                identifier.getGroup(),
                identifier.getName(),
                identifier.getVersion(),
                confMapping,
                dependency.isTransitive(),
                resolveCoordinates(identityPath),
                Collections.emptySet(),
                dependency.getExcludeRules()
            );
        }

        private IvyDependency asExternalDependency(ExternalDependency dependency, String confMapping) {
            return new DefaultIvyDependency(
                dependency.getGroup(),
                dependency.getName(),
                nullToEmpty(dependency.getVersion()),
                confMapping,
                dependency.isTransitive(),
                resolveCoordinates(dependency.getGroup(), dependency.getName(), dependency.getVersion(), null),
                dependency.getArtifacts(),
                dependency.getExcludeRules()
            );
        }

        @Nullable
        private ModuleVersionIdentifier resolveCoordinates(Path identityPath) {
            ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(ModuleVersionIdentifier.class, identityPath);
            return resolveCoordinates(identifier.getGroup(), identifier.getName(), identifier.getVersion(), identityPath);
        }

        @Nullable
        private ModuleVersionIdentifier resolveCoordinates(String organization, String module, @Nullable String revision, @Nullable Path identityPath) {
            ModuleVersionIdentifier resolvedVersion = versionMappingStrategy.maybeResolveVersion(organization, module, identityPath);
            if (resolvedVersion != null) {
                return resolvedVersion;
            }

            // Version mapping is disabled or did not discover coordinates.
            if (revision == null) {
                publicationWarningsCollector.addUnsupported(String.format("%s:%s declared without version", organization, module));
            }

            return null;
        }
    }

    public static class DependencyResult {
        private final DefaultIvyDependencySet dependencies;
        private final PublicationWarningsCollector warnings;

        public DependencyResult(
            DefaultIvyDependencySet ivyDependencies,
            PublicationWarningsCollector warnings
        ) {
            this.dependencies = ivyDependencies;
            this.warnings = warnings;
        }

        public DefaultIvyDependencySet getDependencies() {
            return dependencies;
        }

        public PublicationWarningsCollector getWarnings() {
            return warnings;
        }
    }
}
