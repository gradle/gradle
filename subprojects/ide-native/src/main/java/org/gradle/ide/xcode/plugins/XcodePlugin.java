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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.ide.xcode.XcodeExtension;
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
import org.gradle.language.swift.plugins.SwiftExecutablePlugin;
import org.gradle.language.swift.plugins.SwiftModulePlugin;
import org.gradle.plugins.ide.internal.IdePlugin;

import javax.inject.Inject;
import java.io.File;

/**
 * A plugin for creating a XCode project for a gradle project.
 *
 * @since 4.2
 */
@Incubating
public class XcodePlugin extends IdePlugin {
    private DefaultXcodeExtension xcode;

    private final GidGenerator gidGenerator;
    private final FileOperations fileOperations;

    @Inject
    public XcodePlugin(GidGenerator gidGenerator, FileOperations fileOperations) {
        this.gidGenerator = gidGenerator;
        this.fileOperations = fileOperations;
    }

    @Override
    protected String getLifecycleTaskName() {
        return "xcode";
    }

    @Override
    protected void onApply(final Project project) {
        getLifecycleTask().setDescription("Generates XCode project files (pbxproj, xcworkspace, xcscheme)");
        getCleanTask().setDescription("Cleans XCode project files (xcodeproj)");

        xcode = (DefaultXcodeExtension) project.getExtensions().create(XcodeExtension.class, "xcode", DefaultXcodeExtension.class, fileOperations);
        xcode.getProject().setLocationDir(project.file(projectName(project) + ".xcodeproj"));

        configureForSwiftPlugin(project);

        includeBuildFileInProject(project);
        configureXcodeProject(project);
        configureXcodeWorkspace(project);
        configureXcodeCleanTask(project);
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

    private void configureXcodeProject(final Project project) {
        File xcodeProjectPackageDir = xcode.getProject().getLocationDir();

        GenerateXcodeProjectFileTask projectFileTask = project.getTasks().create("xcodeProject", GenerateXcodeProjectFileTask.class);
        projectFileTask.setXcodeProject(xcode.getProject());
        projectFileTask.setOutputFile(new File(xcodeProjectPackageDir, "project.pbxproj"));
        getLifecycleTask().dependsOn(projectFileTask);

        GenerateWorkspaceSettingsFileTask workspaceSettingsFileTask = project.getTasks().create("xcodeProjectWorkspaceSettings", GenerateWorkspaceSettingsFileTask.class);
        workspaceSettingsFileTask.setOutputFile(new File(xcodeProjectPackageDir, "project.xcworkspace/xcshareddata/WorkspaceSettings.xcsettings"));
        getLifecycleTask().dependsOn(workspaceSettingsFileTask);
    }

    private void configureXcodeWorkspace(Project project) {
        if (isRoot(project)) {
            File xcodeWorkspacePackageDir = toXcodeWorkspacePackageDir(project);

            GenerateXcodeWorkspaceFileTask workspaceFileTask = project.getTasks().create("xcodeWorkspace", GenerateXcodeWorkspaceFileTask.class);
            workspaceFileTask.setXcodeWorkspace(xcode.getWorkspace());
            workspaceFileTask.setOutputFile(new File(xcodeWorkspacePackageDir, "contents.xcworkspacedata"));
            getLifecycleTask().dependsOn(workspaceFileTask);

            GenerateWorkspaceSettingsFileTask workspaceSettingsFileTask = project.getTasks().create("xcodeWorkspaceWorkspaceSettings", GenerateWorkspaceSettingsFileTask.class);
            workspaceSettingsFileTask.setOutputFile(new File(xcodeWorkspacePackageDir, "xcshareddata/WorkspaceSettings.xcsettings"));
            getLifecycleTask().dependsOn(workspaceSettingsFileTask);

            for (final Project p : project.getAllprojects()) {
                p.getPlugins().withType(XcodePlugin.class).all(new Action<Plugin>() {
                    @Override
                    public void execute(Plugin plugin) {
                        if (p.getPlugins().hasPlugin(XcodePlugin.class)) {
                            xcode.getWorkspace().getProjects().add((DefaultXcodeProject) xcodeModelFor(p).getProject());
                        }
                    }
                });
            }
        }
    }

    private static XcodeExtension xcodeModelFor(Project project) {
        return project.getExtensions().getByType(XcodeExtension.class);
    }

    private void configureForSwiftPlugin(final Project project) {
        project.getPlugins().withType(SwiftExecutablePlugin.class, new Action<SwiftExecutablePlugin>() {
            @Override
            public void execute(SwiftExecutablePlugin swiftExecutablePlugin) {
                configureXcodeForSwift(project, PBXTarget.ProductType.TOOL);
            }
        });

        project.getPlugins().withType(SwiftModulePlugin.class, new Action<SwiftModulePlugin>() {
            @Override
            public void execute(SwiftModulePlugin swiftModulePlugin) {
                configureXcodeForSwift(project, PBXTarget.ProductType.DYNAMIC_LIBRARY);
            }
        });
    }

    private void configureXcodeForSwift(Project project, PBXTarget.ProductType productType) {
        // TODO - Reuse the logic from `swift-executable` or `swift-module` to find the sources
        ConfigurableFileTree sourceTree = project.fileTree("src/main/swift");
        sourceTree.include("**/*.swift");
        xcode.getProject().getSources().from(sourceTree);

        // TODO - Reuse the logic from `swift-executable` or `swift-module` to find the build task
        XcodeTarget target = newTarget(projectName(project) + " " + toString(productType), productType, toGradleCommand(project.getRootProject()), project.getTasks().getByName("linkMain").getPath(), project.file("build/exe/" + project.getName()), sourceTree);
        xcode.getProject().setTarget(target);

        getLifecycleTask().dependsOn(createSchemeTask(project.getTasks(), xcode.getProject()));
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
        XcodeTarget target = new XcodeTarget(name, id);
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
}
