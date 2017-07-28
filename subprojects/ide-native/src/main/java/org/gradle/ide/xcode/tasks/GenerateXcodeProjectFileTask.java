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
import org.gradle.ide.xcode.internal.XcodeTarget;
import org.gradle.ide.xcode.internal.xcodeproj.GidGenerator;
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

import javax.inject.Inject;
import java.io.File;
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

        // Required for making think the project isn't corrupted...
        XCBuildConfiguration buildConfiguration = project.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked("Debug");
        buildConfiguration.setBuildSettings(new NSDictionary());

        for (File source : xcodeProject.getSources()) {
            PBXFileReference fileReference = toFileReference(source);
            pathToFileReference.put(source.getAbsolutePath(), fileReference);
            project.getMainGroup().getChildren().add(fileReference);
        }

        XcodeTarget target = xcodeProject.getTarget();
        if (target != null) {
            project.getTargets().add(toGradlePbxTarget(target));
            project.getTargets().add(toIndexPbxTarget(target));

            PBXFileReference fileReference = new PBXFileReference(target.getOutputFile().getName(), target.getOutputFile().getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE);
            fileReference.setExplicitFileType(Optional.of(target.getOutputFileType()));
            project.getMainGroup().getOrCreateChildGroupByName(PRODUCTS_GROUP_NAME).getChildren().add(fileReference);
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

    @Override
    protected XcodeProjectFile create() {
        return new XcodeProjectFile(getPropertyListTransformer());
    }

    private PBXFileReference toFileReference(File file) {
        return new PBXFileReference(file.getName(), file.getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE);
    }

    private PBXTarget toGradlePbxTarget(XcodeTarget xcodeTarget) {
        PBXLegacyTarget target = new PBXLegacyTarget(xcodeTarget.getName(), xcodeTarget.getProductType());
        target.setProductName(xcodeTarget.getProductName());

        NSDictionary buildSettings = new NSDictionary();

        target.getBuildConfigurationList().getBuildConfigurationsByName().getUnchecked("Debug").setBuildSettings(buildSettings);
        target.setBuildToolPath(xcodeTarget.getGradleCommand());
        target.setBuildArgumentsString(xcodeTarget.getTaskName());
        target.setGlobalID(xcodeTarget.getId());
        target.setProductReference(new PBXFileReference(xcodeTarget.getOutputFile().getName(), xcodeTarget.getOutputFile().getAbsolutePath(), PBXReference.SourceTree.ABSOLUTE));

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
        buildSettings.put("HEADER_SEARCH_PATHS", toList(xcodeTarget.getHeaderSearchPaths()));
        buildSettings.put("SWIFT_INCLUDE_PATHS", toList(xcodeTarget.getImportPaths()));

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

    public static String toList(Iterable<File> it) {
        StringBuilder result = new StringBuilder();
        for (File file : it) {
            result.append("\"" + file.getAbsolutePath() + "\" ");
        }
        return result.toString();
    }
}
