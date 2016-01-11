/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.jvm.test.internal;

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.internal.Transformers;
import org.gradle.internal.component.local.model.UsageKind;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmComponentSpec;
import org.gradle.jvm.internal.BaseDependencyResolvingClasspath;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.jvm.test.JvmTestSuiteBinarySpec;
import org.gradle.language.base.internal.model.DefaultVariantsMetaData;
import org.gradle.language.base.internal.resolve.LocalComponentResolveContext;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.java.plugins.JavaLanguagePlugin;
import org.gradle.language.java.tasks.PlatformJavaCompile;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.util.CollectionUtils;

import java.util.List;

import static org.gradle.jvm.internal.DefaultJvmBinarySpec.collectDependencies;

public class JvmTestSuiteCompileClasspathConfig implements JavaLanguagePlugin.Java.PlatformJavaCompileConfig {
    private final ServiceRegistry serviceRegistry;
    private final ModelSchemaStore schemaStore;

    public JvmTestSuiteCompileClasspathConfig(ServiceRegistry serviceRegistry, ModelSchemaStore schemaStore) {
        this.serviceRegistry = serviceRegistry;
        this.schemaStore = schemaStore;
    }

    @Override
    public void configureJavaCompile(BinarySpec spec, JavaSourceSet sourceSet, PlatformJavaCompile javaCompile) {
        if (spec instanceof JvmTestSuiteBinarySpec) {
            JvmTestSuiteBinarySpec testSuiteBinary = (JvmTestSuiteBinarySpec) spec;
            JvmBinarySpec testedBinary = testSuiteBinary.getTestedBinary();
            if (testedBinary instanceof JarBinarySpecInternal) {
                FileCollection classpath = javaCompile.getClasspath();
                JvmAssembly assembly = ((WithJvmAssembly) testedBinary).getAssembly();
                ArtifactDependencyResolver dependencyResolver = serviceRegistry.get(ArtifactDependencyResolver.class);
                RepositoryHandler repositories = serviceRegistry.get(RepositoryHandler.class);
                List<ResolutionAwareRepository> resolutionAwareRepositories = CollectionUtils.collect(repositories, Transformers.cast(ResolutionAwareRepository.class));
                JvmComponentSpec testedComponent = JvmTestSuites.getTestedComponent(serviceRegistry, testSuiteBinary.getTestSuite().getTestedComponent());
                BaseDependencyResolvingClasspath transitiveCompileClasspath = new BaseDependencyResolvingClasspath(
                    (BinarySpecInternal) testedBinary,
                    "test suite",
                    dependencyResolver,
                    resolutionAwareRepositories,
                    new LocalComponentResolveContext(((BinarySpecInternal) testedBinary).getId(),
                        DefaultVariantsMetaData.extractFrom(testedBinary, schemaStore),
                        collectDependencies(testedBinary, testedComponent, ((JarBinarySpecInternal)testedBinary).getApiDependencies()),
                        UsageKind.RUNTIME,
                        testedBinary.getDisplayName()
                    ));
                FileCollection fullClasspath = new UnionFileCollection(
                    classpath,
                    transitiveCompileClasspath,
                    new SimpleFileCollection(assembly.getClassDirectories()),
                    new SimpleFileCollection(assembly.getResourceDirectories()));
                javaCompile.setClasspath(fullClasspath);
                javaCompile.dependsOn(((WithJvmAssembly) testedBinary).getAssembly());
            }
        }
    }
}
