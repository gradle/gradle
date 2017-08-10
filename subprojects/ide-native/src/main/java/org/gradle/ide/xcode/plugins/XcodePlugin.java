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

package org.gradle.ide.xcode.plugins;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectLocalComponentProvider;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.ide.xcode.XcodeExtension;
import org.gradle.ide.xcode.XcodeProject;
import org.gradle.ide.xcode.internal.DefaultXcodeExtension;
import org.gradle.ide.xcode.internal.DefaultXcodeProject;
import org.gradle.ide.xcode.internal.XcodeTarget;
import org.gradle.ide.xcode.internal.xcodeproj.FileTypes;
import org.gradle.ide.xcode.internal.xcodeproj.GidGenerator;
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget;
import org.gradle.ide.xcode.tasks.GenerateSchemeFileTask;
import org.gradle.ide.xcode.tasks.GenerateWorkspaceSettingsFileTask;
import org.gradle.ide.xcode.tasks.GenerateXcodeProjectFileTask;
import org.gradle.ide.xcode.tasks.GenerateXcodeWorkspaceFileTask;
import org.gradle.initialization.ProjectPathRegistry;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.cpp.plugins.CppExecutablePlugin;
import org.gradle.language.cpp.plugins.CppLibraryPlugin;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.swift.model.SwiftComponent;
import org.gradle.language.swift.plugins.SwiftExecutablePlugin;
import org.gradle.language.swift.plugins.SwiftLibraryPlugin;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.plugins.ide.internal.IdePlugin;
import org.gradle.util.CollectionUtils;
import org.gradle.util.Path;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

/**
 * A plugin for creating a XCode project for a gradle project.
 *
 * @since 4.2
 */
@Incubating
public class XcodePlugin extends IdePlugin {
    private final GidGenerator gidGenerator;
    private final ObjectFactory objectFactory;
    private DefaultXcodeExtension xcode;
    private GenerateXcodeWorkspaceFileTask workspaceTask;
    private GenerateXcodeProjectFileTask projectTask;

    @Inject
    public XcodePlugin(GidGenerator gidGenerator, ObjectFactory objectFactory) {
        this.gidGenerator = gidGenerator;
        this.objectFactory = objectFactory;
    }

    @Override
    protected String getLifecycleTaskName() {
        return "xcode";
    }

    @Override
    protected void onApply(final Project project) {
        getLifecycleTask().setDescription("Generates XCode project files (pbxproj, xcworkspace, xcscheme)");
        getCleanTask().setDescription("Cleans XCode project files (xcodeproj)");

        xcode = (DefaultXcodeExtension) project.getExtensions().create(XcodeExtension.class, "xcode", DefaultXcodeExtension.class, objectFactory);
        xcode.getProject().setLocationDir(project.file(projectName(project) + ".xcodeproj"));

        projectTask = createProjectTask(project);
        workspaceTask = createWorkspaceTask(project);

        if (getWorkspaceTask() != null) {
            getLifecycleTask().dependsOn(getWorkspaceTask());
        }
        getLifecycleTask().dependsOn(getProjectTask());

        configureForSwiftPlugin(project);
        configureForCppPlugin(project);

        includeBuildFileInProject(project);
        configureXcodeCleanTask(project);
        registerXcodeProjectArtifact(project);
        addIncludedBuildToWorkspace(project);
    }

    private GenerateXcodeProjectFileTask getProjectTask() {
        return projectTask;
    }

    private GenerateXcodeWorkspaceFileTask getWorkspaceTask() {
        return workspaceTask;
    }

    private void includeBuildFileInProject(Project project) {
        if (project.getBuildFile().exists()) {
            xcode.getProject().addSourceFile(project.getBuildFile());
        }
    }

    private void configureXcodeCleanTask(Project project) {
        Delete cleanTask = project.getTasks().create("cleanXcodeProject", Delete.class);
        cleanTask.delete(xcode.getProject().getLocationDir());
        if (isRoot(project)) {
            cleanTask.delete(toXcodeWorkspacePackageDir(project));
        }
        getCleanTask().dependsOn(cleanTask);
    }

    private GenerateXcodeProjectFileTask createProjectTask(final Project project) {
        File xcodeProjectPackageDir = xcode.getProject().getLocationDir();

        GenerateWorkspaceSettingsFileTask workspaceSettingsFileTask = project.getTasks().create("xcodeProjectWorkspaceSettings", GenerateWorkspaceSettingsFileTask.class);
        workspaceSettingsFileTask.setOutputFile(new File(xcodeProjectPackageDir, "project.xcworkspace/xcshareddata/WorkspaceSettings.xcsettings"));

        GenerateXcodeProjectFileTask projectFileTask = project.getTasks().create("xcodeProject", GenerateXcodeProjectFileTask.class);
        projectFileTask.dependsOn(workspaceSettingsFileTask);
        projectFileTask.setXcodeProject(xcode.getProject());
        projectFileTask.setOutputFile(new File(xcodeProjectPackageDir, "project.pbxproj"));

        return projectFileTask;
    }

    private GenerateXcodeWorkspaceFileTask createWorkspaceTask(Project project) {
        if (isRoot(project)) {
            File xcodeWorkspacePackageDir = toXcodeWorkspacePackageDir(project);

            GenerateWorkspaceSettingsFileTask workspaceSettingsFileTask = project.getTasks().create("xcodeWorkspaceWorkspaceSettings", GenerateWorkspaceSettingsFileTask.class);
            workspaceSettingsFileTask.setOutputFile(new File(xcodeWorkspacePackageDir, "xcshareddata/WorkspaceSettings.xcsettings"));

            GenerateXcodeWorkspaceFileTask workspaceFileTask = project.getTasks().create("xcodeWorkspace", GenerateXcodeWorkspaceFileTask.class);
            workspaceFileTask.dependsOn(workspaceSettingsFileTask);
            workspaceFileTask.setOutputFile(new File(xcodeWorkspacePackageDir, "contents.xcworkspacedata"));

            return workspaceFileTask;
        }

        return null;
    }

    private static XcodeExtension xcodeModelFor(Project project) {
        return project.getExtensions().getByType(XcodeExtension.class);
    }

    private void configureForSwiftPlugin(final Project project) {
        project.getPlugins().withType(SwiftExecutablePlugin.class, new Action<SwiftExecutablePlugin>() {
            @Override
            public void execute(SwiftExecutablePlugin plugin) {
                configureXcodeForSwift(project, PBXTarget.ProductType.TOOL);
            }
        });

        project.getPlugins().withType(SwiftLibraryPlugin.class, new Action<SwiftLibraryPlugin>() {
            @Override
            public void execute(SwiftLibraryPlugin plugin) {
                configureXcodeForSwift(project, PBXTarget.ProductType.DYNAMIC_LIBRARY);
            }
        });
    }

    private void configureXcodeForSwift(Project project, PBXTarget.ProductType productType) {
        SwiftComponent component = project.getExtensions().getByType(SwiftComponent.class);
        FileCollection sources = component.getSwiftSource();
        xcode.getProject().getSources().from(sources);

        // TODO - Reuse the logic from `swift-executable` or `swift-library` to determine the link task path
        // TODO - Reuse the logic from `swift-executable` or `swift-library` to determine the header dirs
        SwiftCompile compileTask = (SwiftCompile) project.getTasks().getByName("compileSwift");
        Task linkTask = project.getTasks().getByName("linkMain");
        XcodeTarget target = newTarget(projectName(project) + " " + toString(productType), productType, toGradleCommand(project.getRootProject()), linkTask.getPath(), project.file("build/exe/" + project.getName()), sources);
        target.getImportPaths().from(compileTask.getIncludes());
        xcode.getProject().setTarget(target);

        getProjectTask().dependsOn(createSchemeTask(project.getTasks(), xcode.getProject()));
    }

    private void configureForCppPlugin(final Project project) {
        project.getPlugins().withType(CppExecutablePlugin.class, new Action<CppExecutablePlugin>() {
            @Override
            public void execute(CppExecutablePlugin plugin) {
                configureXcodeForCpp(project, PBXTarget.ProductType.TOOL);
            }
        });

        project.getPlugins().withType(CppLibraryPlugin.class, new Action<CppLibraryPlugin>() {
            @Override
            public void execute(CppLibraryPlugin plugin) {
                configureXcodeForCpp(project, PBXTarget.ProductType.DYNAMIC_LIBRARY);
            }
        });
    }

    private void configureXcodeForCpp(Project project, PBXTarget.ProductType productType) {
        CppComponent component = project.getExtensions().getByType(CppComponent.class);
        FileCollection sources = component.getCppSource();
        xcode.getProject().getSources().from(sources);

        // TODO - Reuse the logic from `cpp-executable` or `cpp-library` to find the header files and include paths
        CppCompile compileTask = (CppCompile) project.getTasks().getByName("compileCpp");
        FileCollection headers = compileTask.getIncludes().minus(project.getConfigurations().getByName(CppBasePlugin.CPP_INCLUDE_PATH));
        xcode.getProject().getSources().from(headers.getAsFileTree());

        // TODO - Reuse the logic from `cpp-executable` or `cpp-library` to find the link task path
        Task linkTask = project.getTasks().getByName("linkMain");
        XcodeTarget target = newTarget(projectName(project) + " " + toString(productType), productType, toGradleCommand(project.getRootProject()), linkTask.getPath(), project.file("build/exe/" + project.getName()), sources);
        target.getHeaderSearchPaths().from(compileTask.getIncludes());
        xcode.getProject().setTarget(target);

        getProjectTask().dependsOn(createSchemeTask(project.getTasks(), xcode.getProject()));
    }

    private static GenerateSchemeFileTask createSchemeTask(TaskContainer tasks, DefaultXcodeProject xcodeProject) {
        GenerateSchemeFileTask schemeFileTask = tasks.create("xcodeScheme" + xcodeProject.getTarget().getName().replaceAll(" ", ""), GenerateSchemeFileTask.class);
        schemeFileTask.setXcodeProject(xcodeProject);
        schemeFileTask.setOutputFile(new File(xcodeProject.getLocationDir(), "xcshareddata/xcschemes/" + xcodeProject.getTarget().getName() + ".xcscheme"));

        return schemeFileTask;
    }

    private static String toGradleCommand(Project project) {
        if (project.file("gradlew").exists()) {
            return project.file("gradlew").getAbsolutePath();
        } else {
            return "gradle";
        }
    }

    private XcodeTarget newTarget(String name, PBXTarget.ProductType productType, String gradleCommand, String taskName, File outputFile, FileCollection sources) {
        String id = gidGenerator.generateGid("PBXLegacyTarget", name.hashCode());
        XcodeTarget target = objectFactory.newInstance(XcodeTarget.class, name, id);
        target.setOutputFile(outputFile);
        target.setTaskName(taskName);
        target.setGradleCommand(gradleCommand);
        target.setOutputFileType(toFileType(productType));
        target.setProductType(productType);
        target.setProductName(outputFile.getName());
        target.setSources(sources);

        return target;
    }

    private static File toXcodeWorkspacePackageDir(Project project) {
        return project.file(project.getName() + ".xcworkspace");
    }

    private static String toString(PBXTarget.ProductType productType) {
        if (PBXTarget.ProductType.TOOL.equals(productType)) {
            return "Executable";
        } else if (PBXTarget.ProductType.DYNAMIC_LIBRARY.equals(productType)) {
            return "SharedLibrary";
        } else {
            return "";
        }
    }

    private static String toFileType(PBXTarget.ProductType productType) {
        if (PBXTarget.ProductType.TOOL.equals(productType)) {
            return FileTypes.MACH_O_EXECUTABLE.identifier;
        } else if (PBXTarget.ProductType.DYNAMIC_LIBRARY.equals(productType)) {
            return FileTypes.MACH_O_DYNAMIC_LIBRARY.identifier;
        } else {
            return "compiled";
        }
    }

    private static boolean isRoot(Project project) {
        return project.getParent() == null;
    }

    private static String projectName(Project project) {
        return project.getName();
    }

    private void registerXcodeProjectArtifact(Project project) {
        ProjectLocalComponentProvider projectComponentProvider = ((ProjectInternal) project).getServices().get(ProjectLocalComponentProvider.class);
        ProjectComponentIdentifier projectId = newProjectId(project);
        projectComponentProvider.registerAdditionalArtifact(projectId, createXcodeProjectArtifact(projectId, xcodeModelFor(project), getProjectTask()));
    }

    private static LocalComponentArtifactMetadata createXcodeProjectArtifact(ProjectComponentIdentifier projectId, XcodeExtension xcode, Task projectTask) {
        XcodeProject xcodeProject = xcode.getProject();
        PublishArtifact publishArtifact = new XcodeProjectArtifact(xcodeProject, projectTask);
        return new PublishArtifactLocalArtifactMetadata(projectId, publishArtifact);
    }

    private void addIncludedBuildToWorkspace(final Project project) {
        if (isRoot(project)) {
            final ServiceRegistry serviceRegistry = ((ProjectInternal) project).getServices();
            getWorkspaceTask().dependsOn(new Callable<List<TaskDependency>>() {
                @Override
                public List<TaskDependency> call() throws Exception {
                    return CollectionUtils.collect(
                        allXcodeprojArtifactsInComposite(serviceRegistry),
                        new Transformer<TaskDependency, LocalComponentArtifactMetadata>() {
                            @Override
                            public TaskDependency transform(LocalComponentArtifactMetadata metadata) {
                                return metadata.getBuildDependencies();
                            }
                        });
                }
            });

            getWorkspaceTask().setXcodeProjectLocations(project.files(new Callable<Iterable<File>>() {
                @Override
                public Iterable<File> call() throws Exception {
                    return CollectionUtils.collect(
                        allXcodeprojArtifactsInComposite(serviceRegistry),
                        new Transformer<File, LocalComponentArtifactMetadata>() {
                            @Override
                            public File transform(LocalComponentArtifactMetadata metadata) {
                                return metadata.getFile();
                            }
                        });
                }
            }));
        }
    }

    private static List<LocalComponentArtifactMetadata> allXcodeprojArtifactsInComposite(ServiceRegistry serviceRegistry) {
        List<LocalComponentArtifactMetadata> artifactMetadata = Lists.newArrayList();
        ProjectPathRegistry projectPathRegistry = serviceRegistry.get(ProjectPathRegistry.class);
        LocalComponentRegistry localComponentRegistry = serviceRegistry.get(LocalComponentRegistry.class);

        for (Path projectPath : projectPathRegistry.getAllProjectPaths()) {
            ProjectComponentIdentifier projectId = projectPathRegistry.getProjectComponentIdentifier(projectPath);
            LocalComponentArtifactMetadata xcodeprojArtifact = localComponentRegistry.findAdditionalArtifact(projectId, "xcodeproj");
            if (xcodeprojArtifact != null) {
                artifactMetadata.add(xcodeprojArtifact);
            }
        }

        return artifactMetadata;
    }

    private static class XcodeProjectArtifact extends DefaultPublishArtifact {
        private final DefaultXcodeProject xcodeProject;

        public XcodeProjectArtifact(XcodeProject xcodeProject, Object... tasks) {
            super(null, "xcodeproj", "xcodeproj", null, null, null, tasks);
            this.xcodeProject = (DefaultXcodeProject) xcodeProject;
        }

        @Override
        public String getName() {
            String fileName = xcodeProject.getLocationDir().getName();
            return fileName.substring(0, fileName.length() - ".xcodeproj".length());
        }

        @Override
        public File getFile() {
            return xcodeProject.getLocationDir();
        }
    }
}
