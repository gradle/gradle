/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import groovy.util.Node;
import org.gradle.api.Action;
import org.gradle.api.GradleScriptException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.api.plugins.scala.ScalaPluginExtension;
import org.gradle.api.tasks.ScalaRuntime;
import org.gradle.internal.Cast;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.FilePath;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.ModuleLibrary;
import org.gradle.plugins.ide.idea.model.ProjectLibrary;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject.findOrCreateFirstChildNamed;
import static org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject.findOrCreateFirstChildWithAttributeValue;

public class IdeaScalaConfigurer {

    // More information: http://blog.jetbrains.com/scala/2014/10/30/scala-plugin-update-for-intellij-idea-14-rc-is-out/
    private static final VersionNumber IDEA_VERSION_WHEN_SCALA_SDK_WAS_INTRODUCED = VersionNumber.version(14);
    public static final String DEFAULT_SCALA_PLATFORM_VERSION = "2.10.7";
    private final Project rootProject;
    private final Consumer<Collection<Project>> onScalaProjects;

    public IdeaScalaConfigurer(Project rootProject, Consumer<Collection<Project>> onScalaProjects) {
        this.rootProject = rootProject;
        this.onScalaProjects = onScalaProjects;
    }

    public void configure() {
        rootProject.getGradle().projectsEvaluated(new Action<Gradle>() {
            @Override
            public void execute(Gradle gradle) {
                VersionNumber ideaTargetVersion = findIdeaTargetVersion();
                final boolean useScalaSdk = ideaTargetVersion == null || IDEA_VERSION_WHEN_SCALA_SDK_WAS_INTRODUCED.compareTo(ideaTargetVersion) <= 0;
                final Collection<Project> scalaProjects = findProjectsApplyingIdeaAndScalaPlugins();

                onScalaProjects.accept(scalaProjects);

                final Map<String, ProjectLibrary> scalaCompilerLibraries = new LinkedHashMap<>();
                rootProject.getTasks().named("ideaProject", new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        task.doFirst(new Action<Task>() {
                            @Override
                            public void execute(Task task) {
                                if (scalaProjects.size() > 0) {
                                    scalaCompilerLibraries.clear();
                                    scalaCompilerLibraries.putAll(resolveScalaCompilerLibraries(scalaProjects, useScalaSdk));
                                    declareUniqueProjectLibraries(Sets.newLinkedHashSet(scalaCompilerLibraries.values()));
                                }
                            }
                        });
                    }
                });
                rootProject.configure(scalaProjects, new Action<Project>() {
                    @Override
                    public void execute(final Project project) {
                        project.getExtensions().getByType(IdeaModel.class).getModule().getIml().withXml(new Action<XmlProvider>() {
                            @Override
                            public void execute(XmlProvider xmlProvider) {
                                if (useScalaSdk) {
                                    declareScalaSdk(scalaCompilerLibraries.get(project.getPath()), xmlProvider.asNode());
                                } else {
                                    declareScalaFacet(scalaCompilerLibraries.get(project.getPath()), xmlProvider.asNode());
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private static Map<String, ProjectLibrary> resolveScalaCompilerLibraries(Collection<Project> scalaProjects, final boolean useScalaSdk) {
        Map<String, ProjectLibrary> scalaCompilerLibraries = new LinkedHashMap<>();
        for (final Project scalaProject : scalaProjects) {
            ProjectState owner = ((ProjectInternal) scalaProject).getOwner();
            ProjectLibrary library = owner.fromMutableState(p -> createScalaSdkLibrary(p, useScalaSdk));
            ProjectLibrary duplicate = Iterables.find(scalaCompilerLibraries.values(), Predicates.equalTo(library), null);
            scalaCompilerLibraries.put(owner.getIdentity().getProjectPath().toString(), duplicate == null ? library : duplicate);
        }
        return scalaCompilerLibraries;
    }

    private static Iterable<File> getIdeaModuleLibraryDependenciesAsFiles(IdeaModule ideaModule) {
        // could make resolveDependencies() cache its result for later use by GenerateIdeaModule
        Set<Dependency> dependencies = ideaModule.resolveDependencies();
        List<File> files = new ArrayList<>();
        for (ModuleLibrary moduleLibrary : Iterables.filter(dependencies, ModuleLibrary.class)) {
            for (FilePath filePath : Iterables.filter(moduleLibrary.getClasses(), FilePath.class)) {
                files.add(filePath.getFile());
            }
        }
        return files;
    }

    private static ProjectLibrary createScalaSdkLibrary(Project scalaProject, boolean useScalaSdk) {
        ScalaPluginExtension scalaPluginExtension = scalaProject.getExtensions().getByType(ScalaPluginExtension.class);
        if (scalaPluginExtension.getScalaVersion().isPresent()) {
            String scalaVersion = scalaPluginExtension.getScalaVersion().get();
            Configuration toolchainClasspath = scalaProject.getConfigurations().getByName("scalaToolchainRuntimeClasspath");
            return createScalaSdkFromScalaVersion(scalaVersion, toolchainClasspath.getIncoming().getFiles(), useScalaSdk);
        }

        // Otherwise, use legacy logic to scan classpath jars for version.
        IdeaModule ideaModule = scalaProject.getExtensions().getByType(IdeaModel.class).getModule();
        Iterable<File> files = getIdeaModuleLibraryDependenciesAsFiles(ideaModule);

        ScalaRuntime runtime = scalaProject.getExtensions().getByType(ScalaRuntime.class);
        FileCollection scalaClasspath = runtime.inferScalaClasspath(files);

        File compilerJar = runtime.findScalaJar(scalaClasspath, "compiler");
        if (compilerJar == null) {
            compilerJar = runtime.findScalaJar(scalaClasspath, "compiler_3");
        }

        String scalaVersion = compilerJar != null ? runtime.getScalaVersion(compilerJar) : DEFAULT_SCALA_PLATFORM_VERSION;
        return createScalaSdkFromScalaVersion(scalaVersion, scalaClasspath, useScalaSdk);
    }

    private static ProjectLibrary createScalaSdkFromScalaVersion(String version, FileCollection scalaClasspath, boolean useScalaSdk) {
        if (useScalaSdk) {
            return createScalaSdkLibrary("scala-sdk-" + version, scalaClasspath);
        }
        return createProjectLibrary("scala-compiler-" + version, scalaClasspath);
    }

    private void declareUniqueProjectLibraries(Set<ProjectLibrary> projectLibraries) {
        Set<ProjectLibrary> existingLibraries = rootProject.getExtensions().getByType(IdeaModel.class).getProject().getProjectLibraries();
        Set<ProjectLibrary> newLibraries = Sets.difference(projectLibraries, existingLibraries);
        for (ProjectLibrary newLibrary : newLibraries) {
            String originalName = newLibrary.getName();
            int suffix = 1;
            while (containsLibraryWithSameName(existingLibraries, newLibrary.getName())) {
                newLibrary.setName(originalName + "-" + suffix++);
            }
            existingLibraries.add(newLibrary);
        }
    }

    private static boolean containsLibraryWithSameName(Set<ProjectLibrary> libraries, final String name) {
        return libraries.stream().anyMatch(library -> Objects.equal(library.getName(), name));
    }

    private static void declareScalaSdk(ProjectLibrary scalaSdkLibrary, Node iml) {
        // only define a Scala SDK for a module if we could create a scalaSdkLibrary
        if (scalaSdkLibrary != null) {
            Node newModuleRootManager = findOrCreateFirstChildWithAttributeValue(iml, "component", "name", "NewModuleRootManager");

            Node sdkLibrary = findOrCreateFirstChildWithAttributeValue(newModuleRootManager, "orderEntry", "name", scalaSdkLibrary.getName());
            setNodeAttribute(sdkLibrary, "type", "library");
            setNodeAttribute(sdkLibrary, "level", "project");
        }
    }

    private static void declareScalaFacet(ProjectLibrary scalaCompilerLibrary, Node iml) {
        Node facetManager = findOrCreateFirstChildWithAttributeValue(iml, "component", "name", "FacetManager");

        Node scalaFacet = findOrCreateFirstChildWithAttributeValue(facetManager, "facet", "type", "scala");
        setNodeAttribute(scalaFacet, "name", "Scala");


        Node configuration = findOrCreateFirstChildNamed(scalaFacet, "configuration");

        Node libraryLevel = findOrCreateFirstChildWithAttributeValue(configuration, "option", "name", "compilerLibraryLevel");
        setNodeAttribute(libraryLevel, "value", "Project");

        Node libraryName = findOrCreateFirstChildWithAttributeValue(configuration, "option", "name", "compilerLibraryName");
        setNodeAttribute(libraryName, "value", scalaCompilerLibrary.getName());
    }

    private static void setNodeAttribute(Node node, String key, String value) {
        final Map<String, String> attributes = Cast.uncheckedCast(node.attributes());
        attributes.put(key, value);
    }

    private Collection<Project> findProjectsApplyingIdeaAndScalaPlugins() {
        return Collections2.filter(rootProject.getAllprojects(), new Predicate<Project>() {
            @Override
            public boolean apply(Project project) {
                final boolean hasIdeaPlugin = IdeaIsolatedProjectsWorkarounds.hasPlugin(project, IdeaPlugin.class);
                final boolean hasScalaPlugin = IdeaIsolatedProjectsWorkarounds.hasPlugin(project, ScalaBasePlugin.class);
                return hasIdeaPlugin && hasScalaPlugin;
            }
        });
    }

    private VersionNumber findIdeaTargetVersion() {
        VersionNumber targetVersion = null;
        String targetVersionString = rootProject.getExtensions().getByType(IdeaModel.class).getTargetVersion();
        if (targetVersionString != null) {
            targetVersion = VersionNumber.parse(targetVersionString);
            if (targetVersion.equals(VersionNumber.UNKNOWN)) {
                throw new GradleScriptException("String '" + targetVersionString + "' is not a valid value for IdeaModel.targetVersion.", null);
            }
        }
        return targetVersion;
    }

    private static ProjectLibrary createProjectLibrary(String name, Iterable<File> jars) {
        ProjectLibrary projectLibrary = new ProjectLibrary();
        projectLibrary.setName(name);
        projectLibrary.setClasses(Sets.newLinkedHashSet(jars));
        return projectLibrary;
    }

    private static ProjectLibrary createScalaSdkLibrary(String name, Iterable<File> jars) {
        ProjectLibrary projectLibrary = new ProjectLibrary();
        projectLibrary.setName(name);
        projectLibrary.setType("Scala");
        projectLibrary.setCompilerClasspath(Sets.newLinkedHashSet(jars));
        return projectLibrary;
    }
}
