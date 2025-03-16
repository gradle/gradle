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

package org.gradle.api.plugins.internal;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.FeatureSpec;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.JavaResolutionConsistency;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.Actions;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec;
import org.gradle.jvm.toolchain.internal.JavaToolchainSpecInternal;
import org.gradle.testing.base.plugins.TestingBasePlugin;
import org.gradle.util.internal.CollectionUtils;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.Collections;
import java.util.regex.Pattern;

import static org.gradle.api.attributes.DocsType.JAVADOC;
import static org.gradle.api.attributes.DocsType.SOURCES;
import static org.gradle.util.internal.ConfigureUtil.configure;

/**
 * Default implementation of {@link JavaPluginExtension}.
 *
 * This extension is used to implicitly configure all JVM-related {@link org.gradle.api.component.Component}s
 * in the project.  Some methods - such as {@link #registerFeature(String, Action)} -
 * are not applicable in this manner and will throw exceptions if used when multiple
 * {@link org.gradle.jvm.component.internal.JvmSoftwareComponentInternal JvmSoftwareComponentInternal}
 * components are present.
 *
 * At present there should only ever be one such component - the {@code java} component added by the {@link org.gradle.api.plugins.JavaPlugin JavaPlugin} - but
 * multiple components may be created by JVM language plugins in the future.
 */
@SuppressWarnings("JavadocReference")
public class DefaultJavaPluginExtension implements JavaPluginExtension {
    private static final Pattern VALID_FEATURE_NAME = Pattern.compile("[a-zA-Z0-9]+");
    private final SourceSetContainer sourceSets;

    private final JavaToolchainSpecInternal toolchainSpec;
    private final ObjectFactory objectFactory;
    private final ModularitySpec modularity;
    private final JavaToolchainSpec toolchain;
    private final ProjectInternal project;

    private final DirectoryProperty docsDir;
    private final DirectoryProperty testResultsDir;
    private final DirectoryProperty testReportDir;
    private JavaVersion srcCompat;
    private JavaVersion targetCompat;
    private boolean autoTargetJvm = true;

    @Inject
    public DefaultJavaPluginExtension(ProjectInternal project, SourceSetContainer sourceSets, DefaultToolchainSpec toolchainSpec) {
        this.docsDir = project.getObjects().directoryProperty();
        this.testResultsDir = project.getObjects().directoryProperty();
        this.testReportDir = project.getObjects().directoryProperty(); //TestingBasePlugin.TESTS_DIR_NAME;
        this.project = project;
        this.sourceSets = sourceSets;
        this.toolchainSpec = toolchainSpec;
        this.objectFactory = project.getObjects();
        this.modularity = objectFactory.newInstance(DefaultModularitySpec.class);
        this.toolchain = toolchainSpec;
        configureDefaults();
    }

    private void configureDefaults() {
        docsDir.convention(project.getLayout().getBuildDirectory().dir("docs"));
        testResultsDir.convention(project.getLayout().getBuildDirectory().dir(TestingBasePlugin.TEST_RESULTS_DIR_NAME));
        testReportDir.convention(project.getExtensions().getByType(ReportingExtension.class).getBaseDirectory().dir(TestingBasePlugin.TESTS_DIR_NAME));
    }

    @Override
    public Object sourceSets(@SuppressWarnings("rawtypes") Closure closure) {
        return sourceSets.configure(closure);
    }

    @Override
    public DirectoryProperty getDocsDir() {
        return docsDir;
    }

    @Override
    public DirectoryProperty getTestResultsDir() {
        return testResultsDir;
    }

    @Override
    public DirectoryProperty getTestReportDir() {
        return testReportDir;
    }

    @Override
    public JavaVersion getSourceCompatibility() {
        if (srcCompat != null) {
            return srcCompat;
        } else if (toolchainSpec != null && toolchainSpec.isConfigured()) {
            return JavaVersion.toVersion(toolchainSpec.getLanguageVersion().get().toString());
        } else {
            return JavaVersion.current();
        }
    }

    public JavaVersion getRawSourceCompatibility() {
        return srcCompat;
    }

    @Override
    public void setSourceCompatibility(Object value) {
        setSourceCompatibility(JavaVersion.toVersion(value));
    }

    @Override
    public void setSourceCompatibility(JavaVersion value) {
        srcCompat = value;
    }

    @Override
    public JavaVersion getTargetCompatibility() {
        return targetCompat != null ? targetCompat : getSourceCompatibility();
    }

    public JavaVersion getRawTargetCompatibility() {
        return targetCompat;
    }

    @Override
    public void setTargetCompatibility(Object value) {
        setTargetCompatibility(JavaVersion.toVersion(value));
    }

    @Override
    public void setTargetCompatibility(JavaVersion value) {
        targetCompat = value;
    }

    @Override
    public Manifest manifest() {
        return manifest(Actions.doNothing());
    }

    @Override
    public Manifest manifest(@SuppressWarnings("rawtypes") Closure closure) {
        return configure(closure, createManifest());
    }

    @Override
    public Manifest manifest(Action<? super Manifest> action) {
        Manifest manifest = createManifest();
        action.execute(manifest);
        return manifest;
    }

    private Manifest createManifest() {
        return new DefaultManifest(project.getFileResolver());
    }

    @Override
    public SourceSetContainer getSourceSets() {
        return sourceSets;
    }

    @Override
    public void disableAutoTargetJvm() {
        this.autoTargetJvm = false;
    }

    @Override
    public boolean getAutoTargetJvmDisabled() {
        return !autoTargetJvm;
    }

    /**
     * @implNote throws an exception if used when multiple {@link JvmSoftwareComponentInternal} components are present.
     */
    @Override
    public void registerFeature(String name, Action<? super FeatureSpec> configureAction) {
        DefaultJavaFeatureSpec spec = new DefaultJavaFeatureSpec(validateFeatureName(name), project);
        configureAction.execute(spec);
        JvmFeatureInternal feature = spec.create();

        JvmSoftwareComponentInternal component = getSingleJavaComponent();
        if (component != null) {
            component.getFeatures().add(feature);

            // TODO: Much of the logic below should become automatic.
            // The component should be aware of all variants in its features and should advertise them
            // without needing to explicitly know about each variant.

            AdhocComponentWithVariants adhocComponent = (AdhocComponentWithVariants) component;
            Configuration javadocElements = feature.getJavadocElementsConfiguration();
            if (javadocElements != null) {
                adhocComponent.addVariantsFromConfiguration(javadocElements, new JavaConfigurationVariantMapping("runtime", true));
            }

            Configuration sourcesElements = feature.getSourcesElementsConfiguration();
            if (sourcesElements != null) {
                adhocComponent.addVariantsFromConfiguration(sourcesElements, new JavaConfigurationVariantMapping("runtime", true));
            }

            if (spec.isPublished()) {
                adhocComponent.addVariantsFromConfiguration(feature.getApiElementsConfiguration(), new JavaConfigurationVariantMapping("compile", true, feature.getCompileClasspathConfiguration()));
                adhocComponent.addVariantsFromConfiguration(feature.getRuntimeElementsConfiguration(), new JavaConfigurationVariantMapping("runtime", true, feature.getRuntimeClasspathConfiguration()));
            }
        }
    }

    @Nullable
    private JvmSoftwareComponentInternal getSingleJavaComponent() {
        NamedDomainObjectSet<JvmSoftwareComponentInternal> jvmComponents = project.getComponents().withType(JvmSoftwareComponentInternal.class);
        if (jvmComponents.size() > 1) {
            String componentNames = CollectionUtils.join(", ", jvmComponents.getNames());
            throw new InvalidUserCodeException("Cannot register feature because multiple JVM components are present. The following components were found: " + componentNames);
        } else if (!jvmComponents.isEmpty()) {
            return jvmComponents.iterator().next();
        }

        // TODO: This case should be deprecated.
        // Users should not be able to create detached features with `registerFeature`
        return null;
    }

    @Override
    public void withJavadocJar() {
        maybeEmitMissingJavaComponentDeprecation("withJavadocJar()");

        if (isJavaComponentPresent(project)) {
            project.getComponents().withType(JvmSoftwareComponentInternal.class).configureEach(JvmSoftwareComponentInternal::withJavadocJar);
        } else {
            SourceSet main = getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            JvmPluginsHelper.createDocumentationVariantWithArtifact(
                main.getJavadocElementsConfigurationName(),
                null,
                JAVADOC,
                Collections.emptySet(),
                main.getJavadocJarTaskName(),
                project.getTasks().named(main.getJavadocTaskName()),
                project
            );
        }
    }

    @Override
    public void withSourcesJar() {
        maybeEmitMissingJavaComponentDeprecation("withSourcesJar()");

        if (isJavaComponentPresent(project)) {
            project.getComponents().withType(JvmSoftwareComponentInternal.class).configureEach(JvmSoftwareComponentInternal::withSourcesJar);
        } else {
            SourceSet main = getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            JvmPluginsHelper.createDocumentationVariantWithArtifact(
                main.getSourcesElementsConfigurationName(),
                null,
                SOURCES,
                Collections.emptySet(),
                main.getSourcesJarTaskName(),
                main.getAllSource(),
                project
            );
        }
    }

    @Override
    public ModularitySpec getModularity() {
        return modularity;
    }

    @Override
    public JavaToolchainSpec getToolchain() {
        return toolchain;
    }

    @Override
    public JavaToolchainSpec toolchain(Action<? super JavaToolchainSpec> action) {
        action.execute(toolchain);
        return toolchain;
    }

    @Override
    public void consistentResolution(Action<? super JavaResolutionConsistency> action) {
        maybeEmitMissingJavaComponentDeprecation("consistentResolution(Action)");

        final SoftwareComponentContainer components = project.getComponents();
        final ConfigurationContainer configurations = project.getConfigurations();
        final SourceSetContainer sourceSets = getSourceSets();
        action.execute(project.getObjects().newInstance(DefaultJavaPluginExtension.DefaultJavaResolutionConsistency.class, components, sourceSets, configurations));
    }

    private static String validateFeatureName(String name) {
        if (!VALID_FEATURE_NAME.matcher(name).matches()) {
            throw new InvalidUserDataException("Invalid feature name '" + name + "'. Must match " + VALID_FEATURE_NAME.pattern());
        }
        return name;
    }

    private static boolean isJavaComponentPresent(ProjectInternal project) {
        return project.getComponents().stream().anyMatch(JvmSoftwareComponentInternal.class::isInstance);
    }

    private void maybeEmitMissingJavaComponentDeprecation(String name) {
        if (!isJavaComponentPresent(project)) {
            DeprecationLogger.deprecateBehaviour(name + " was called without the presence of the java component.")
                .withAdvice("Apply a JVM component plugin such as: java-library, application, groovy, or scala")
                .willBeRemovedInGradle9()
                .withUpgradeGuideSection(8, "java_extension_without_java_component")
                .nagUser();
        }
    }

    public static class DefaultJavaResolutionConsistency implements JavaResolutionConsistency {
        private final SoftwareComponentContainer components;
        private final SourceSetContainer sourceSets;
        private final ConfigurationContainer configurations;
        private final ProjectInternal project;

        @Inject
        public DefaultJavaResolutionConsistency(SoftwareComponentContainer components, SourceSetContainer sourceSets, ConfigurationContainer configurations, ProjectInternal project) {
            this.components = components;
            this.sourceSets = sourceSets;
            this.configurations = configurations;
            this.project = project;
        }

        @Override
        public void useCompileClasspathVersions() {
            sourceSets.configureEach(this::applyCompileClasspathConsistency);
            components.withType(JvmSoftwareComponentInternal.class).configureEach(JvmSoftwareComponentInternal::useCompileClasspathConsistency);

            if (!isJavaComponentPresent(project)) {
                SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                SourceSet testSourceSet = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
                Configuration mainCompileClasspath = findConfiguration(mainSourceSet.getCompileClasspathConfigurationName());
                Configuration testCompileClasspath = findConfiguration(testSourceSet.getCompileClasspathConfigurationName());

                testCompileClasspath.shouldResolveConsistentlyWith(mainCompileClasspath);
            }
        }

        @Override
        public void useRuntimeClasspathVersions() {
            sourceSets.configureEach(this::applyRuntimeClasspathConsistency);
            components.withType(JvmSoftwareComponentInternal.class).configureEach(JvmSoftwareComponentInternal::useRuntimeClasspathConsistency);

            if (!isJavaComponentPresent(project)) {
                SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                SourceSet testSourceSet = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
                Configuration mainRuntimeClasspath = findConfiguration(mainSourceSet.getRuntimeClasspathConfigurationName());
                Configuration testRuntimeClasspath = findConfiguration(testSourceSet.getRuntimeClasspathConfigurationName());

                testRuntimeClasspath.shouldResolveConsistentlyWith(mainRuntimeClasspath);
            }
        }

        private void applyCompileClasspathConsistency(SourceSet sourceSet) {
            Configuration compileClasspath = findConfiguration(sourceSet.getCompileClasspathConfigurationName());
            Configuration runtimeClasspath = findConfiguration(sourceSet.getRuntimeClasspathConfigurationName());
            runtimeClasspath.shouldResolveConsistentlyWith(compileClasspath);
        }

        private void applyRuntimeClasspathConsistency(SourceSet sourceSet) {
            Configuration compileClasspath = findConfiguration(sourceSet.getCompileClasspathConfigurationName());
            Configuration runtimeClasspath = findConfiguration(sourceSet.getRuntimeClasspathConfigurationName());
            compileClasspath.shouldResolveConsistentlyWith(runtimeClasspath);
        }

        private Configuration findConfiguration(String configName) {
            return configurations.getByName(configName);
        }
    }
}
