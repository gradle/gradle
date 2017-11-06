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

package org.gradle.ide.xcode.tasks;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSString;
import com.google.common.base.Optional;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Internal;
import org.gradle.ide.xcode.XcodeProject;
import org.gradle.ide.xcode.internal.DefaultXcodeProject;
import org.gradle.ide.xcode.internal.XcodeTarget;
import org.gradle.ide.xcode.internal.xcodeproj.GidGenerator;
import org.gradle.ide.xcode.internal.xcodeproj.PBXBuildFile;
import org.gradle.ide.xcode.internal.xcodeproj.PBXFileReference;
import org.gradle.ide.xcode.internal.xcodeproj.PBXGroup;
import org.gradle.ide.xcode.internal.xcodeproj.PBXLegacyTarget;
import org.gradle.ide.xcode.internal.xcodeproj.PBXNativeTarget;
import org.gradle.ide.xcode.internal.xcodeproj.PBXProject;
import org.gradle.ide.xcode.internal.xcodeproj.PBXReference;
import org.gradle.ide.xcode.internal.xcodeproj.PBXShellScriptBuildPhase;
import org.gradle.ide.xcode.internal.xcodeproj.PBXSourcesBuildPhase;
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget;
import org.gradle.ide.xcode.internal.xcodeproj.XcodeprojSerializer;
import org.gradle.ide.xcode.tasks.internal.XcodeProjectFile;
import org.gradle.plugins.ide.api.PropertyListGeneratorTask;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.gradle.ide.xcode.internal.DefaultXcodeProject.*;
import static org.gradle.ide.xcode.internal.XcodeUtils.toSpaceSeparatedList;

/**
 * Task for generating a project file.
 *
 * @since 4.2
 */
@Incubating
public class GenerateXcodeProjectFileTask extends PropertyListGeneratorTask<XcodeProjectFile> {
    private static final String PRODUCTS_GROUP_NAME = "Products";
    private final GidGenerator gidGenerator;
    private DefaultXcodeProject xcodeProject;
    private Map<String, PBXFileReference> pathToFileReference = new HashMap<String, PBXFileReference>();

    @Inject
    public GenerateXcodeProjectFileTask(GidGenerator gidGenerator) {
        this.gidGenerator = gidGenerator;
    }

    @Override
    protected void configure(XcodeProjectFile projectFile) {
        PBXProject project = new PBXProject(getProject().getPath());

        project.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(BUILD_DEBUG);
        project.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(BUILD_RELEASE);

        addToGroup(project.getMainGroup(), xcodeProject.getGroups().getSources(), "Sources");
        addToGroup(project.getMainGroup(), xcodeProject.getGroups().getHeaders(), "Headers");
        addToGroup(project.getMainGroup(), xcodeProject.getGroups().getTests(), "Tests");
        addToGroup(project.getMainGroup(), xcodeProject.getGroups().getRoot());

        for (XcodeTarget xcodeTarget : xcodeProject.getTargets()) {
            project.getTargets().add(toGradlePbxTarget(xcodeTarget));
            project.getTargets().add(toIndexPbxTarget(xcodeTarget));

            if (xcodeTarget.isUnitTest()) {
                // Creates XCTest configuration only if XCTest are present.
                project.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(TEST_DEBUG);
            } else {
                File debugOutputFile = xcodeTarget.getDebugOutputFile().get().getAsFile();
                PBXFileReference fileReference = new PBXFileReference(debugOutputFile.getName(), debugOutputFile.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE);
                fileReference.setExplicitFileType(Optional.of(xcodeTarget.getOutputFileType()));
                project.getMainGroup().getOrCreateChildGroupByName(PRODUCTS_GROUP_NAME).getChildren().add(fileReference);
            }
        }

        XcodeprojSerializer serializer = new XcodeprojSerializer(gidGenerator, project);
        final NSDictionary rootObject = serializer.toPlist();

        projectFile.transformAction(new Action<NSDictionary>() {
            @Override
            public void execute(NSDictionary dict) {
                dict.clear();
                dict.putAll(rootObject);
            }
        });
    }

    private void addToGroup(PBXGroup mainGroup, FileCollection sources, String groupName) {
        if (!sources.isEmpty()) {
            addToGroup(mainGroup.getOrCreateChildGroupByName(groupName), sources);
        }
    }

    private void addToGroup(PBXGroup group, FileCollection sources) {
        for (File source : sources.getAsFileTree()) {
            PBXFileReference fileReference = toFileReference(source);
            pathToFileReference.put(source.getAbsolutePath(), fileReference);
            group.getChildren().add(fileReference);
        }
    }

    @Override
    protected XcodeProjectFile create() {
        return new XcodeProjectFile(getPropertyListTransformer());
    }

    private PBXFileReference toFileReference(File file) {
        return new PBXFileReference(file.getName(), file.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE);
    }

    private PBXTarget toGradlePbxTarget(XcodeTarget xcodeTarget) {
        if (xcodeTarget.isUnitTest()) {
            return toXCTestPbxTarget(xcodeTarget);
        }
        return toToolAndLibraryPbxTarget(xcodeTarget);
    }

    private PBXTarget toToolAndLibraryPbxTarget(XcodeTarget xcodeTarget) {
        PBXLegacyTarget target = new PBXLegacyTarget(xcodeTarget.getName(), xcodeTarget.getProductType());
        target.setProductName(xcodeTarget.getProductName());

        NSDictionary debugSettings = target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(BUILD_DEBUG).getBuildSettings();
        NSDictionary releaseSettings = target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(BUILD_RELEASE).getBuildSettings();

        target.setBuildToolPath(xcodeTarget.getGradleCommand());
        target.setBuildArgumentsString(xcodeTarget.getTaskName());
        target.setGlobalID(xcodeTarget.getId());
        File outputFile = xcodeTarget.getDebugOutputFile().get().getAsFile();
        target.setProductReference(new PBXFileReference(outputFile.getName(), outputFile.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE));

        File debugOutputFile = xcodeTarget.getDebugOutputFile().get().getAsFile();
        debugSettings.put("CONFIGURATION_BUILD_DIR", new NSString(debugOutputFile.getParentFile().getAbsolutePath()));
        debugSettings.put("PRODUCT_NAME", target.getProductName());

        File releaseOutputFile = xcodeTarget.getReleaseOutputFile().get().getAsFile();
        releaseSettings.put("CONFIGURATION_BUILD_DIR", new NSString(releaseOutputFile.getParentFile().getAbsolutePath()));
        releaseSettings.put("PRODUCT_NAME", target.getProductName());

        return target;
    }

    private PBXTarget toXCTestPbxTarget(XcodeTarget xcodeTarget) {
        PBXShellScriptBuildPhase hackBuildPhase = new PBXShellScriptBuildPhase();
        hackBuildPhase.setShellPath("/bin/sh");
        hackBuildPhase.setShellScript("# Script to generate specific Swift files Xcode expects when running tests.\n"
            + "set -eu\n"
            + "ARCH_ARRAY=($ARCHS)\n"
            + "SUFFIXES=(swiftdoc swiftmodule h)\n"
            + "for ARCH in \"${ARCH_ARRAY[@]}\"\n"
            + "do\n"
            + "  for SUFFIX in \"${SUFFIXES[@]}\"\n"
            + "  do\n"
            + "    touch \"$OBJECT_FILE_DIR_normal/$ARCH/$PRODUCT_NAME.$SUFFIX\"\n"
            + "  done\n"
            + "done");

        PBXSourcesBuildPhase sourcesBuildPhase = new PBXSourcesBuildPhase();
        for (File file : xcodeTarget.getSources()) {
            PBXFileReference fileReference = pathToFileReference.get(file.getAbsolutePath());
            sourcesBuildPhase.getFiles().add(new PBXBuildFile(fileReference));
        }

        PBXShellScriptBuildPhase gradleBuildPhase = new PBXShellScriptBuildPhase();
        gradleBuildPhase.setShellPath("/bin/sh");
        gradleBuildPhase.setShellScript("exec \"" + xcodeTarget.getGradleCommand() + "\" " + xcodeTarget.getTaskName() + " < /dev/null");

        PBXNativeTarget target = new PBXNativeTarget(xcodeTarget.getName(), xcodeTarget.getProductType());
        target.setProductName(xcodeTarget.getProductName());
        target.setGlobalID(xcodeTarget.getId());
        // Note the order in which the build phase are added is important
        target.getBuildPhases().add(hackBuildPhase);
        target.getBuildPhases().add(sourcesBuildPhase);
        target.getBuildPhases().add(gradleBuildPhase);
        File outputFile = xcodeTarget.getDebugOutputFile().get().getAsFile();
        target.setProductReference(new PBXFileReference(outputFile.getName(), outputFile.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE));
        NSDictionary debugSettings = target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(BUILD_DEBUG).getBuildSettings();
        NSDictionary releaseSettings = target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(BUILD_RELEASE).getBuildSettings();
        NSDictionary testRunnerSettings = target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(TEST_DEBUG).getBuildSettings();

        if (!xcodeTarget.getCompileModules().isEmpty()) {
            debugSettings.put("SWIFT_INCLUDE_PATHS", toSpaceSeparatedList(xcodeTarget.getCompileModules()));
            releaseSettings.put("SWIFT_INCLUDE_PATHS", toSpaceSeparatedList(xcodeTarget.getCompileModules()));
            testRunnerSettings.put("SWIFT_INCLUDE_PATHS", toSpaceSeparatedList(xcodeTarget.getCompileModules()));
        }

        testRunnerSettings.put("SWIFT_VERSION", "3.0");  // TODO - Choose the right version for swift
        testRunnerSettings.put("PRODUCT_NAME", target.getProductName());
        testRunnerSettings.put("OTHER_LDFLAGS", "-help");
        testRunnerSettings.put("OTHER_CFLAGS", "-help");
        testRunnerSettings.put("OTHER_SWIFT_FLAGS", "-help");
        testRunnerSettings.put("SWIFT_INSTALL_OBJC_HEADER", "NO");
        testRunnerSettings.put("SWIFT_OBJC_INTERFACE_HEADER_NAME", "$(PRODUCT_NAME).h");

        debugSettings.put("PRODUCT_NAME", target.getProductName());
        debugSettings.put("SWIFT_VERSION", "3.0");  // TODO - Choose the right version for swift

        releaseSettings.put("PRODUCT_NAME", target.getProductName());
        releaseSettings.put("SWIFT_VERSION", "3.0");  // TODO - Choose the right version for swift

        return target;
    }

    private PBXTarget toIndexPbxTarget(XcodeTarget xcodeTarget) {
        PBXSourcesBuildPhase buildPhase = new PBXSourcesBuildPhase();
        for (File file : xcodeTarget.getSources()) {
            PBXFileReference fileReference = pathToFileReference.get(file.getAbsolutePath());
            buildPhase.getFiles().add(new PBXBuildFile(fileReference));
        }

        PBXNativeTarget target = new PBXNativeTarget("[INDEXING ONLY] " + xcodeTarget.getName(), xcodeTarget.getProductType());
        target.setProductName(xcodeTarget.getProductName());

        NSDictionary buildSettings = new NSDictionary();
        buildSettings.put("SWIFT_VERSION", "3.0");  // TODO - Choose the right version for swift
        buildSettings.put("PRODUCT_NAME", xcodeTarget.getProductName());  // Mandatory

        if (!xcodeTarget.getHeaderSearchPaths().isEmpty()) {
            buildSettings.put("HEADER_SEARCH_PATHS", toSpaceSeparatedList(xcodeTarget.getHeaderSearchPaths()));
        }

        if (!xcodeTarget.getCompileModules().isEmpty()) {
            buildSettings.put("SWIFT_INCLUDE_PATHS", toSpaceSeparatedList(parentDirs(xcodeTarget.getCompileModules())));
        }

        target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(BUILD_DEBUG).setBuildSettings(buildSettings);
        target.getBuildPhases().add(buildPhase);

        return target;
    }

    private Iterable<File> parentDirs(Iterable<File> files) {
        List<File> parents = new ArrayList<File>();
        for (File file : files) {
            parents.add(file.getParentFile());
        }
        return parents;
    }

    @Internal
    public XcodeProject getXcodeProject() {
        return xcodeProject;
    }

    public void setXcodeProject(XcodeProject xcodeProject) {
        this.xcodeProject = (DefaultXcodeProject) xcodeProject;
    }
}
