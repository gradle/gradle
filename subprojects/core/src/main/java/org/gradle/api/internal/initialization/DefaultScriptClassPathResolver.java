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

import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;
import org.gradle.internal.logging.util.Log4jBannedVersion;
import org.gradle.util.GradleVersion;

import java.util.List;

public class DefaultScriptClassPathResolver implements ScriptClassPathResolver {
    private final List<ScriptClassPathInitializer> initializers;
    private final NamedObjectInstantiator instantiator;
    private final CachedClasspathTransformer classpathTransformer;

    public DefaultScriptClassPathResolver(List<ScriptClassPathInitializer> initializers, NamedObjectInstantiator instantiator, CachedClasspathTransformer classpathTransformer) {
        this.initializers = initializers;
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
    }

    @Override
    public ClassPath resolveClassPath(Configuration classpathConfiguration) {
        if (classpathConfiguration == null) {
            return ClassPath.EMPTY;
        }
        for (ScriptClassPathInitializer initializer : initializers) {
            initializer.execute(classpathConfiguration);
        }
        ArtifactView view = classpathConfiguration.getIncoming().artifactView(config -> {
            config.componentFilter(componentId -> {
                if (componentId instanceof OpaqueComponentIdentifier) {
                    DependencyFactoryInternal.ClassPathNotation classPathNotation = ((OpaqueComponentIdentifier) componentId).getClassPathNotation();
                    return classPathNotation != DependencyFactoryInternal.ClassPathNotation.GRADLE_API && classPathNotation != DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY;
                }
                return true;
            });
        });
        return classpathTransformer.transform(DefaultClassPath.of(view.getFiles()), CachedClasspathTransformer.StandardTransform.BuildLogic);
    }
}
