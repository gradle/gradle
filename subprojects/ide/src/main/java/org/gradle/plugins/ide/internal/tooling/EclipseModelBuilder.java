/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.AbstractLibrary;
import org.gradle.plugins.ide.eclipse.model.AccessRule;
import org.gradle.plugins.ide.eclipse.model.BuildCommand;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.Container;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.EclipseJdt;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.eclipse.model.Link;
import org.gradle.plugins.ide.eclipse.model.Output;
import org.gradle.plugins.ide.eclipse.model.ProjectDependency;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;
import org.gradle.plugins.ide.eclipse.model.UnresolvedLibrary;
import org.gradle.plugins.ide.internal.configurer.EclipseModelAwareUniqueProjectNameProvider;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultAccessRule;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultClasspathAttribute;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseBuildCommand;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseClasspathContainer;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseExternalDependency;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseJavaSourceSettings;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseLinkedResource;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseOutputLocation;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseProject;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseProjectDependency;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseProjectNature;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseSourceDirectory;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseTask;
import org.gradle.plugins.ide.internal.tooling.java.DefaultInstalledJdk;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.eclipse.EclipseRuntime;
import org.gradle.tooling.model.eclipse.EclipseWorkspace;
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EclipseModelBuilder implements ParameterizedToolingModelBuilder<EclipseRuntime> {
    private final GradleProjectBuilder gradleProjectBuilder;
    private final EclipseModelAwareUniqueProjectNameProvider uniqueProjectNameProvider;

    private boolean projectDependenciesOnly;
    private DefaultEclipseProject result;
    private List<DefaultEclipseProject> eclipseProjects;
    private TasksFactory tasksFactory;
    private DefaultGradleProject rootGradleProject;
    private Project currentProject;
    private EclipseRuntime eclipseRuntime;
    private Map<String, Boolean> projectOpenStatus = new HashMap<>();

    @VisibleForTesting
    public EclipseModelBuilder(GradleProjectBuilder gradleProjectBuilder, EclipseModelAwareUniqueProjectNameProvider uniqueProjectNameProvider) {
        this.gradleProjectBuilder = gradleProjectBuilder;
        this.uniqueProjectNameProvider = uniqueProjectNameProvider;
    }

    public EclipseModelBuilder(GradleProjectBuilder gradleProjectBuilder, ProjectStateRegistry projectStateRegistry) {
        this(gradleProjectBuilder, new EclipseModelAwareUniqueProjectNameProvider(projectStateRegistry));
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.eclipse.EclipseProject") || modelName.equals("org.gradle.tooling.model.eclipse.HierarchicalEclipseProject");
    }

    @Override
    public Class<EclipseRuntime> getParameterType() {
        return EclipseRuntime.class;
    }

    @Override
    public Object buildAll(String modelName, EclipseRuntime eclipseRuntime, Project project) {
        this.eclipseRuntime = eclipseRuntime;
        List<EclipseWorkspaceProject> projects = eclipseRuntime.getWorkspace().getProjects();
        HashSet<EclipseWorkspaceProject> projectsInBuild = new HashSet<>(projects);
        projectsInBuild.removeAll(gatherExternalProjects((ProjectInternal) project.getRootProject(), projects));
        projectOpenStatus = projectsInBuild.stream().collect(Collectors.toMap(EclipseWorkspaceProject::getName, EclipseModelBuilder::isProjectOpen, (a, b) -> a | b));

        return buildAll(modelName, project);
    }

    public static boolean isProjectOpen(EclipseWorkspaceProject project) {
        // TODO we should refactor this to general, compatibility mapping solution, as we have it for model loading. See HasCompatibilityMapping class.
        try {
            return project.isOpen();
        } catch (UnsupportedMethodException e) {
            // isOpen was added in gradle 5.6. for 5.5 we default to true
            return true;
        }
    }

    @Override
    public DefaultEclipseProject buildAll(String modelName, Project project) {
        boolean includeTasks = modelName.equals("org.gradle.tooling.model.eclipse.EclipseProject");
        tasksFactory = new TasksFactory(includeTasks);
        projectDependenciesOnly = modelName.equals("org.gradle.tooling.model.eclipse.HierarchicalEclipseProject");
        currentProject = project;
        eclipseProjects = Lists.newArrayList();
        ProjectInternal root = (ProjectInternal) project.getRootProject();
        rootGradleProject = gradleProjectBuilder.buildAll(project);
        tasksFactory.collectTasks(root);
        applyEclipsePlugin(root, new ArrayList<>());
        deduplicateProjectNames(root);
        buildHierarchy(root);
        populate(root);
        return result;
    }

    private void deduplicateProjectNames(ProjectInternal root) {
        uniqueProjectNameProvider.setReservedProjectNames(calculateReservedProjectNames(root, eclipseRuntime));
        for (Project project : root.getAllprojects()) {
            EclipseModel eclipseModel = project.getExtensions().findByType(EclipseModel.class);
            if (eclipseModel != null) {
                eclipseModel.getProject().setName(uniqueProjectNameProvider.getUniqueName(project));
            }
        }
    }

    private void applyEclipsePlugin(ProjectInternal root, List<GradleInternal> alreadyProcessed) {
        Set<Project> allProjects = root.getAllprojects();
        for (Project p : allProjects) {
            p.getPluginManager().apply(EclipsePlugin.class);
        }
        for (IncludedBuildInternal reference : root.getGradle().includedBuilds()) {
            BuildState target = reference.getTarget();
            if (target instanceof IncludedBuildState) {
                GradleInternal build = ((IncludedBuildState) target).getConfiguredBuild();
                if (!alreadyProcessed.contains(build)) {
                    alreadyProcessed.add(build);
                    applyEclipsePlugin(build.getRootProject(), alreadyProcessed);
                }
            }
        }
    }

    private DefaultEclipseProject buildHierarchy(Project project) {
        List<DefaultEclipseProject> children = new ArrayList<>();
        for (Project child : project.getChildProjects().values()) {
            children.add(buildHierarchy(child));
        }

        EclipseModel eclipseModel = project.getExtensions().getByType(EclipseModel.class);
        org.gradle.plugins.ide.eclipse.model.EclipseProject internalProject = eclipseModel.getProject();
        String name = internalProject.getName();
        String description = GUtil.elvis(internalProject.getComment(), null);
        DefaultEclipseProject eclipseProject = new DefaultEclipseProject(name, project.getPath(), description, project.getProjectDir(), children).setGradleProject(rootGradleProject.findByPath(project.getPath()));

        for (DefaultEclipseProject child : children) {
            child.setParent(eclipseProject);
        }
        addProject(project, eclipseProject);
        return eclipseProject;
    }

    private void addProject(Project project, DefaultEclipseProject eclipseProject) {
        if (project == currentProject) {
            result = eclipseProject;
        }
        eclipseProjects.add(eclipseProject);
    }

    private void populate(Project project) {
        ((ProjectInternal) project).getModel().applyToMutableState(state -> {
            EclipseModel eclipseModel = project.getExtensions().getByType(EclipseModel.class);

            boolean projectDependenciesOnly = this.projectDependenciesOnly;

            ClasspathElements classpathElements = gatherClasspathElements(projectOpenStatus, eclipseModel.getClasspath(), projectDependenciesOnly);

            DefaultEclipseProject eclipseProject = findEclipseProject(project);

            eclipseProject.setClasspath(classpathElements.getExternalDependencies());
            eclipseProject.setProjectDependencies(classpathElements.getProjectDependencies());
            eclipseProject.setSourceDirectories(classpathElements.getSourceDirectories());
            eclipseProject.setClasspathContainers(classpathElements.getClasspathContainers());
            eclipseProject.setOutputLocation(classpathElements.getEclipseOutputLocation() != null ? classpathElements.getEclipseOutputLocation() : new DefaultEclipseOutputLocation("bin"));
            eclipseProject.setAutoBuildTasks(!eclipseModel.getAutoBuildTasks().getDependencies(null).isEmpty());

            org.gradle.plugins.ide.eclipse.model.Project xmlProject = new org.gradle.plugins.ide.eclipse.model.Project(new XmlTransformer());

            XmlFileContentMerger projectFile = eclipseModel.getProject().getFile();
            if (projectFile == null) {
                xmlProject.configure(eclipseModel.getProject());
            } else {
                eclipseModel.getProject().mergeXmlProject(xmlProject);
            }

            populateEclipseProjectTasks(eclipseProject, tasksFactory.getTasks(project));
            populateEclipseProject(eclipseProject, xmlProject);
            populateEclipseProjectJdt(eclipseProject, eclipseModel.getJdt());
        });

        for (Project childProject : project.getChildProjects().values()) {
            populate(childProject);
        }
    }

    public static ClasspathElements gatherClasspathElements(Map<String, Boolean> projectOpenStatus, EclipseClasspath eclipseClasspath, boolean projectDependenciesOnly) {
        ClasspathElements classpathElements = new ClasspathElements();
        eclipseClasspath.setProjectDependenciesOnly(projectDependenciesOnly);

        List<ClasspathEntry> classpathEntries;
        if (eclipseClasspath.getFile() == null) {
            classpathEntries = eclipseClasspath.resolveDependencies();
        } else {
            Classpath classpath = new Classpath(eclipseClasspath.getFileReferenceFactory());
            eclipseClasspath.mergeXmlClasspath(classpath);
            classpathEntries = classpath.getEntries();
        }

        final Map<String, DefaultEclipseProjectDependency> projectDependencyMap = new HashMap<>();

        for (ClasspathEntry entry : classpathEntries) {
            //we don't handle Variables at the moment because users didn't request it yet
            //and it would probably push us to add support in the tooling api to retrieve the variable mappings.
            if (entry instanceof Library) {
                AbstractLibrary library = (AbstractLibrary) entry;
                final File file = library.getLibrary().getFile();
                final File source = library.getSourcePath() == null ? null : library.getSourcePath().getFile();
                final File javadoc = library.getJavadocPath() == null ? null : library.getJavadocPath().getFile();
                DefaultEclipseExternalDependency dependency;
                if (entry instanceof UnresolvedLibrary) {
                    UnresolvedLibrary unresolvedLibrary = (UnresolvedLibrary) entry;
                    dependency = DefaultEclipseExternalDependency.createUnresolved(file, javadoc, source, library.getModuleVersion(), library.isExported(), createAttributes(library), createAccessRules(library), unresolvedLibrary.getAttemptedSelector().getDisplayName());
                } else {
                    dependency = DefaultEclipseExternalDependency.createResolved(file, javadoc, source, library.getModuleVersion(), library.isExported(), createAttributes(library), createAccessRules(library));
                }
                classpathElements.getExternalDependencies().add(dependency);
            } else if (entry instanceof ProjectDependency) {
                final ProjectDependency projectDependency = (ProjectDependency) entry;
                // By removing the leading "/", this is no longer a "path" as defined by Eclipse
                final String path = StringUtils.removeStart(projectDependency.getPath(), "/");
                boolean isProjectOpen = projectOpenStatus.getOrDefault(path, true);
                if (!isProjectOpen) {
                    final File source = projectDependency.getPublicationSourcePath() == null ? null : projectDependency.getPublicationSourcePath().getFile();
                    final File javadoc = projectDependency.getPublicationJavadocPath() == null ? null : projectDependency.getPublicationJavadocPath().getFile();
                    classpathElements.getExternalDependencies().add(DefaultEclipseExternalDependency.createResolved(projectDependency.getPublication().getFile(), javadoc, source, null, projectDependency.isExported(), createAttributes(projectDependency), createAccessRules(projectDependency)));
                    classpathElements.getBuildDependencies().add(projectDependency.getBuildDependencies());
                } else {
                    projectDependencyMap.put(path, new DefaultEclipseProjectDependency(path, projectDependency.isExported(), createAttributes(projectDependency), createAccessRules(projectDependency)));
                }
            } else if (entry instanceof SourceFolder) {
                final SourceFolder sourceFolder = (SourceFolder) entry;
                String path = sourceFolder.getPath();
                List<String> excludes = sourceFolder.getExcludes();
                List<String> includes = sourceFolder.getIncludes();
                String output = sourceFolder.getOutput();
                classpathElements.getSourceDirectories().add(new DefaultEclipseSourceDirectory(path, sourceFolder.getDir(), excludes, includes, output, createAttributes(sourceFolder), createAccessRules(sourceFolder)));
            } else if (entry instanceof Container) {
                final Container container = (Container) entry;
                classpathElements.getClasspathContainers().add(new DefaultEclipseClasspathContainer(container.getPath(), container.isExported(), createAttributes(container), createAccessRules(container)));
            } else if (entry instanceof Output) {
                classpathElements.setEclipseOutputLocation(new DefaultEclipseOutputLocation(((Output) entry).getPath()));
            }
        }
        classpathElements.getProjectDependencies().addAll(projectDependencyMap.values());
        return classpathElements;
    }

    private static void populateEclipseProjectTasks(DefaultEclipseProject eclipseProject, Iterable<Task> projectTasks) {
        List<DefaultEclipseTask> tasks = new ArrayList<>();
        for (Task t : projectTasks) {
            tasks.add(new DefaultEclipseTask(eclipseProject, t.getPath(), t.getName(), t.getDescription()));
        }
        eclipseProject.setTasks(tasks);
    }

    private static void populateEclipseProject(DefaultEclipseProject eclipseProject, org.gradle.plugins.ide.eclipse.model.Project xmlProject) {
        List<DefaultEclipseLinkedResource> linkedResources = new LinkedList<>();
        for (Link r : xmlProject.getLinkedResources()) {
            linkedResources.add(new DefaultEclipseLinkedResource(r.getName(), r.getType(), r.getLocation(), r.getLocationUri()));
        }
        eclipseProject.setLinkedResources(linkedResources);

        List<DefaultEclipseProjectNature> natures = new ArrayList<>();
        for (String n : xmlProject.getNatures()) {
            natures.add(new DefaultEclipseProjectNature(n));
        }
        eclipseProject.setProjectNatures(natures);

        List<DefaultEclipseBuildCommand> buildCommands = new ArrayList<>();
        for (BuildCommand b : xmlProject.getBuildCommands()) {
            Map<String, String> arguments = Maps.newLinkedHashMap();
            for (Map.Entry<String, String> entry : b.getArguments().entrySet()) {
                arguments.put(convertGString(entry.getKey()), convertGString(entry.getValue()));
            }
            buildCommands.add(new DefaultEclipseBuildCommand(b.getName(), arguments));
        }
        eclipseProject.setBuildCommands(buildCommands);
    }

    private static void populateEclipseProjectJdt(DefaultEclipseProject eclipseProject, EclipseJdt jdt) {
        if (jdt != null) {
            eclipseProject.setJavaSourceSettings(new DefaultEclipseJavaSourceSettings().setSourceLanguageLevel(jdt.getSourceCompatibility()).setTargetBytecodeVersion(jdt.getTargetCompatibility()).setJdk(DefaultInstalledJdk.current()));
        }
    }

    private DefaultEclipseProject findEclipseProject(final Project project) {
        return CollectionUtils.findFirst(eclipseProjects, new Spec<DefaultEclipseProject>() {
            @Override
            public boolean isSatisfiedBy(DefaultEclipseProject element) {
                return element.getGradleProject().getPath().equals(project.getPath());
            }
        });
    }

    private static List<DefaultClasspathAttribute> createAttributes(AbstractClasspathEntry classpathEntry) {
        List<DefaultClasspathAttribute> result = Lists.newArrayList();
        Map<String, Object> attributes = classpathEntry.getEntryAttributes();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            Object value = entry.getValue();
            result.add(new DefaultClasspathAttribute(convertGString(entry.getKey()), value == null ? "" : value.toString()));
        }
        return result;
    }

    private static List<DefaultAccessRule> createAccessRules(AbstractClasspathEntry classpathEntry) {
        List<DefaultAccessRule> result = Lists.newArrayList();
        for (AccessRule accessRule : classpathEntry.getAccessRules()) {
            result.add(createAccessRule(accessRule));
        }
        return result;
    }

    private static DefaultAccessRule createAccessRule(AccessRule accessRule) {
        int kindCode;
        String kind = accessRule.getKind();
        switch (kind) {
            case "accessible":
            case "0":
                kindCode = 0;
                break;
            case "nonaccessible":
            case "1":
                kindCode = 1;
                break;
            case "discouraged":
            case "2":
                kindCode = 2;
                break;
            default:
                kindCode = 0;
                break;
        }
        return new DefaultAccessRule(kindCode, accessRule.getPattern());
    }

    private List<Project> collectAllProjects(List<Project> all, GradleInternal gradle, Set<Gradle> allBuilds) {
        all.addAll(gradle.getRootProject().getAllprojects());
        for (IncludedBuildInternal reference : gradle.includedBuilds()) {
            BuildState target = reference.getTarget();
            if (target instanceof IncludedBuildState) {
                GradleInternal build = ((IncludedBuildState) target).getConfiguredBuild();
                if (!allBuilds.contains(build)) {
                    allBuilds.add(build);
                    collectAllProjects(all, build, allBuilds);
                }
            }
        }
        return all;
    }

    private GradleInternal getRootBuild(GradleInternal gradle) {
        if (gradle.getParent() == null) {
            return gradle;
        }
        return gradle.getParent();
    }

    private List<String> calculateReservedProjectNames(ProjectInternal rootProject, EclipseRuntime parameter) {
        if (parameter == null) {
            return Collections.emptyList();
        }

        EclipseWorkspace workspace = parameter.getWorkspace();
        if (workspace == null) {
            return Collections.emptyList();
        }

        List<EclipseWorkspaceProject> projects = workspace.getProjects();
        if (projects == null) {
            return Collections.emptyList();
        }

        List<String> reservedProjectNames = new ArrayList<>();
        List<EclipseWorkspaceProject> externalProjects = gatherExternalProjects(rootProject, projects);
        for (EclipseWorkspaceProject externalProject : externalProjects) {
            reservedProjectNames.add(externalProject.getName());
        }

        return reservedProjectNames;
    }

    private List<EclipseWorkspaceProject> gatherExternalProjects(ProjectInternal rootProject, List<EclipseWorkspaceProject> projects) {
        // The eclipse workspace contains projects from root and included builds. Check projects from all builds
        // so that models built for included builds do not consider projects from parent builds as external.
        Set<File> gradleProjectLocations = collectAllProjects(new ArrayList<>(), getRootBuild(rootProject.getGradle()), new HashSet<>()).stream().map(p -> p.getProjectDir().getAbsoluteFile()).collect(Collectors.toSet());
        List<EclipseWorkspaceProject> externalProjects = new ArrayList<>();
        for (EclipseWorkspaceProject project : projects) {
            if (project == null || project.getLocation() == null || project.getName() == null || project.getLocation() == null) {
                continue;
            }
            if (!gradleProjectLocations.contains(project.getLocation().getAbsoluteFile())) {
                externalProjects.add(project);
            }
        }
        return externalProjects;
    }


    /*
     * Groovy manipulates the JVM to let GString extend String.
     * Whenever we have a Set or Map containing Strings, it might also
     * contain GStrings. This breaks deserialization on the client.
     * This method forces GString to String conversion.
     */
    private static String convertGString(CharSequence original) {
        return original.toString();
    }

    public static class ClasspathElements {
        private final List<DefaultEclipseExternalDependency> externalDependencies = new ArrayList<>();
        private final List<DefaultEclipseProjectDependency> projectDependencies = new ArrayList<>();
        private final List<DefaultEclipseSourceDirectory> sourceDirectories = new ArrayList<>();
        private final List<DefaultEclipseClasspathContainer> classpathContainers = new ArrayList<>();
        private final List<TaskDependency> buildDependencies = new ArrayList<>();
        private DefaultEclipseOutputLocation eclipseOutputLocation;

        public List<DefaultEclipseExternalDependency> getExternalDependencies() {
            return externalDependencies;
        }

        public List<DefaultEclipseProjectDependency> getProjectDependencies() {
            return projectDependencies;
        }

        public List<DefaultEclipseSourceDirectory> getSourceDirectories() {
            return sourceDirectories;
        }

        public List<DefaultEclipseClasspathContainer> getClasspathContainers() {
            return classpathContainers;
        }

        public List<TaskDependency> getBuildDependencies() {
            return buildDependencies;
        }

        public DefaultEclipseOutputLocation getEclipseOutputLocation() {
            return eclipseOutputLocation;
        }

        public void setEclipseOutputLocation(DefaultEclipseOutputLocation eclipseOutputLocation) {
            this.eclipseOutputLocation = eclipseOutputLocation;
        }
    }
}
