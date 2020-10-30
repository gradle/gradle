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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.dsl.DependenciesModelBuilder;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Property;
import org.gradle.initialization.DependenciesAccessors;
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.management.DependenciesModelBuilderInternal;
import org.gradle.internal.management.DependencyResolutionManagementInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.IncubationLogger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.gradle.api.internal.std.SimpleGeneratedJavaClassCompiler.compile;

public class DefaultDependenciesAccessors implements DependenciesAccessors {
    private final static Logger LOGGER = Logging.getLogger(DefaultDependenciesAccessors.class);

    private final static String KEBAB_CASE = "[a-z]([a-z0-9\\-])*";
    private final static Pattern KEBAB_PATTERN = Pattern.compile(KEBAB_CASE);
    private final static String ACCESSORS_PACKAGE = "org.gradle.accessors.dm";
    private final static String ACCESSORS_CLASSNAME_PREFIX = "LibrariesFor";
    private final static String ROOT_PROJECT_ACCESSOR_FQCN = ACCESSORS_PACKAGE + "." + RootProjectAccessorSourceGenerator.ROOT_PROJECT_ACCESSOR_CLASSNAME;

    private final ClassPath classPath;
    private final DependenciesAccessorsWorkspace workspace;
    private final DefaultProjectDependencyFactory projectDependencyFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final FeaturePreviews featurePreviews;
    private final List<AllDependenciesModel> models = Lists.newArrayList();
    private final Map<String, Class<? extends ExternalModuleDependencyFactory>> factories = Maps.newHashMap();

    private ClassLoaderScope classLoaderScope;
    private Class<? extends TypeSafeProjectDependencyFactory> generatedProjectFactory;
    private ClassPath sources = DefaultClassPath.of();
    private ClassPath classes = DefaultClassPath.of();

    public DefaultDependenciesAccessors(ClassPathRegistry registry,
                                        DependenciesAccessorsWorkspace workspace,
                                        DefaultProjectDependencyFactory projectDependencyFactory,
                                        BuildOperationExecutor buildOperationExecutor,
                                        FeaturePreviews featurePreview) {
        this.classPath = registry.getClassPath("DEPENDENCIES-EXTENSION-COMPILER");
        this.workspace = workspace;
        this.projectDependencyFactory = projectDependencyFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.featurePreviews = featurePreview;
    }

    @Override
    public void generateAccessors(List<DependenciesModelBuilder> builders, ClassLoaderScope classLoaderScope, Settings settings) {
        try {
            this.classLoaderScope = classLoaderScope;
            this.models.clear(); // this is used in tests only, shouldn't happen in real context
            for (DependenciesModelBuilder builder : builders) {
                AllDependenciesModel model = ((DependenciesModelBuilderInternal) builder).build();
                models.add(model);
            }
            if (models.stream().anyMatch(AllDependenciesModel::isNotEmpty)) {
                IncubationLogger.incubatingFeatureUsed("Type-safe dependency accessors");
                for (AllDependenciesModel model : models) {
                    if (model.isNotEmpty()) {
                        writeDependenciesAccessors(model);
                    }
                }
            }
            if (featurePreviews.isFeatureEnabled(FeaturePreviews.Feature.TYPESAFE_PROJECT_ACCESSORS)) {
                IncubationLogger.incubatingFeatureUsed("Type-safe project accessors");
                writeProjectAccessors(((SettingsInternal) settings).getProjectRegistry());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeDependenciesAccessors(AllDependenciesModel model) {
        Hasher hash = Hashing.sha1().newHasher();
        hash.putString(model.getName());
        List<String> dependencyAliases = model.getDependencyAliases();
        List<String> bundles = model.getBundleAliases();
        List<String> versions = model.getVersionAliases();
        dependencyAliases.forEach(hash::putString);
        bundles.forEach(hash::putString);
        versions.forEach(hash::putString);
        String keysHash = hash.hash().toString();
        workspace.withWorkspace(keysHash, (workspace, executionHistoryStore) -> {
            File srcDir = new File(workspace, "sources");
            File dstDir = new File(workspace, "classes");
            if (!srcDir.exists() || !dstDir.exists()) {
                buildOperationExecutor.run(new DependencyAccessorCompilationOperation(model, srcDir, dstDir, classPath));
            }
            ClassPath generatedClasses = DefaultClassPath.of(dstDir);
            sources = sources.plus(DefaultClassPath.of(srcDir));
            classes = classes.plus(generatedClasses);
            classLoaderScope.export(generatedClasses);
            return null;
        });
    }

    private void writeProjectAccessors(ProjectRegistry<? extends ProjectDescriptor> projectRegistry) {
        if (!assertCanGenerateAccessors(projectRegistry)) {
            return;
        }
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
                List<ClassSource> sources = Lists.newArrayList();
                sources.add(new RootProjectAccessorSource(projectRegistry.getRootProject()));
                for (ProjectDescriptor project : projectRegistry.getAllProjects()) {
                    sources.add(new ProjectAccessorClassSource(project));
                }
                compile(srcDir, dstDir, sources, classPath);
            }
            ClassPath exported = DefaultClassPath.of(dstDir);
            classLoaderScope.export(exported);
            sources = sources.plus(DefaultClassPath.of(srcDir));
            classes = classes.plus(exported);
            return null;
        });
    }

    private static boolean assertCanGenerateAccessors(ProjectRegistry<? extends ProjectDescriptor> projectRegistry) {
        List<String> errors = Lists.newArrayList();
        projectRegistry.getAllProjects()
            .stream()
            .map(ProjectDescriptor::getName)
            .filter(p -> !KEBAB_PATTERN.matcher(p).matches())
            .map(name -> "project '" + name + "' doesn't follow the kebab case naming convention: " + KEBAB_CASE)
            .forEach(errors::add);
        for (ProjectDescriptor project : projectRegistry.getAllProjects()) {
            project.getChildren()
                .stream()
                .map(ProjectDescriptor::getName)
                .collect(Collectors.groupingBy(AbstractSourceGenerator::toJavaName))
                .entrySet()
                .stream()
                .filter(e -> e.getValue().size() > 1)
                .forEachOrdered(e -> {
                    String javaName = e.getKey();
                    List<String> names = e.getValue();
                    errors.add("subprojects " + names + " of project " + project.getPath() + " map to the same method name get" + javaName + "()");
                });
        }
        if (!errors.isEmpty()) {
            for (String error : errors) {
                LOGGER.warn("Cannot generate project dependency accessors because " + error);
            }
        }
        return errors.isEmpty();
    }

    @Nullable
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
    public void createExtensions(ProjectInternal project) {
        ExtensionContainer container = project.getExtensions();
        try {
            if (!models.isEmpty()) {
                for (AllDependenciesModel model : models) {
                    if (model.isNotEmpty()) {
                        Class<? extends ExternalModuleDependencyFactory> factory;
                        synchronized (this) {
                            factory = factories.computeIfAbsent(model.getName(), n -> loadFactory(classLoaderScope, ACCESSORS_PACKAGE + "." + ACCESSORS_CLASSNAME_PREFIX + StringUtils.capitalize(n)));
                        }
                        if (factory != null) {
                            container.create(model.getName(), factory, model);
                        }
                    }
                }
            }
        } finally {
            if (featurePreviews.isFeatureEnabled(FeaturePreviews.Feature.TYPESAFE_PROJECT_ACCESSORS)) {
                ServiceRegistry services = project.getServices();
                DependencyResolutionManagementInternal drm = services.get(DependencyResolutionManagementInternal.class);
                ProjectFinder projectFinder = services.get(ProjectFinder.class);
                createProjectsExtension(container, drm, projectFinder);
            }
        }
    }

    private void createProjectsExtension(ExtensionContainer container, DependencyResolutionManagementInternal drm, ProjectFinder projectFinder) {
        if (generatedProjectFactory == null) {
            synchronized (this) {
                generatedProjectFactory = loadFactory(classLoaderScope, ROOT_PROJECT_ACCESSOR_FQCN);
            }
        }
        if (generatedProjectFactory != null) {
            Property<String> defaultProjectsExtensionName = drm.getDefaultProjectsExtensionName();
            defaultProjectsExtensionName.finalizeValue();
            container.create(defaultProjectsExtensionName.get(), generatedProjectFactory, projectDependencyFactory, projectFinder);
        }
    }

    @Override
    public ClassPath getSources() {
        return sources;
    }

    @Override
    public ClassPath getClasses() {
        return classes;
    }

    private static class DependencyAccessorCompilationOperation implements RunnableBuildOperation {

        private final AllDependenciesModel model;
        private final File srcDir;
        private final File dstDir;
        private final ClassPath classPath;

        private DependencyAccessorCompilationOperation(AllDependenciesModel model, File srcDir, File dstDir, ClassPath classPath) {
            this.model = model;
            this.srcDir = srcDir;
            this.dstDir = dstDir;
            this.classPath = classPath;
        }

        @Override
        public void run(BuildOperationContext context) {
            List<ClassSource> sources = Collections.singletonList(new DependenciesAccessorClassSource(model.getName(), model));
            compile(srcDir, dstDir, sources, classPath);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Generate dependency accessors for " + model.getName());
        }
    }

    private static class DependenciesAccessorClassSource implements ClassSource {

        private final String name;
        private final AllDependenciesModel model;

        private DependenciesAccessorClassSource(String name, AllDependenciesModel model) {
            this.name = name;
            this.model = model;
        }

        @Override
        public String getPackageName() {
            return ACCESSORS_PACKAGE;
        }

        @Override
        public String getSimpleClassName() {
            return ACCESSORS_CLASSNAME_PREFIX + StringUtils.capitalize(name);
        }

        @Override
        public String getSource() {
            StringWriter writer = new StringWriter();
            DependenciesSourceGenerator.generateSource(writer, model, ACCESSORS_PACKAGE, getSimpleClassName());
            return writer.toString();
        }
    }

    private static class ProjectAccessorClassSource implements ClassSource {
        private final ProjectDescriptor project;
        private String className;
        private String source;

        private ProjectAccessorClassSource(ProjectDescriptor project) {
            this.project = project;
        }

        @Override
        public String getPackageName() {
            return ACCESSORS_PACKAGE;
        }

        @Override
        public String getSimpleClassName() {
            ensureInitialized();
            return className;
        }

        @Override
        public String getSource() {
            ensureInitialized();
            return source;
        }

        private void ensureInitialized() {
            if (className == null) {
                StringWriter writer = new StringWriter();
                className = ProjectAccessorsSourceGenerator.generateSource(writer, project, ACCESSORS_PACKAGE);
                source = writer.toString();
            }
        }
    }

    private static class RootProjectAccessorSource implements ClassSource {
        private final ProjectDescriptor rootProject;

        private RootProjectAccessorSource(ProjectDescriptor rootProject) {
            this.rootProject = rootProject;
        }

        @Override
        public String getPackageName() {
            return ACCESSORS_PACKAGE;
        }

        @Override
        public String getSimpleClassName() {
            return RootProjectAccessorSourceGenerator.ROOT_PROJECT_ACCESSOR_CLASSNAME;
        }

        @Override
        public String getSource() {
            StringWriter writer = new StringWriter();
            RootProjectAccessorSourceGenerator.generateSource(writer, rootProject, ACCESSORS_PACKAGE);
            return writer.toString();
        }

    }
}
