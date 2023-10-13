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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.initialization.transform.CollectDirectClassSuperTypesTransform;
import org.gradle.api.internal.initialization.transform.InstrumentArtifactTransform;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.classpath.TransformedClassPath;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;
import org.gradle.internal.logging.util.Log4jBannedVersion;
import org.gradle.util.GradleVersion;

import java.util.EnumSet;
import java.util.Set;

public class DefaultScriptClassPathResolver implements ScriptClassPathResolver {

    private static final Set<DependencyFactoryInternal.ClassPathNotation> NO_GRADLE_API = EnumSet.copyOf(ImmutableSet.of(
        DependencyFactoryInternal.ClassPathNotation.GRADLE_API,
        DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY
    ));
    private static final Set<DependencyFactoryInternal.ClassPathNotation> NO_GRADLE_API_AND_PROJECTS = EnumSet.copyOf(ImmutableSet.of(
        DependencyFactoryInternal.ClassPathNotation.GRADLE_API,
        DependencyFactoryInternal.ClassPathNotation.GRADLE_PROJECTS_ON_BUILD_CLASSPATH,
        DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY
    ));

    private static final Attribute<Boolean> HIERARCHY_COLLECTED_ATTRIBUTE = Attribute.of("hierarchy-collected", Boolean.class);
    private static final Attribute<Boolean> INSTRUMENTED_ATTRIBUTE = Attribute.of("instrumented", Boolean.class);
    private final NamedObjectInstantiator instantiator;
    private final CachedClasspathTransformer classpathTransformer;

    public DefaultScriptClassPathResolver(
        NamedObjectInstantiator instantiator,
        CachedClasspathTransformer classpathTransformer
    ) {
        this.instantiator = instantiator;
        this.classpathTransformer = classpathTransformer;
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

        dependencyHandler.getArtifactTypes().getByName("jar").getAttributes()
            .attribute(INSTRUMENTED_ATTRIBUTE, false)
            .attribute(HIERARCHY_COLLECTED_ATTRIBUTE, false);
    }

    @Override
    public ClassPath resolveClassPath(Configuration classpathConfiguration, DependencyHandler dependencyHandler, ConfigurationContainer configContainer) {
        FileCollection instrumentedView = getInstrumentedView(classpathConfiguration, dependencyHandler, configContainer);
        return TransformedClassPath.handleInstrumentingArtifactTransform(DefaultClassPath.of(instrumentedView));
    }

    private static FileCollection getInstrumentedView(Configuration classpathConfiguration, DependencyHandler dependencyHandler, ConfigurationContainer configContainer) {
        // Handle projects as files, so we cache them globally
        ArtifactView projectsView = artifactView(classpathConfiguration, config -> config.componentFilter(id -> id instanceof ProjectComponentIdentifier));
        DefaultSelfResolvingDependency projectDependencies = new DefaultSelfResolvingDependency(
            new OpaqueComponentIdentifier(DependencyFactoryInternal.ClassPathNotation.GRADLE_PROJECTS_ON_BUILD_CLASSPATH),
            (FileCollectionInternal) projectsView.getFiles()
        );
        Configuration projectsOnlyConfiguration = configContainer.detachedConfiguration(projectDependencies);

        // Register collect type hierarchy
        ArtifactView hierarchyCollectedView = artifactView(classpathConfiguration, config -> {
            config.attributes(it -> it.attribute(HIERARCHY_COLLECTED_ATTRIBUTE, true));
            config.componentFilter(id -> DefaultScriptClassPathResolver.filterGradleDependencies(id, NO_GRADLE_API_AND_PROJECTS));
        });
        dependencyHandler.registerTransform(
            CollectDirectClassSuperTypesTransform.class,
            spec -> {
                spec.getFrom().attribute(HIERARCHY_COLLECTED_ATTRIBUTE, false);
                spec.getTo().attribute(HIERARCHY_COLLECTED_ATTRIBUTE, true);
            }
        );

        // Register instrumentation and upgrades transform
        dependencyHandler.registerTransform(
            InstrumentArtifactTransform.class,
            spec -> {
                spec.getFrom().attribute(INSTRUMENTED_ATTRIBUTE, false);
                spec.getTo().attribute(INSTRUMENTED_ATTRIBUTE, true);
                spec.parameters(parameters -> parameters.getClassHierarchy().setFrom(hierarchyCollectedView.getFiles()));
            }
        );

        FileCollection instrumentedClasspath = artifactView(classpathConfiguration, config -> {
            config.attributes(it -> it.attribute(INSTRUMENTED_ATTRIBUTE, true));
            config.componentFilter(id -> DefaultScriptClassPathResolver.filterGradleDependencies(id, NO_GRADLE_API));
        }).getFiles();
        //noinspection CodeBlock2Expr
        FileCollection instrumentedProjectsOnlyClasspath = artifactView(projectsOnlyConfiguration, config -> {
            config.attributes(it -> it.attribute(INSTRUMENTED_ATTRIBUTE, true));
        }).getFiles();
        return instrumentedClasspath.plus(instrumentedProjectsOnlyClasspath);
    }

    private static ArtifactView artifactView(Configuration configuration, Action<? super ArtifactView.ViewConfiguration> configAction) {
        return configuration.getIncoming().artifactView(configAction);
    }

    private static boolean filterGradleDependencies(ComponentIdentifier componentId, Set<DependencyFactoryInternal.ClassPathNotation> ignoredClasspathNotations) {
        if (componentId instanceof OpaqueComponentIdentifier) {
            DependencyFactoryInternal.ClassPathNotation classPathNotation = ((OpaqueComponentIdentifier) componentId).getClassPathNotation();
            return !ignoredClasspathNotations.contains(classPathNotation);
        }
        return !(componentId instanceof ProjectComponentIdentifier);
    }
}
