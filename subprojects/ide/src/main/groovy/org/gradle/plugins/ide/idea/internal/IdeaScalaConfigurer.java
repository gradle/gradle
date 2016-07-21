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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.util.Node;
import org.gradle.BuildAdapter;
import org.gradle.api.Action;
import org.gradle.api.GradleScriptException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.XmlProvider;
import org.gradle.api.file.FileCollection;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.api.tasks.ScalaRuntime;
import org.gradle.language.scala.ScalaPlatform;
import org.gradle.language.scala.internal.DefaultScalaPlatform;
import org.gradle.language.scala.plugins.ScalaLanguagePlugin;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.FilePath;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.ModuleLibrary;
import org.gradle.plugins.ide.idea.model.ProjectLibrary;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject.findOrCreateFirstChildNamed;
import static org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject.findOrCreateFirstChildWithAttributeValue;

public class IdeaScalaConfigurer {

    // More information: http://blog.jetbrains.com/scala/2014/10/30/scala-plugin-update-for-intellij-idea-14-rc-is-out/
    private static final VersionNumber IDEA_VERSION_WHEN_SCALA_SDK_WAS_INTRODUCED = VersionNumber.version(14);
    private final Project rootProject;

    public IdeaScalaConfigurer(Project rootProject) {
        this.rootProject = rootProject;
    }

    public void configure() {
        rootProject.getGradle().addBuildListener(new BuildAdapter() {
            public void projectsEvaluated(Gradle gradle) {
                VersionNumber ideaTargetVersion = findIdeaTargetVersion();
                final boolean useScalaSdk = ideaTargetVersion == null || IDEA_VERSION_WHEN_SCALA_SDK_WAS_INTRODUCED.compareTo(ideaTargetVersion) <= 0;
                final Collection<Project> scalaProjects = findProjectsApplyingIdeaAndScalaPlugins();
                final Map<String, ProjectLibrary> scalaCompilerLibraries = Maps.newHashMap();
                rootProject.getTasks().getByName("ideaProject").doFirst(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        if (scalaProjects.size() > 0) {
                            scalaCompilerLibraries.clear();
                            scalaCompilerLibraries.putAll(resolveScalaCompilerLibraries(scalaProjects, useScalaSdk));
                            declareUniqueProjectLibraries(Sets.newLinkedHashSet(scalaCompilerLibraries.values()));
                        }
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

    private static Map<String, ProjectLibrary> resolveScalaCompilerLibraries(Collection<Project> scalaProjects, boolean useScalaSdk) {
        Map<String, ProjectLibrary> scalaCompilerLibraries = Maps.newHashMap();
        for (Project scalaProject : scalaProjects) {
            IdeaModule ideaModule = scalaProject.getExtensions().getByType(IdeaModel.class).getModule();
            Iterable<File> files = getIdeaModuleLibraryDependenciesAsFiles(ideaModule);
            ProjectLibrary library = createScalaSdkLibrary(scalaProject, files, useScalaSdk, ideaModule);
            if (library != null) {
                ProjectLibrary duplicate = Iterables.find(scalaCompilerLibraries.values(), Predicates.equalTo(library), null);
                scalaCompilerLibraries.put(scalaProject.getPath(), duplicate == null ? library : duplicate);
            }
        }
        return scalaCompilerLibraries;
    }

    private static Iterable<File> getIdeaModuleLibraryDependenciesAsFiles(IdeaModule ideaModule) {
        // could make resolveDependencies() cache its result for later use by GenerateIdeaModule
        Set<Dependency> dependencies = ideaModule.resolveDependencies();
        List<File> files = Lists.newArrayList();
        for(ModuleLibrary moduleLibrary : Iterables.filter(dependencies, ModuleLibrary.class)) {
            for (FilePath filePath : Iterables.filter(moduleLibrary.getClasses(), FilePath.class)) {
                files.add(filePath.getFile());
            }
        }
        return files;
    }

    private static ProjectLibrary createScalaSdkLibrary(Project scalaProject, Iterable<File> files, boolean useScalaSdk, IdeaModule ideaModule) {
        ScalaRuntime runtime = scalaProject.getExtensions().findByType(ScalaRuntime.class);
        if (runtime != null) {
            // Old Scala Plugin does not specify a ScalaPlatform
            FileCollection scalaClasspath = runtime.inferScalaClasspath(files);
            File compilerJar = runtime.findScalaJar(scalaClasspath, "compiler");
            ScalaPlatform scalaPlatform = compilerJar != null ? new DefaultScalaPlatform(runtime.getScalaVersion(compilerJar)) : new DefaultScalaPlatform();
            return createScalaSdkFromPlatform(scalaPlatform, scalaClasspath, useScalaSdk);
        } else if (ideaModule.getScalaPlatform() != null) {
            // TODO: Wrong, using the full classpath of the application
            return createScalaSdkFromPlatform(ideaModule.getScalaPlatform(), scalaProject.files(files), useScalaSdk);
        } else {
            // One of the Scala plugins is applied, but ScalaRuntime extension is missing or the ScalaPlatform is undefined.
            // we can't create a Scala SDK without either one
            return null;
        }
    }

    private static ProjectLibrary createScalaSdkFromPlatform(ScalaPlatform platform, FileCollection scalaClasspath, boolean useScalaSdk) {
        String version = platform.getScalaVersion();
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
                newLibrary.setName(originalName + "-" + (suffix++));
            }
            existingLibraries.add(newLibrary);
        }
    }

    private static boolean containsLibraryWithSameName(Set<ProjectLibrary> libraries, final String name) {
        return Iterables.any(libraries, new Predicate<ProjectLibrary>() {
            @Override
            public boolean apply(ProjectLibrary library) {
                return Objects.equal(library.getName(), name);
            }
        });
    }

    private static void declareScalaSdk(ProjectLibrary scalaSdkLibrary, Node iml) {
        // only define a Scala SDK for a module if we could create a scalaSdkLibrary
        if (scalaSdkLibrary != null) {
            Node newModuleRootManager = findOrCreateFirstChildWithAttributeValue(iml, "component", "name", "NewModuleRootManager");

            Node sdkLibrary = findOrCreateFirstChildWithAttributeValue(newModuleRootManager, "orderEntry", "name", scalaSdkLibrary.getName());
            sdkLibrary.attributes().put("type", "library");
            sdkLibrary.attributes().put("level", "project");
        }
    }

    private static void declareScalaFacet(ProjectLibrary scalaCompilerLibrary, Node iml) {
        Node facetManager = findOrCreateFirstChildWithAttributeValue(iml, "component", "name", "FacetManager");

        Node scalaFacet = findOrCreateFirstChildWithAttributeValue(facetManager, "facet", "type", "scala");
        scalaFacet.attributes().put("name", "Scala");


        Node configuration = findOrCreateFirstChildNamed(scalaFacet, "configuration");

        Node libraryLevel = findOrCreateFirstChildWithAttributeValue(configuration, "option", "name", "compilerLibraryLevel");
        libraryLevel.attributes().put("value", "Project");

        Node libraryName = findOrCreateFirstChildWithAttributeValue(configuration, "option", "name", "compilerLibraryName");
        libraryName.attributes().put("value", scalaCompilerLibrary.getName());
    }

    private Collection<Project> findProjectsApplyingIdeaAndScalaPlugins() {
        return Collections2.filter(rootProject.getAllprojects(), new Predicate<Project>() {
            @Override
            public boolean apply(Project project) {
                return project.getPlugins().hasPlugin(IdeaPlugin.class) && (project.getPlugins().hasPlugin(ScalaBasePlugin.class) || project.getPlugins().hasPlugin(ScalaLanguagePlugin.class));
            }
        });
    }

    private VersionNumber findIdeaTargetVersion() {
        VersionNumber targetVersion = null;
        String targetVersionString = rootProject.getExtensions().getByType(IdeaModel.class).getTargetVersion();
        if (targetVersionString != null) {
            targetVersion = VersionNumber.parse(targetVersionString);
            if (targetVersion.equals(VersionNumber.UNKNOWN)) {
                throw new GradleScriptException("String \'" + targetVersionString + "\' is not a valid value for IdeaModel.targetVersion.", null);
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
