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
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.ide.xcode.XcodeProject;
import org.gradle.ide.xcode.internal.DefaultXcodeProject;
import org.gradle.ide.xcode.internal.XcodeBinary;
import org.gradle.ide.xcode.internal.XcodePropertyAdapter;
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
import org.gradle.internal.Cast;
import org.gradle.language.swift.SwiftVersion;
import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.plugins.ide.api.PropertyListGeneratorTask;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gradle.ide.xcode.internal.DefaultXcodeProject.TEST_DEBUG;
import static org.gradle.ide.xcode.internal.XcodeUtils.toSpaceSeparatedList;

/**
 * Task for generating a Xcode project file (e.g. {@code Foo.xcodeproj/project.pbxproj}). A project contains all the elements used to build your products and maintains the relationships between those elements. It contains one or more targets, which specify how to build products. A project defines default build settings for all the targets in the project (each target can also specify its own build settings, which override the project build settings).
 *
 * @see org.gradle.ide.xcode.XcodeProject
 * @since 4.2
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class GenerateXcodeProjectFileTask extends PropertyListGeneratorTask<XcodeProjectFile> {
    private static final String PRODUCTS_GROUP_NAME = "Products";
    private static final String UNBUILDABLE_BUILD_CONFIGURATION_NAME = "unbuildable";
    private final String projectPath = getProject().getPath();
    private final GidGenerator gidGenerator;
    private DefaultXcodeProject xcodeProject;
    private Map<String, PBXFileReference> pathToFileReference = new HashMap<String, PBXFileReference>();

    @Inject
    public GenerateXcodeProjectFileTask(GidGenerator gidGenerator) {
        this.gidGenerator = gidGenerator;
    }

    @Override
    protected void configure(XcodeProjectFile projectFile) {
        PBXProject project = new PBXProject(projectPath);

        addToGroup(project.getMainGroup(), xcodeProject.getGroups().getSources(), "Sources");
        addToGroup(project.getMainGroup(), xcodeProject.getGroups().getHeaders(), "Headers");
        addToGroup(project.getMainGroup(), xcodeProject.getGroups().getTests(), "Tests");
        addToGroup(project.getMainGroup(), xcodeProject.getGroups().getRoot());

        for (XcodeTarget xcodeTarget : xcodeProject.getTargets()) {
            if (xcodeTarget.isBuildable()) {
                project.getTargets().add(toGradlePbxTarget(xcodeTarget));
            } else {
                getLogger().warn("'" + xcodeTarget.getName() + "' component in project '" + projectPath + "' is not buildable.");
            }
            project.getTargets().add(toIndexPbxTarget(xcodeTarget));

            if (!xcodeTarget.isUnitTest() && xcodeTarget.getDebugOutputFile().isPresent()) {
                File debugOutputFile = xcodeTarget.getDebugOutputFile().get().getAsFile();
                PBXFileReference fileReference = new PBXFileReference(debugOutputFile.getName(), debugOutputFile.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE);
                fileReference.setExplicitFileType(Optional.of(xcodeTarget.getOutputFileType()));
                project.getMainGroup().getOrCreateChildGroupByName(PRODUCTS_GROUP_NAME).getChildren().add(fileReference);
            }
        }

        // Create build configuration at the project level from all target's build configuration
        project.getTargets().stream().flatMap(it -> it.getBuildConfigurationList().getBuildConfigurationsByName().asMap().keySet().stream()).forEach(project.getBuildConfigurationList().getBuildConfigurationsByName()::getUnchecked);

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

    private void addToGroup(PBXGroup group, Iterable<File> sources) {
        for (File source : sources) {
            PBXFileReference fileReference = toFileReference(source);
            pathToFileReference.put(source.getAbsolutePath(), fileReference);
            group.getChildren().add(fileReference);
        }
    }

    private List<XcodeBinary> getAllBinaries() {
        return xcodeProject.getTargets().stream().map(XcodeTarget::getBinaries).flatMap(Collection::stream).collect(Collectors.toList());
    }

    @Override
    protected XcodeProjectFile create() {
        return new XcodeProjectFile(Cast.uncheckedNonnullCast(getPropertyListTransformer()));
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

        target.setBuildToolPath(xcodeTarget.getGradleCommand());
        target.setBuildArgumentsString(buildGradleArgs(xcodeTarget));
        target.setGlobalID(xcodeTarget.getId());
        File outputFile = xcodeTarget.getDebugOutputFile().get().getAsFile();
        target.setProductReference(new PBXFileReference(outputFile.getName(), outputFile.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE));

        xcodeTarget.getBinaries().forEach(xcodeBinary -> {
            NSDictionary settings = target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(xcodeBinary.getBuildConfigurationName()).getBuildSettings();

            File binaryOutputFile = xcodeBinary.getOutputFile().get().getAsFile();
            settings.put("CONFIGURATION_BUILD_DIR", new NSString(binaryOutputFile.getParentFile().getAbsolutePath()));
            settings.put("PRODUCT_NAME", target.getProductName());
            settings.put("SWIFT_VERSION", toXcodeSwiftVersion(xcodeTarget.getSwiftSourceCompatibility()));
            settings.put("ARCHS", toXcodeArchitecture(xcodeBinary.getArchitectureName()));
            settings.put("VALID_ARCHS", xcodeTarget.getBinaries().stream().map(XcodeBinary::getArchitectureName).map(GenerateXcodeProjectFileTask::toXcodeArchitecture).distinct().collect(Collectors.joining(" ")));
        });

        return target;
    }

    private String buildGradleArgs(XcodeTarget xcodeTarget) {
        return Joiner.on(' ').join(XcodePropertyAdapter.getAdapterCommandLine()) + " " + xcodeTarget.getTaskName();
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

        PBXShellScriptBuildPhase gradleBuildPhase = new PBXShellScriptBuildPhase();
        gradleBuildPhase.setShellPath("/bin/sh");
        gradleBuildPhase.setShellScript("exec \"" + xcodeTarget.getGradleCommand() + "\" " + buildGradleArgs(xcodeTarget) + " < /dev/null");

        PBXNativeTarget target = new PBXNativeTarget(xcodeTarget.getName(), xcodeTarget.getProductType());
        target.setProductName(xcodeTarget.getProductName());
        target.setGlobalID(xcodeTarget.getId());
        // Note the order in which the build phase are added is important
        target.getBuildPhases().add(hackBuildPhase);
        target.getBuildPhases().add(newSourceBuildPhase(xcodeTarget.getSources()));
        target.getBuildPhases().add(gradleBuildPhase);
        File outputFile = xcodeTarget.getDebugOutputFile().get().getAsFile();
        target.setProductReference(new PBXFileReference(outputFile.getName(), outputFile.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE));

        getAllBinaries().stream().filter(it -> !Objects.equals(it.getBuildConfigurationName(), TEST_DEBUG)).forEach(configureBuildSettings(xcodeTarget, target));

        NSDictionary testRunnerSettings = target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(TEST_DEBUG).getBuildSettings();

        if (!xcodeTarget.getCompileModules().isEmpty()) {
            testRunnerSettings.put("SWIFT_INCLUDE_PATHS", toSpaceSeparatedList(parentDirs(xcodeTarget.getCompileModules())));
        }

        testRunnerSettings.put("SWIFT_VERSION", toXcodeSwiftVersion(xcodeTarget.getSwiftSourceCompatibility()));
        testRunnerSettings.put("PRODUCT_NAME", target.getProductName());
        testRunnerSettings.put("OTHER_LDFLAGS", "-help");
        testRunnerSettings.put("OTHER_CFLAGS", "-help");
        testRunnerSettings.put("OTHER_SWIFT_FLAGS", "-help");
        testRunnerSettings.put("SWIFT_INSTALL_OBJC_HEADER", "NO");
        testRunnerSettings.put("SWIFT_OBJC_INTERFACE_HEADER_NAME", "$(PRODUCT_NAME).h");

        return target;
    }

    private PBXTarget toIndexPbxTarget(XcodeTarget xcodeTarget) {
        PBXNativeTarget target = new PBXNativeTarget("[INDEXING ONLY] " + xcodeTarget.getName(), PBXTarget.ProductType.INDEXER);
        target.setProductName(xcodeTarget.getProductName());
        target.getBuildPhases().add(newSourceBuildPhase(xcodeTarget.getSources()));

        xcodeTarget.getBinaries().forEach(configureBuildSettings(xcodeTarget, target));

        // Create unbuildable build configuration so the indexer can keep functioning
        if (xcodeTarget.getBinaries().isEmpty()) {
            NSDictionary settings = newBuildSettings(xcodeTarget);
            target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(UNBUILDABLE_BUILD_CONFIGURATION_NAME).setBuildSettings(settings);
        }

        return target;
    }

    private Consumer<XcodeBinary> configureBuildSettings(XcodeTarget xcodeTarget, PBXNativeTarget target) {
        return xcodeBinary -> {
            NSDictionary settings = newBuildSettings(xcodeTarget);
            settings.put("ARCHS", toXcodeArchitecture(xcodeBinary.getArchitectureName()));
            settings.put("VALID_ARCHS", xcodeTarget.getBinaries().stream().map(it -> GenerateXcodeProjectFileTask.toXcodeArchitecture(it.getArchitectureName())).distinct().collect(Collectors.joining(" ")));
            target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked(xcodeBinary.getBuildConfigurationName()).setBuildSettings(settings);
        };
    }

    private PBXSourcesBuildPhase newSourceBuildPhase(FileCollection sourceFiles) {
        PBXSourcesBuildPhase result = new PBXSourcesBuildPhase();
        for (File file : sourceFiles) {
            PBXFileReference fileReference = pathToFileReference.get(file.getAbsolutePath());
            result.getFiles().add(new PBXBuildFile(fileReference));
        }
        return result;
    }

    private NSDictionary newBuildSettings(XcodeTarget xcodeTarget) {
        NSDictionary result = new NSDictionary();
        result.put("SWIFT_VERSION", toXcodeSwiftVersion(xcodeTarget.getSwiftSourceCompatibility()));
        result.put("PRODUCT_NAME", xcodeTarget.getProductName());  // Mandatory

        if (!xcodeTarget.getHeaderSearchPaths().isEmpty()) {
            result.put("HEADER_SEARCH_PATHS", toSpaceSeparatedList(xcodeTarget.getHeaderSearchPaths()));
        }

        if (!xcodeTarget.getCompileModules().isEmpty()) {
            result.put("SWIFT_INCLUDE_PATHS", toSpaceSeparatedList(parentDirs(xcodeTarget.getCompileModules())));
        }
        return result;
    }

    private static String toXcodeArchitecture(String architectureName) {
        if (architectureName.equals(MachineArchitecture.X86)) {
            return "i386";
        } else if (architectureName.equals(MachineArchitecture.X86_64)) {
            return "x86_64";
        } else if (architectureName.equals(MachineArchitecture.ARM64)) {
            return "arm64e";
        }

        return architectureName;
    }

    private static String toXcodeSwiftVersion(Provider<SwiftVersion> swiftVersion) {
        if (swiftVersion.isPresent()) {
            return String.format("%d.0", swiftVersion.get().getVersion());
        }
        return null;
    }

    private static Iterable<File> parentDirs(Iterable<File> files) {
        List<File> parents = new ArrayList<File>();
        for (File file : files) {
            if (file.isDirectory()) {
                parents.add(file);
            } else {
                parents.add(file.getParentFile());
            }
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
