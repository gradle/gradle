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
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.tasks.Delete;
import org.gradle.ide.xcode.internal.DefaultXcodeExtension;
import org.gradle.ide.xcode.internal.XcodeScheme;
import org.gradle.ide.xcode.internal.XcodeTarget;
import org.gradle.ide.xcode.internal.xcodeproj.FileTypes;
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget;
import org.gradle.ide.xcode.tasks.GenerateSchemeFileTask;
import org.gradle.ide.xcode.tasks.GenerateWorkspaceSettingsFileTask;
import org.gradle.ide.xcode.tasks.GenerateXcodeProjectFileTask;
import org.gradle.language.swift.plugins.SwiftExecutablePlugin;
import org.gradle.language.swift.plugins.SwiftModulePlugin;
import org.gradle.plugins.ide.internal.IdePlugin;

import java.io.File;
import java.util.Set;

/**
 * A plugin for creating a XCode project for a gradle project.
 */
@Incubating
public class XcodePlugin extends IdePlugin {
    private DefaultXcodeExtension xcode;

    @Override
    protected String getLifecycleTaskName() {
        return "xcode";
    }

    @Override
    protected void onApply(final Project project) {
        getLifecycleTask().setDescription("Generates XCode project files (pbxproj, xcworkspace, xcscheme)");
        getCleanTask().setDescription("Cleans XCode project files (xcodeproj)");

        xcode = project.getExtensions().create("xcode", DefaultXcodeExtension.class);

        configureForSwiftPlugin(project);

        configureXcodeProject(project);
        configureXcodeCleanTask(project);
    }

    private void configureXcodeCleanTask(Project project) {
        Delete cleanTask = project.getTasks().create("cleanXcodeProject", Delete.class);
        cleanTask.delete(toXcodeProjectPackageDir(project));
        getCleanTask().dependsOn(cleanTask);
    }

    private void configureXcodeProject(final Project project) {
        File xcodeProjectPackageDir = toXcodeProjectPackageDir(project);

        GenerateXcodeProjectFileTask projectFileTask = project.getTasks().create("xcodeProject", GenerateXcodeProjectFileTask.class);
        projectFileTask.setXcodeProject(xcode.getProject());
        projectFileTask.setOutputFile(new File(xcodeProjectPackageDir, "project.pbxproj"));
        getLifecycleTask().dependsOn(projectFileTask);

        GenerateWorkspaceSettingsFileTask workspaceSettingsFileTask = project.getTasks().create("xcodeWorkspaceSettings", GenerateWorkspaceSettingsFileTask.class);
        workspaceSettingsFileTask.setOutputFile(new File(xcodeProjectPackageDir, "project.xcworkspace/xcshareddata/WorkspaceSettings.xcsettings"));
        getLifecycleTask().dependsOn(workspaceSettingsFileTask);

        for (XcodeScheme scheme : xcode.getProject().getSchemes()) {
            // TODO - Ensure scheme.getName() give something sensible
            GenerateSchemeFileTask schemeFileTask = project.getTasks().create("xcodeScheme" + scheme.getName(), GenerateSchemeFileTask.class);
            schemeFileTask.setScheme(scheme);
            schemeFileTask.setOutputFile(new File(xcodeProjectPackageDir, "xcshareddata/xcschemes/" + scheme.getName() + ".xcscheme"));
            getLifecycleTask().dependsOn(schemeFileTask);
        }
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
        if (project.getBuildFile().exists()) {
            xcode.getProject().getSources().add(project.getBuildFile());
        }

        ConfigurableFileTree sourceTree = project.fileTree("src/main/swift");
        sourceTree.include("**/*.swift");
        xcode.getProject().getSources().addAll(sourceTree.getFiles());

        XcodeTarget target = newTarget(project.getPath(), productType, toGradleCommand(project.getRootProject()), project.getTasks().getByName("linkMain").getPath(), project.file("build/exe/" + project.getName()), sourceTree.getFiles());
        xcode.getProject().getTargets().add(target);
        xcode.getProject().getSchemes().add(newScheme(target));
    }

    private static String toGradleCommand(Project project) {
        if (project.file("gradlew").exists()) {
            return project.file("gradlew").getAbsolutePath();
        } else {
            return "gradle";
        }
    }

    private static XcodeTarget newTarget(String name, PBXTarget.ProductType productType, String gradleCommand, String taskName, File outputFile, Set<File> sources) {
        XcodeTarget target = new XcodeTarget(name + " " + toString(productType));
        target.setOutputFile(outputFile);
        target.setTaskName(taskName);
        target.setGradleCommand(gradleCommand);
        target.setOutputFileType(toFileType(productType));
        target.setProductType(productType);
        target.setProductName(outputFile.getName());
        target.setSources(sources);

        return target;
    }

    private static XcodeScheme newScheme(XcodeTarget target) {
        XcodeScheme scheme = new XcodeScheme(target.getName());
        scheme.getBuildEntries().add(new XcodeScheme.BuildEntry(target));
        scheme.setBuildConfiguration("Debug");

        return scheme;
    }

    private static File toXcodeProjectPackageDir(Project project) {
        return project.file(project.getName() + ".xcodeproj");
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
            return FileTypes.MACH_O_EXECUTABLE;
        } else if (PBXTarget.ProductType.DYNAMIC_LIBRARY.equals(productType)) {
            return FileTypes.MACH_O_DYNAMIC_LIBRARY;
        } else {
            return "compiled";
        }
    }
}
