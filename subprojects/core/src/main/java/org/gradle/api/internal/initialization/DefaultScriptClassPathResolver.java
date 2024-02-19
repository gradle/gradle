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
package org.gradle.api.internal.initialization;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dsl.DependencyHandlerInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.initialization.transform.BaseInstrumentingArtifactTransform;
import org.gradle.api.internal.initialization.transform.CacheInstrumentationTypeRegistryBuildService;
import org.gradle.api.internal.initialization.transform.CollectDirectClassSuperTypesTransform;
import org.gradle.api.internal.initialization.transform.ExternalDependencyInstrumentingArtifactTransform;
import org.gradle.api.internal.initialization.transform.MergeSuperTypesTransform;
import org.gradle.api.internal.initialization.transform.ProjectDependencyInstrumentingArtifactTransform;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.provider.Provider;
import org.gradle.internal.agents.AgentStatus;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.classpath.TransformedClassPath;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;
import org.gradle.internal.logging.util.Log4jBannedVersion;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.InstrumentationPhase.COLLECTED_DIRECT_SUPER_TYPES;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.InstrumentationPhase.INSTRUMENTED_AND_UPGRADED;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.InstrumentationPhase.INSTRUMENTED_ONLY;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.InstrumentationPhase.MERGED_SUPER_TYPES;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.InstrumentationPhase.NOT_INSTRUMENTED;
import static org.gradle.internal.classpath.TransformedClassPath.AGENT_INSTRUMENTATION_MARKER_FILE_NAME;
import static org.gradle.internal.classpath.TransformedClassPath.INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME;
import static org.gradle.internal.classpath.TransformedClassPath.LEGACY_INSTRUMENTATION_MARKER_FILE_NAME;
import static org.gradle.internal.classpath.TransformedClassPath.ORIGINAL_FILE_PLACEHOLDER_SUFFIX;
import static org.gradle.internal.classpath.TransformedClassPath.ORIGINAL_FILE_DOES_NOT_EXIST_MARKER;

public class DefaultScriptClassPathResolver implements ScriptClassPathResolver {

    private static final Set<DependencyFactoryInternal.ClassPathNotation> GRADLE_API_NOTATIONS = EnumSet.of(
        DependencyFactoryInternal.ClassPathNotation.GRADLE_API,
        DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY
    );

    public enum InstrumentationPhase {
        NOT_INSTRUMENTED,
        COLLECTED_DIRECT_SUPER_TYPES,
        MERGED_SUPER_TYPES,
        INSTRUMENTED_AND_UPGRADED,
        INSTRUMENTED_ONLY
    }

    public static final Attribute<InstrumentationPhase> INSTRUMENTATION_PHASE_ATTRIBUTE = Attribute.of("org.gradle.internal.instrumentation.phase", InstrumentationPhase.class);
    private final NamedObjectInstantiator instantiator;
    private final AgentStatus agentStatus;
    private final ConfigurableFileCollection classHierarchy;
    private final Gradle gradle;

    public DefaultScriptClassPathResolver(
        NamedObjectInstantiator instantiator,
        AgentStatus agentStatus,
        FileCollectionFactory fileCollectionFactory,
        Gradle gradle
    ) {
        this.instantiator = instantiator;
        this.agentStatus = agentStatus;
        this.classHierarchy = fileCollectionFactory.configurableFiles();
        this.gradle = gradle;
    }

    @Override
    public void prepareDependencyHandler(DependencyHandler dependencyHandler) {
        ((DependencyHandlerInternal) dependencyHandler).getDefaultArtifactAttributes()
            .attribute(INSTRUMENTATION_PHASE_ATTRIBUTE, NOT_INSTRUMENTED);

        // Register instrumentation transforms
        Provider<CacheInstrumentationTypeRegistryBuildService> service = getOrRegisterNewService();
        dependencyHandler.registerTransform(
            CollectDirectClassSuperTypesTransform.class,
            spec -> {
                spec.getFrom().attribute(INSTRUMENTATION_PHASE_ATTRIBUTE, NOT_INSTRUMENTED);
                spec.getTo().attribute(INSTRUMENTATION_PHASE_ATTRIBUTE, COLLECTED_DIRECT_SUPER_TYPES);
                spec.parameters(params -> params.getBuildService().set(service));
            }
        );

        dependencyHandler.registerTransform(
            MergeSuperTypesTransform.class,
            spec -> {
                spec.getFrom().attribute(INSTRUMENTATION_PHASE_ATTRIBUTE, COLLECTED_DIRECT_SUPER_TYPES);
                spec.getTo().attribute(INSTRUMENTATION_PHASE_ATTRIBUTE, MERGED_SUPER_TYPES);
                spec.parameters(params -> {
                    params.getBuildService().set(service);
                    params.getOriginalClasspath().setFrom(service.map(it -> it.getParameters().getOriginalClasspath()));
                });
            }
        );

        registerTransform(dependencyHandler, ExternalDependencyInstrumentingArtifactTransform.class, service, MERGED_SUPER_TYPES, INSTRUMENTED_AND_UPGRADED);
        registerTransform(dependencyHandler, ProjectDependencyInstrumentingArtifactTransform.class, service, NOT_INSTRUMENTED, INSTRUMENTED_ONLY);
    }

    private void registerTransform(
        DependencyHandler dependencyHandler,
        Class<? extends BaseInstrumentingArtifactTransform> transform,
        Provider<CacheInstrumentationTypeRegistryBuildService> service,
        InstrumentationPhase fromPhase,
        InstrumentationPhase toPhase
    ) {
        dependencyHandler.registerTransform(
            transform,
            spec -> {
                spec.getFrom().attribute(INSTRUMENTATION_PHASE_ATTRIBUTE, fromPhase);
                spec.getTo().attribute(INSTRUMENTATION_PHASE_ATTRIBUTE, toPhase);
                spec.parameters(parameters -> {
                    parameters.getBuildService().set(service);
                    parameters.getAgentSupported().set(agentStatus.isAgentInstrumentationEnabled());
                });
            }
        );
    }

    private Provider<CacheInstrumentationTypeRegistryBuildService> getOrRegisterNewService() {
        return gradle.getSharedServices().registerIfAbsent(
            CacheInstrumentationTypeRegistryBuildService.class.getName() + "@" + System.identityHashCode(this),
            CacheInstrumentationTypeRegistryBuildService.class,
            spec -> spec.getParameters().getClassHierarchy().setFrom(classHierarchy)
        );
    }

    @Override
    public void prepareClassPath(Configuration configuration, DependencyHandler dependencyHandler) {
        // should ideally reuse the `JvmPluginServices` but this code is too low level
        // and this service is therefore not available!
        AttributeContainer attributes = configuration.getAttributes();
        attributes.attribute(Usage.USAGE_ATTRIBUTE, instantiator.named(Usage.class, Usage.JAVA_RUNTIME));
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, instantiator.named(Category.class, Category.LIBRARY));
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, instantiator.named(LibraryElements.class, LibraryElements.JAR));
        attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, instantiator.named(Bundling.class, Bundling.EXTERNAL));
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().getMajorVersion()));
        attributes.attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, instantiator.named(GradlePluginApiVersion.class, GradleVersion.current().getVersion()));

        configuration.getDependencyConstraints().add(dependencyHandler.getConstraints().create(Log4jBannedVersion.LOG4J2_CORE_COORDINATES, constraint -> constraint.version(version -> {
            version.require(Log4jBannedVersion.LOG4J2_CORE_REQUIRED_VERSION);
            version.reject(Log4jBannedVersion.LOG4J2_CORE_VULNERABLE_VERSION_RANGE);
        })));
    }

    @Override
    public ClassPath resolveClassPath(Configuration classpathConfiguration) {
        // Clear build service data after resolution so content can be garbage collected
        return runAndClearBuildServiceAfter(() -> {
            // We resolve class hierarchy before instrumentation, otherwise the resolution can block the whole build
            CacheInstrumentationTypeRegistryBuildService buildService = getOrRegisterNewService().get();
            buildService.getParameters().getClassHierarchy().setFrom(getHierarchyView(classpathConfiguration));
            buildService.getParameters().getOriginalClasspath().setFrom(classpathConfiguration);
            ArtifactCollection instrumentedExternalDependencies = getInstrumentedExternalDependencies(classpathConfiguration);
            ArtifactCollection instrumentedProjectDependencies = getInstrumentedProjectDependencies(classpathConfiguration);
            ClassPath instrumentedClasspath = combineClassPaths(classpathConfiguration.getIncoming().getArtifacts(), instrumentedExternalDependencies, instrumentedProjectDependencies);
            return TransformedClassPath.handleInstrumentingArtifactTransform(instrumentedClasspath);
        });
    }

    private <T> T runAndClearBuildServiceAfter(Supplier<T> action) {
        T value = action.get();
        getOrRegisterNewService().get().clear();
        classHierarchy.unset();
        return value;
    }

    private static FileCollection getHierarchyView(Configuration classpathConfiguration) {
        return classpathConfiguration.getIncoming().artifactView((Action<? super ArtifactView.ViewConfiguration>) config -> {
            config.attributes(it -> it.attribute(INSTRUMENTATION_PHASE_ATTRIBUTE, COLLECTED_DIRECT_SUPER_TYPES));
            config.componentFilter(componentId -> !isGradleApi(componentId) && !isProjectDependency(componentId));
        }).getFiles();
    }

    private static ArtifactCollection getInstrumentedExternalDependencies(Configuration classpathConfiguration) {
        return classpathConfiguration.getIncoming().artifactView((Action<? super ArtifactView.ViewConfiguration>) config -> {
            config.attributes(it -> it.attribute(INSTRUMENTATION_PHASE_ATTRIBUTE, INSTRUMENTED_AND_UPGRADED));
            config.componentFilter(DefaultScriptClassPathResolver::isExternalDependency);
        }).getArtifacts();
    }

    private static ArtifactCollection getInstrumentedProjectDependencies(Configuration classpathConfiguration) {
        return classpathConfiguration.getIncoming().artifactView((Action<? super ArtifactView.ViewConfiguration>) config -> {
            config.attributes(it -> it.attribute(INSTRUMENTATION_PHASE_ATTRIBUTE, INSTRUMENTED_ONLY));
            config.componentFilter(DefaultScriptClassPathResolver::isProjectDependency);
        }).getArtifacts();
    }

    private static boolean isGradleApi(ComponentIdentifier componentId) {
        if (componentId instanceof OpaqueComponentIdentifier) {
            DependencyFactoryInternal.ClassPathNotation classPathNotation = ((OpaqueComponentIdentifier) componentId).getClassPathNotation();
            return DefaultScriptClassPathResolver.GRADLE_API_NOTATIONS.contains(classPathNotation);
        }
        return false;
    }

    private static boolean isProjectDependency(ComponentIdentifier componentId) {
        return componentId instanceof ProjectComponentIdentifier;
    }

    private static boolean isExternalDependency(ComponentIdentifier componentId) {
        return !isGradleApi(componentId) && !isProjectDependency(componentId);
    }

    /**
     * Combines the original classpath with transformed external and project dependencies.
     */
    public static ClassPath combineClassPaths(ArtifactCollection originalClasspath, ArtifactCollection transformedExternalDependencies, ArtifactCollection transformedProjectDependencies) {
        List<ResolvedArtifactResult> originalExternalArtifacts = originalClasspath.getArtifacts().stream()
            .filter(artifact -> isExternalDependency(artifact.getId().getComponentIdentifier()))
            .collect(Collectors.toList());
        List<File> files = new ArrayList<>(combine(originalExternalArtifacts, transformedExternalDependencies));

        List<ResolvedArtifactResult> originalProjectArtifacts = originalClasspath.getArtifacts().stream()
            .filter(artifact -> isProjectDependency(artifact.getId().getComponentIdentifier()))
            .collect(Collectors.toList());
        files.addAll(combine(originalProjectArtifacts, transformedProjectDependencies));

        return DefaultClassPath.of(files);
    }

    private static List<File> combine(List<ResolvedArtifactResult> originalArtifacts, ArtifactCollection transformedCollection) {
        List<File> transformedArtifacts = transformedCollection.getArtifacts().stream()
            .map(ResolvedArtifactResult::getFile)
            .filter(file -> !file.getName().equals(INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME))
            .collect(Collectors.toList());
        checkArgument(originalArtifacts.size() <= transformedArtifacts.size(), "Unexpected number of transformed artifacts");

        int i = 0;
        List<File> files = new ArrayList<>();
        for (ResolvedArtifactResult originalArtifact : originalArtifacts) {
            File original = originalArtifact.getFile();
            File markerFile = transformedArtifacts.get(i++);
            switch (markerFile.getName()) {
                case ORIGINAL_FILE_DOES_NOT_EXIST_MARKER:
                    // skip
                    break;
                case AGENT_INSTRUMENTATION_MARKER_FILE_NAME:
                    // Agent instrumentation always contain 3 entries:
                    // [a marker, a copy of original file or placeholder, a transformed file]
                    File first = transformedArtifacts.get(i++);
                    File second = transformedArtifacts.get(i++);
                    files.addAll(resolveAgentInstrumentationFiles(original, first, second));
                    break;
                case LEGACY_INSTRUMENTATION_MARKER_FILE_NAME:
                    // Legacy instrumentation always contain 2 entries:
                    // [a marker, a transformed file]
                    files.add(transformedArtifacts.get(i++));
                    break;
                default:
                    throw new IllegalStateException("Unexpected marker file: " + markerFile);
            }
        }
        return files;
    }

    private static List<File> resolveAgentInstrumentationFiles(File original, File first, File second) {
        if (second.getName().equals(ORIGINAL_FILE_PLACEHOLDER_SUFFIX)) {
            return Arrays.asList(first, original);
        } else {
            return Arrays.asList(first, second);
        }
    }
}
