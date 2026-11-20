/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugins.ide.idea.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.FilePath;
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel;
import org.gradle.plugins.ide.idea.model.ModuleDependency;
import org.gradle.plugins.ide.idea.model.PathFactory;
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary;
import org.gradle.plugins.ide.idea.model.internal.IdeaDependenciesOptimizer;
import org.gradle.plugins.ide.internal.resolver.UnresolvedIdeDependencyHandler;
import org.gradle.plugins.ide.internal.tooling.IdeaModuleBuilderSupport;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaCompilerOutput;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaContentRoot;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaSourceDirectory;
import org.gradle.plugins.ide.internal.tooling.idea.IsolatedIdeaModuleInternal;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes the {@link IsolatedIdeaModuleInternal} model of a project and writes it to a file.
 * <p>
 * Prototype of building IDE sync models with tasks instead of tooling model builders.
 * The inputs are the values of the project's {@code idea.module} and the lazily resolved
 * dependencies of its scopes; the model itself is computed by the task action.
 * When enabled, the {@code IsolatedIdeaModuleInternal} builder reads the model from the
 * output of this task, if the task was requested in the same build, instead of computing it.
 */
@NullMarked
@DisableCachingByDefault(because = "The model contains absolute paths")
public abstract class GenerateIdeaModuleModel extends DefaultTask {

    public static final String TASK_NAME = "ideaModuleModel";

    public static final InternalOption<Boolean> ENABLED = InternalOptions.ofBoolean("org.gradle.internal.ide.idea.module-model-task", false);

    private final List<DependencyScope> dependencyScopes = new ArrayList<>();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Input
    public abstract Property<String> getModuleName();

    @Input
    @Optional
    public abstract Property<String> getJdkName();

    @Input
    public abstract Property<String> getProjectBuildTreePath();

    @Input
    @Optional
    public abstract Property<String> getExplicitSourceLanguageLevel();

    @Input
    @Optional
    public abstract Property<JavaVersion> getExplicitTargetBytecodeVersion();

    @Input
    @Optional
    public abstract Property<JavaVersion> getJavaSourceCompatibility();

    @Input
    @Optional
    public abstract Property<JavaVersion> getJavaTargetCompatibility();

    @Input
    public abstract Property<String> getContentRoot();

    @Input
    public abstract ListProperty<String> getSourceDirs();

    @Input
    public abstract ListProperty<String> getGeneratedSourceDirs();

    @Input
    public abstract ListProperty<String> getTestSourceDirs();

    @Input
    public abstract ListProperty<String> getResourceDirs();

    @Input
    public abstract ListProperty<String> getTestResourceDirs();

    @Input
    public abstract ListProperty<String> getExcludeDirs();

    @Input
    public abstract Property<Boolean> getInheritOutputDirs();

    @Input
    @Optional
    public abstract Property<String> getOutputDir();

    @Input
    @Optional
    public abstract Property<String> getTestOutputDir();

    /**
     * Directories added as libraries to the module, by IDEA scope.
     */
    @Input
    public abstract MapProperty<String, List<String>> getSingleEntryLibraries();

    @Input
    public abstract Property<Boolean> getDownloadSources();

    @Input
    public abstract Property<Boolean> getDownloadJavadoc();

    /**
     * IDEA module names of the projects in the build tree, by build tree path.
     * A project dependency points to the module of the target project, whose name may be deduplicated.
     */
    @Input
    public abstract MapProperty<String, String> getProjectModuleNames();

    /**
     * The resolved dependencies of each IDEA scope.
     */
    @Internal
    public List<DependencyScope> getDependencyScopes() {
        return dependencyScopes;
    }

    /**
     * The files of the resolved external dependencies, so the task reruns when they change.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getDependencyFiles();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /**
     * Adds the dependencies of an IDEA scope, resolved lazily from the given configurations.
     */
    public void dependencyScope(String scope, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        DependencyScope dependencyScope = new DependencyScope(
            scope,
            plusConfigurations.stream().map(this::resolvedConfiguration).collect(Collectors.toList()),
            minusConfigurations.stream().map(this::resolvedConfiguration).collect(Collectors.toList())
        );
        dependencyScopes.add(dependencyScope);
        // Project dependencies only contribute their identity to the model, so their artifacts are not built
        for (Configuration configuration : plusConfigurations) {
            getDependencyFiles().from(configuration.getIncoming().artifactView(view -> {
                view.lenient(true);
                view.componentFilter(id -> !(id instanceof ProjectComponentIdentifier));
            }).getFiles());
        }
    }

    private ResolvedConfiguration resolvedConfiguration(Configuration configuration) {
        return new ResolvedConfiguration(
            configuration.getIncoming().artifactView(view -> view.lenient(true)).getArtifacts(),
            configuration.getIncoming().getResolutionResult().getRootComponent(),
            documentation(configuration, DocsType.SOURCES),
            documentation(configuration, DocsType.JAVADOC)
        );
    }

    private ArtifactCollection documentation(Configuration configuration, String docsType) {
        ObjectFactory objects = getObjectFactory();
        return configuration.getIncoming().artifactView(view -> {
            view.lenient(true);
            view.withVariantReselection();
            view.componentFilter(id -> id instanceof ModuleComponentIdentifier);
            view.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
                attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, docsType));
            });
        }).getArtifacts();
    }

    @TaskAction
    void generate() {
        IsolatedIdeaModuleInternal model = buildModel();
        File outputFile = getOutputFile().get().getAsFile();
        try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile.toPath())))) {
            out.writeObject(model);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private IsolatedIdeaModuleInternal buildModel() {
        IsolatedIdeaModuleInternal model = new IsolatedIdeaModuleInternal();
        model.setName(getModuleName().get());
        model.setJdkName(getJdkName().getOrNull());
        model.setContentRoot(buildContentRoot());
        model.setCompilerOutput(new DefaultIdeaCompilerOutput()
            .setInheritOutputDirs(getInheritOutputDirs().get())
            .setOutputDir(toFile(getOutputDir().getOrNull()))
            .setTestOutputDir(toFile(getTestOutputDir().getOrNull())));
        String explicitSourceLanguageLevel = getExplicitSourceLanguageLevel().getOrNull();
        model.setExplicitSourceLanguageLevel(explicitSourceLanguageLevel == null ? null : new IdeaLanguageLevel(explicitSourceLanguageLevel));
        model.setExplicitTargetBytecodeVersion(getExplicitTargetBytecodeVersion().getOrNull());
        model.setJavaSourceCompatibility(getJavaSourceCompatibility().getOrNull());
        model.setJavaTargetCompatibility(getJavaTargetCompatibility().getOrNull());
        model.setDependencies(IdeaModuleBuilderSupport.buildDependencies(resolveDependencies()));
        return model;
    }

    private DefaultIdeaContentRoot buildContentRoot() {
        Set<File> generatedSourceDirs = toFiles(getGeneratedSourceDirs().get());
        return new DefaultIdeaContentRoot()
            .setRootDirectory(new File(getContentRoot().get()))
            .setSourceDirectories(sourceDirectories(getSourceDirs().get(), generatedSourceDirs))
            .setTestDirectories(sourceDirectories(getTestSourceDirs().get(), generatedSourceDirs))
            .setResourceDirectories(sourceDirectories(getResourceDirs().get(), generatedSourceDirs))
            .setTestResourceDirectories(sourceDirectories(getTestResourceDirs().get(), generatedSourceDirs))
            .setExcludeDirectories(toFiles(getExcludeDirs().get()));
    }

    private static Set<DefaultIdeaSourceDirectory> sourceDirectories(List<String> dirs, Set<File> generatedSourceDirs) {
        Set<DefaultIdeaSourceDirectory> out = new LinkedHashSet<>();
        for (File dir : toFiles(dirs)) {
            out.add(new DefaultIdeaSourceDirectory().setDirectory(dir).setGenerated(generatedSourceDirs.contains(dir)));
        }
        return out;
    }

    // Mirrors IdeaDependenciesProvider, working from the resolved configurations instead of the project
    private Set<Dependency> resolveDependencies() {
        PathFactory pathFactory = new PathFactory();
        Set<Dependency> result = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> libraries : getSingleEntryLibraries().get().entrySet()) {
            for (File file : toFiles(libraries.getValue())) {
                if (file.isDirectory()) {
                    result.add(new SingleEntryModuleLibrary(pathFactory.path(file), libraries.getKey()));
                }
            }
        }

        Set<Dependency> dependencies = new LinkedHashSet<>();
        Map<ComponentSelector, Throwable> unresolvedDependencies = new LinkedHashMap<>();
        for (DependencyScope scope : dependencyScopes) {
            dependencies.addAll(scope.dependencies(pathFactory, unresolvedDependencies));
        }
        new IdeaDependenciesOptimizer().optimizeDeps(dependencies);
        for (Map.Entry<ComponentSelector, Throwable> dep : unresolvedDependencies.entrySet()) {
            UnresolvedIdeDependencyHandler.log(dep.getKey(), dep.getValue());
        }
        result.addAll(dependencies);
        return result;
    }

    private static @Nullable File toFile(@Nullable String path) {
        return path == null ? null : new File(path);
    }

    private static Set<File> toFiles(List<String> paths) {
        return paths.stream().map(File::new).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<FilePath> paths(PathFactory pathFactory, @Nullable Set<File> files) {
        if (files == null) {
            return new LinkedHashSet<>();
        }
        return files.stream().map(pathFactory::path).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static final class ResolvedConfiguration {
        private final ArtifactCollection artifacts;
        private final Provider<ResolvedComponentResult> root;
        private final ArtifactCollection sources;
        private final ArtifactCollection javadoc;

        private ResolvedConfiguration(ArtifactCollection artifacts, Provider<ResolvedComponentResult> root, ArtifactCollection sources, ArtifactCollection javadoc) {
            this.artifacts = artifacts;
            this.root = root;
            this.sources = sources;
            this.javadoc = javadoc;
        }

        private List<UnresolvedDependencyResult> unresolvedDependencies() {
            List<UnresolvedDependencyResult> unresolved = new ArrayList<>();
            for (DependencyResult dependency : root.get().getDependencies()) {
                if (dependency instanceof UnresolvedDependencyResult) {
                    unresolved.add((UnresolvedDependencyResult) dependency);
                }
            }
            return unresolved;
        }
    }

    /**
     * The configurations that make up an IDEA scope.
     */
    public final class DependencyScope {
        private final String scope;
        private final List<ResolvedConfiguration> plus;
        private final List<ResolvedConfiguration> minus;

        private DependencyScope(String scope, List<ResolvedConfiguration> plus, List<ResolvedConfiguration> minus) {
            this.scope = scope;
            this.plus = plus;
            this.minus = minus;
        }

        private Collection<Dependency> dependencies(PathFactory pathFactory, Map<ComponentSelector, Throwable> allUnresolvedDependencies) {
            Map<ComponentArtifactIdentifier, ResolvedArtifactResult> resolvedArtifacts = new LinkedHashMap<>();
            Map<ComponentSelector, UnresolvedDependencyResult> unresolvedDependencies = new LinkedHashMap<>();
            for (ResolvedConfiguration configuration : plus) {
                for (ResolvedArtifactResult artifact : configuration.artifacts) {
                    resolvedArtifacts.put(artifact.getId(), artifact);
                }
                if (!configuration.artifacts.getFailures().isEmpty()) {
                    for (UnresolvedDependencyResult unresolved : configuration.unresolvedDependencies()) {
                        unresolvedDependencies.put(unresolved.getAttempted(), unresolved);
                    }
                }
            }
            for (ResolvedConfiguration configuration : minus) {
                for (ResolvedArtifactResult artifact : configuration.artifacts) {
                    resolvedArtifacts.remove(artifact.getId());
                }
                if (!configuration.artifacts.getFailures().isEmpty()) {
                    for (UnresolvedDependencyResult unresolved : configuration.unresolvedDependencies()) {
                        unresolvedDependencies.remove(unresolved.getAttempted());
                    }
                }
            }
            Map<ComponentIdentifier, Set<File>> sources = getDownloadSources().get() ? documentation(c -> c.sources) : Collections.emptyMap();
            Map<ComponentIdentifier, Set<File>> javadoc = getDownloadJavadoc().get() ? documentation(c -> c.javadoc) : Collections.emptyMap();

            // Projects first, then modules, then files, as IdeaDependenciesProvider does
            List<Dependency> projectDependencies = new ArrayList<>();
            List<Dependency> moduleDependencies = new ArrayList<>();
            List<Dependency> fileDependencies = new ArrayList<>();
            for (ResolvedArtifactResult artifact : resolvedArtifacts.values()) {
                ComponentIdentifier componentId = artifact.getId().getComponentIdentifier();
                if (componentId instanceof ProjectComponentIdentifier) {
                    ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) componentId;
                    if (!projectId.getBuildTreePath().equals(getProjectBuildTreePath().get())) {
                        String moduleName = getProjectModuleNames().get().get(projectId.getBuildTreePath());
                        projectDependencies.add(new ModuleDependency(moduleName != null ? moduleName : projectId.getProjectName(), scope));
                    }
                } else if (componentId instanceof ModuleComponentIdentifier) {
                    ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) componentId;
                    SingleEntryModuleLibrary library = new SingleEntryModuleLibrary(
                        pathFactory.path(artifact.getFile()),
                        paths(pathFactory, javadoc.get(componentId)),
                        paths(pathFactory, sources.get(componentId)),
                        scope
                    );
                    library.setModuleVersion(DefaultModuleVersionIdentifier.newId(moduleId.getModuleIdentifier(), moduleId.getVersion()));
                    moduleDependencies.add(library);
                } else {
                    // Unlike IdeaDependenciesProvider, sources of the local Groovy dependency are not resolved
                    fileDependencies.add(new SingleEntryModuleLibrary(pathFactory.path(artifact.getFile()), scope));
                }
            }
            UnresolvedIdeDependencyHandler unresolvedHandler = new UnresolvedIdeDependencyHandler();
            File contentRoot = new File(getContentRoot().get());
            for (UnresolvedDependencyResult unresolved : unresolvedDependencies.values()) {
                fileDependencies.add(new SingleEntryModuleLibrary(pathFactory.path(unresolvedHandler.asFile(contentRoot, unresolved.getAttempted())), scope));
                allUnresolvedDependencies.put(unresolved.getAttempted(), unresolved.getFailure());
            }

            Collection<Dependency> dependencies = new LinkedHashSet<>();
            dependencies.addAll(projectDependencies);
            dependencies.addAll(moduleDependencies);
            dependencies.addAll(fileDependencies);
            return dependencies;
        }

        private Map<ComponentIdentifier, Set<File>> documentation(Function<ResolvedConfiguration, ArtifactCollection> kind) {
            Map<ComponentIdentifier, Set<File>> files = new LinkedHashMap<>();
            for (ResolvedConfiguration configuration : plus) {
                for (ResolvedArtifactResult artifact : kind.apply(configuration)) {
                    files.computeIfAbsent(artifact.getId().getComponentIdentifier(), id -> new LinkedHashSet<>()).add(artifact.getFile());
                }
            }
            return files;
        }
    }

    public static IsolatedIdeaModuleInternal readModel(File file) {
        ClassLoader classLoader = IsolatedIdeaModuleInternal.class.getClassLoader();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()));
             ObjectInputStream objects = new ObjectInputStream(in) {
                 @Override
                 protected Class<?> resolveClass(ObjectStreamClass desc) throws ClassNotFoundException {
                     return Class.forName(desc.getName(), false, classLoader);
                 }
             }) {
            return (IsolatedIdeaModuleInternal) objects.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
