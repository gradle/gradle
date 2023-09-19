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
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.publish.internal.component.IvyPublishingAwareVariant;
import org.gradle.api.publish.internal.mapping.DefaultVariantDependencyResolverFactory;
import org.gradle.api.publish.internal.mapping.VariantDependencyResolver;
import org.gradle.api.publish.internal.mapping.VariantDependencyResolverFactory;
import org.gradle.api.publish.internal.validation.PublicationErrorChecker;
import org.gradle.api.publish.internal.validation.PublicationWarningsCollector;
import org.gradle.api.publish.internal.validation.VariantWarningCollector;
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
    private final NotationParser<Object, IvyArtifact> ivyArtifactParser;
    private final DocumentationRegistry documentationRegistry;
    private final CollectionCallbackActionDecorator collectionCallbackActionDecorator;
    private final VariantDependencyResolverFactory variantDependencyResolverFactory;

    public IvyComponentParser(
        Instantiator instantiator,
        PlatformSupport platformSupport,
        ProjectDependencyPublicationResolver projectDependencyResolver,
        NotationParser<Object, IvyArtifact> ivyArtifactParser,
        DocumentationRegistry documentationRegistry,
        VersionMappingStrategyInternal versionMappingStrategy,
        CollectionCallbackActionDecorator collectionCallbackActionDecorator
    ) {
        this.instantiator = instantiator;
        this.platformSupport = platformSupport;
        this.ivyArtifactParser = ivyArtifactParser;
        this.documentationRegistry = documentationRegistry;
        this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
        this.variantDependencyResolverFactory = new DefaultVariantDependencyResolverFactory(projectDependencyResolver, versionMappingStrategy);
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

    public ParsedDependencyResult parseDependencies(SoftwareComponentInternal component) {
        PublicationErrorChecker.checkForUnpublishableAttributes(component, documentationRegistry);

        DefaultIvyDependencySet ivyDependencies = instantiator.newInstance(DefaultIvyDependencySet.class, collectionCallbackActionDecorator);

        PublicationWarningsCollector publicationWarningsCollector =
            new PublicationWarningsCollector(LOG, UNSUPPORTED_FEATURE, "", PUBLICATION_WARNING_FOOTER, "suppressIvyMetadataWarningsFor");

        for (SoftwareComponentVariant variant : component.getUsages()) {
            VariantWarningCollector warnings = publicationWarningsCollector.warningCollectorFor(variant.getName());

            VariantDependencyResolver dependencyResolver = variantDependencyResolverFactory.createResolver(variant, (organization, module, declaredVersion) -> {
                if (declaredVersion == null) {
                    // Version mapping is disabled or did not discover coordinates.
                    warnings.addUnsupported(String.format("%s:%s declared without version", organization, module));
                }
                return declaredVersion;
            });

            VariantDependencyFactory dependencyFactory = new VariantDependencyFactory(dependencyResolver, warnings);

            for (ModuleDependency dependency : variant.getDependencies()) {
                String confMapping = confMappingFor(variant, dependency);
                if (platformSupport.isTargetingPlatform(dependency)) {
                    warnings.addUnsupported(String.format("%s:%s:%s declared as platform", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                }
                ivyDependencies.add(dependencyFactory.convertDependency(dependency, confMapping));
            }

            if (!variant.getDependencyConstraints().isEmpty()) {
                for (DependencyConstraint constraint : variant.getDependencyConstraints()) {
                    warnings.addUnsupported(String.format("%s:%s:%s declared as a dependency constraint", constraint.getGroup(), constraint.getName(), constraint.getVersion()));
                }
            }
            if (!variant.getCapabilities().isEmpty()) {
                for (Capability capability : variant.getCapabilities()) {
                    warnings.addVariantUnsupported(String.format("Declares capability %s:%s:%s which cannot be mapped to Ivy", capability.getGroup(), capability.getName(), capability.getVersion()));
                }
            }
        }

        return new ParsedDependencyResult(
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

        private final VariantDependencyResolver dependencyResolver;
        private final VariantWarningCollector warnings;

        public VariantDependencyFactory(
            VariantDependencyResolver dependencyResolver,
            VariantWarningCollector warnings
        ) {
            this.dependencyResolver = dependencyResolver;
            this.warnings = warnings;
        }

        private IvyDependency convertDependency(ModuleDependency dependency, String confMapping) {
            VariantDependencyResolver.ResolvedCoordinates coordinates = dependencyResolver.resolveVariantCoordinates(dependency, warnings);

            String revConstraint = null;
            if (!(dependency instanceof ProjectDependency) &&
                dependency.getVersion() != null &&
                isDynamicVersion(dependency.getVersion())
            ) {
                revConstraint = dependency.getVersion();
            }

            return new DefaultIvyDependency(
                coordinates.getGroup(),
                coordinates.getName(),
                nullToEmpty(coordinates.getVersion()),
                confMapping,
                dependency.isTransitive(),
                revConstraint,
                dependency.getArtifacts(),
                dependency.getExcludeRules()
            );
        }

        private static boolean isDynamicVersion(String version) {
            return !ExactVersionSelector.isExact(version);
        }
    }

    public static class ParsedDependencyResult {
        private final DefaultIvyDependencySet dependencies;
        private final PublicationWarningsCollector warnings;

        public ParsedDependencyResult(
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
