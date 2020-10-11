/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.std;

import com.google.common.collect.Maps;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.dsl.DependenciesModelBuilder;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.initialization.DependenciesAccessors;
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.management.DependenciesModelBuilderInternal;
import org.gradle.internal.management.DependencyResolutionManagementInternal;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DefaultDependenciesAccessors implements DependenciesAccessors {
    private final static String ACCESSORS_PACKAGE = "org.gradle.accessors.dm";
    private final static String ACCESSORS_CLASSNAME = "Libraries";

    private final ClassPath classPath;
    private final DependenciesAccessorsWorkspace workspace;
    private final DefaultProjectDependencyFactory projectDependencyFactory;

    private AllDependenciesModel dependenciesConfiguration;
    private ClassLoaderScope classLoaderScope;
    private Class<? extends ExternalModuleDependencyFactory> generatedDependenciesFactory;
    private Class<? extends TypeSafeProjectDependencyFactory> generatedProjectFactory;
    private ClassPath sources = DefaultClassPath.of();
    private ClassPath classes = DefaultClassPath.of();

    public DefaultDependenciesAccessors(ClassPathRegistry registry,
                                        DependenciesAccessorsWorkspace workspace,
                                        DefaultProjectDependencyFactory projectDependencyFactory) {
        this.classPath = registry.getClassPath("DEPENDENCIES-EXTENSION-COMPILER");
        this.workspace = workspace;
        this.projectDependencyFactory = projectDependencyFactory;
    }

    @Override
    public void generateAccessors(DependenciesModelBuilder builder, ClassLoaderScope classLoaderScope, Settings settings) {
        try {
            this.dependenciesConfiguration = ((DependenciesModelBuilderInternal) builder).build();
            this.classLoaderScope = classLoaderScope;
            Hasher hash = Hashing.sha1().newHasher();
            List<String> dependencyAliases = dependenciesConfiguration.getDependencyAliases();
            List<String> bundles = dependenciesConfiguration.getBundleAliases();
            dependencyAliases.forEach(hash::putString);
            bundles.forEach(hash::putString);
            String keysHash = hash.hash().toString();
            workspace.withWorkspace(keysHash, (workspace, executionHistoryStore) -> {
                File srcDir = new File(workspace, "sources");
                File dstDir = new File(workspace, "classes");
                if (!srcDir.exists() || !dstDir.exists()) {
                    StringWriter writer = new StringWriter();
                    DependenciesSourceGenerator.generateSource(writer, dependenciesConfiguration, ACCESSORS_PACKAGE, ACCESSORS_CLASSNAME);
                    DependencyManagementAccessorsCompiler.compile(srcDir, dstDir, ACCESSORS_PACKAGE, Collections.singletonMap(ACCESSORS_CLASSNAME, writer.toString()), classPath);
                }
                sources = DefaultClassPath.of(srcDir);
                classes = DefaultClassPath.of(dstDir);
                classLoaderScope.export(DefaultClassPath.of(dstDir));
                return null;
            });
            writeProjectAccessors(((SettingsInternal)settings).getProjectRegistry());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeProjectAccessors(ProjectRegistry<? extends ProjectDescriptor> projectRegistry) {
        Hasher hash = Hashing.sha1().newHasher();
        projectRegistry.getAllProjects().stream()
            .map(ProjectDescriptor::getPath)
            .sorted()
            .forEachOrdered(hash::putString);
        String projectsHash = hash.hash().toString();
        workspace.withWorkspace(projectsHash, (workspace, executionHistoryStore) -> {
            File srcDir = new File(workspace, "sources");
            File dstDir = new File(workspace, "classes");
            if (!srcDir.exists() || !dstDir.exists()) {
                Map<String, String> classNameToSources = Maps.newLinkedHashMap();
                StringWriter writer = new StringWriter();
                RootProjectAccessorSourceGenerator.generateSource(writer, projectRegistry.getRootProject(), ACCESSORS_PACKAGE);
                classNameToSources.put(RootProjectAccessorSourceGenerator.ROOT_PROJECT_ACCESSOR_CLASSNAME, writer.toString());
                for (ProjectDescriptor project : projectRegistry.getAllProjects()) {
                    writer = new StringWriter();
                    classNameToSources.put(ProjectAccessorsSourceGenerator.generateSource(writer, project, ACCESSORS_PACKAGE), writer.toString());
                }
                DependencyManagementAccessorsCompiler.compile(srcDir, dstDir, ACCESSORS_PACKAGE, classNameToSources, classPath);
            }
            ClassPath exported = DefaultClassPath.of(dstDir);
            classLoaderScope.export(exported);
            sources = sources.plus(DefaultClassPath.of(srcDir));
            classes = classes.plus(exported);
            return null;
        });
    }

    private <T> Class<? extends T> loadFactory(ClassLoaderScope classLoaderScope, String className) {
        Class<? extends T> clazz;
        try {
            clazz = Cast.uncheckedCast(classLoaderScope.getExportClassLoader().loadClass(className));
        } catch (ClassNotFoundException e) {
            return null;
        }
        return clazz;
    }

    @Override
    public void createExtension(ProjectInternal project) {
        if (dependenciesConfiguration != null) {
            ExtensionContainer container = project.getExtensions();
            ServiceRegistry services = project.getServices();
            DependencyResolutionManagementInternal drm = services.get(DependencyResolutionManagementInternal.class);
            ProjectFinder projectFinder = services.get(ProjectFinder.class);
            if (generatedDependenciesFactory == null) {
                synchronized (this) {
                    generatedDependenciesFactory = loadFactory(classLoaderScope, ACCESSORS_PACKAGE + "." + ACCESSORS_CLASSNAME);
                }
            }
            container.create(drm.getLibrariesExtensionName(), generatedDependenciesFactory, dependenciesConfiguration);
            if (generatedProjectFactory == null) {
                synchronized (this) {
                    generatedProjectFactory = loadFactory(classLoaderScope, ACCESSORS_PACKAGE + "." + RootProjectAccessorSourceGenerator.ROOT_PROJECT_ACCESSOR_CLASSNAME);
                }
            }
            container.create(drm.getProjectsExtensionName(), generatedProjectFactory, projectDependencyFactory, projectFinder);
        }
    }

    @Override
    public ClassPath getSources() {
        return classes;
    }

    @Override
    public ClassPath getClasses() {
        return sources;
    }
}
