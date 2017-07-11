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
import com.google.common.base.Optional;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Internal;
import org.gradle.ide.xcode.XcodeProject;
import org.gradle.ide.xcode.internal.DefaultXcodeProject;
import org.gradle.ide.xcode.internal.xcodeproj.GidGenerator;
import org.gradle.ide.xcode.internal.GradleBuildTarget;
import org.gradle.ide.xcode.internal.IndexingSwiftTarget;
import org.gradle.ide.xcode.internal.XcodeTarget;
import org.gradle.ide.xcode.internal.xcodeproj.PBXBuildFile;
import org.gradle.ide.xcode.internal.xcodeproj.PBXFileReference;
import org.gradle.ide.xcode.internal.xcodeproj.PBXLegacyTarget;
import org.gradle.ide.xcode.internal.xcodeproj.PBXNativeTarget;
import org.gradle.ide.xcode.internal.xcodeproj.PBXProject;
import org.gradle.ide.xcode.internal.xcodeproj.PBXReference;
import org.gradle.ide.xcode.internal.xcodeproj.PBXSourcesBuildPhase;
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget;
import org.gradle.ide.xcode.internal.xcodeproj.XCBuildConfiguration;
import org.gradle.ide.xcode.internal.xcodeproj.XcodeprojSerializer;
import org.gradle.ide.xcode.tasks.internal.XcodeProjectFile;
import org.gradle.plugins.ide.api.PropertyListGeneratorTask;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Task for generating a project file.
 *
 * @since 4.2
 */
@Incubating
public class GenerateXcodeProjectFileTask extends PropertyListGeneratorTask<XcodeProjectFile> {
    private static final String PRODUCTS_GROUP_NAME = "Products";
    private DefaultXcodeProject xcodeProject;
    private Map<String, PBXFileReference> pathToFileReference = new HashMap<String, PBXFileReference>();

    @Override
    protected void configure(XcodeProjectFile projectFile) {
        PBXProject project = new PBXProject(getProject().getName());

        // Required for making think the project isn't corrupted...
        XCBuildConfiguration buildConfiguration = project.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked("Debug");
        buildConfiguration.setBuildSettings(new NSDictionary());

        for (File source : xcodeProject.getSources()) {
            PBXFileReference fileReference = new PBXFileReference(source.getName(), source.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE);
            pathToFileReference.put(source.getAbsolutePath(), fileReference);
            project.getMainGroup().getChildren().add(fileReference);
        }

        for (XcodeTarget target : xcodeProject.getTargets()) {
            project.getTargets().add(toPbxTarget(target));

            if (target instanceof GradleBuildTarget) {
                PBXFileReference fileReference = new PBXFileReference(target.getOutputFile().getName(), target.getOutputFile().getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE);
                fileReference.setExplicitFileType(Optional.of(target.getOutputFileType()));
                project.getMainGroup().getOrCreateChildGroupByName(PRODUCTS_GROUP_NAME).getChildren().add(fileReference);
            }
        }

        XcodeprojSerializer serializer = new XcodeprojSerializer(new GidGenerator(Collections.<String>emptySet()), project);
        final NSDictionary rootObject = serializer.toPlist();

        projectFile.transformAction(new Action<NSDictionary>() {
            @Override
            public void execute(NSDictionary dict) {
                dict.clear();
                dict.putAll(rootObject);
            }
        });
    }

    @Override
    protected XcodeProjectFile create() {
        return new XcodeProjectFile(getPropertyListTransformer());
    }

    private PBXTarget toPbxTarget(XcodeTarget target) {
        if (target instanceof IndexingSwiftTarget) {
            return toPbxTarget((IndexingSwiftTarget) target);
        } else if (target instanceof GradleBuildTarget) {
            return toPbxTarget((GradleBuildTarget) target);
        }

        throw new IllegalArgumentException("XCode target need to be of type XcodeIndexingTarget or XcodeGradleTarget");
    }

    private PBXTarget toPbxTarget(GradleBuildTarget xcodeTarget) {
        PBXLegacyTarget target = new PBXLegacyTarget(xcodeTarget.getName(), xcodeTarget.getProductType());
        target.setProductName(xcodeTarget.getProductName());

        NSDictionary buildSettings = new NSDictionary();

        target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked("Debug").setBuildSettings(buildSettings);
        target.setBuildToolPath(xcodeTarget.getGradleCommand());
        target.setBuildArgumentsString(xcodeTarget.getTaskName());
        target.setGlobalID(xcodeTarget.getId());

        return target;
    }

    private PBXTarget toPbxTarget(IndexingSwiftTarget xcodeTarget) {
        PBXSourcesBuildPhase buildPhase = new PBXSourcesBuildPhase();
        for (File file : xcodeTarget.getSources()) {
            PBXFileReference fileReference = pathToFileReference.get(file.getAbsolutePath());
            buildPhase.getFiles().add(new PBXBuildFile(fileReference));
        }

        PBXNativeTarget target = new PBXNativeTarget(xcodeTarget.getName(), xcodeTarget.getProductType());
        target.setProductName(xcodeTarget.getProductName());

        NSDictionary buildSettings = new NSDictionary();
        buildSettings.put("SWIFT_VERSION", "3.0");  // TODO - Choose the right version for swift
        buildSettings.put("PRODUCT_NAME", xcodeTarget.getProductName());  // Mandatory

        target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked("Debug").setBuildSettings(buildSettings);
        target.getBuildPhases().add(buildPhase);

        return target;
    }

    @Internal
    public XcodeProject getXcodeProject() {
        return xcodeProject;
    }

    public void setXcodeProject(XcodeProject xcodeProject) {
        this.xcodeProject = (DefaultXcodeProject) xcodeProject;
    }
}
