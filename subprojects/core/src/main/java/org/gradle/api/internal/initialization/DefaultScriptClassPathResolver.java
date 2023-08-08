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
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
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

public class DefaultScriptClassPathResolver implements ScriptClassPathResolver {

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
    public ClassPath resolveClassPath(Configuration classpathConfiguration, DependencyHandler dependencyHandler) {
        ArtifactView instrumentedView = getInstrumentedView(classpathConfiguration, dependencyHandler);
        return TransformedClassPath.handleInstrumentingArtifactTransform(DefaultClassPath.of(instrumentedView.getFiles()));
    }

    private static boolean removeGradleApi(ComponentIdentifier componentId) {
        if (componentId instanceof OpaqueComponentIdentifier) {
            DependencyFactoryInternal.ClassPathNotation classPathNotation = ((OpaqueComponentIdentifier) componentId).getClassPathNotation();
            return classPathNotation != DependencyFactoryInternal.ClassPathNotation.GRADLE_API
                && classPathNotation != DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY;
        }
        return true;
    }

    private static ArtifactView getInstrumentedView(Configuration classpathConfiguration, DependencyHandler dependencyHandler) {
        dependencyHandler.registerTransform(
            CollectDirectClassSuperTypesTransform.class,
            spec -> {
                spec.getFrom().attribute(INSTRUMENTED_ATTRIBUTE, false);
                spec.getTo().attribute(INSTRUMENTED_ATTRIBUTE, true);
            }
        );

        ArtifactView hierarchyCollectedView = artifactView(classpathConfiguration, config -> config.attributes(it -> it.attribute(HIERARCHY_COLLECTED_ATTRIBUTE, true)));
        dependencyHandler.registerTransform(
            CollectDirectClassSuperTypesTransform.class,
            spec -> {
                spec.getFrom().attribute(HIERARCHY_COLLECTED_ATTRIBUTE, false);
                spec.getTo().attribute(HIERARCHY_COLLECTED_ATTRIBUTE, true);
            }
        );

        dependencyHandler.registerTransform(
            InstrumentArtifactTransform.class,
            spec -> {
                spec.getFrom().attribute(INSTRUMENTED_ATTRIBUTE, false);
                spec.getTo().attribute(INSTRUMENTED_ATTRIBUTE, true);
                spec.parameters(parameters -> parameters.getClassHierarchy().setFrom(hierarchyCollectedView.getFiles()));
            }
        );

        return artifactView(classpathConfiguration, config -> config.attributes(attributes -> attributes.attribute(INSTRUMENTED_ATTRIBUTE, true)));
    }

    private static ArtifactView artifactView(Configuration configuration, Action<? super ArtifactView.ViewConfiguration> configAction) {
        return configuration.getIncoming().artifactView(config -> {
            configAction.execute(config);
            config.componentFilter(DefaultScriptClassPathResolver::removeGradleApi);
        });
    }
}
