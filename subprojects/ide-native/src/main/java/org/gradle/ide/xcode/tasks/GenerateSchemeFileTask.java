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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Internal;
import org.gradle.ide.xcode.XcodeProject;
import org.gradle.ide.xcode.internal.DefaultXcodeProject;
import org.gradle.ide.xcode.internal.XcodeTarget;
import org.gradle.ide.xcode.tasks.internal.XcodeSchemeFile;
import org.gradle.plugins.ide.api.XmlGeneratorTask;

import java.io.File;

import static org.gradle.ide.xcode.internal.DefaultXcodeProject.BUILD_DEBUG;
import static org.gradle.ide.xcode.internal.DefaultXcodeProject.TEST_DEBUG;

/**
 * Task for generating a scheme file.
 *
 * @since 4.2
 */
@Incubating
public class GenerateSchemeFileTask extends XmlGeneratorTask<XcodeSchemeFile> {
    public DefaultXcodeProject xcodeProject;

    @Internal
    public XcodeProject getXcodeProject() {
        return xcodeProject;
    }

    public void setXcodeProject(XcodeProject xcodeProject) {
        this.xcodeProject = (DefaultXcodeProject) xcodeProject;
    }

    @Override
    protected void configure(XcodeSchemeFile schemeFile) {
        configureBuildAction(schemeFile.getBuildAction());
        configureTestAction(schemeFile.getTestAction());
        configureLaunchAction(schemeFile.getLaunchAction());
        configureArchiveAction(schemeFile.getArchiveAction());
        configureAnalyzeAction(schemeFile.getAnalyzeAction());
        configureProfileAction(schemeFile.getProfileAction());
    }

    private void configureBuildAction(XcodeSchemeFile.BuildAction action) {
        for (final XcodeTarget xcodeTarget : xcodeProject.getTargets()) {
            action.entry(new Action<XcodeSchemeFile.BuildActionEntry>() {
                @Override
                public void execute(XcodeSchemeFile.BuildActionEntry buildActionEntry) {
                    buildActionEntry.setBuildForAnalysing(!xcodeTarget.isUnitTest());
                    buildActionEntry.setBuildForArchiving(!xcodeTarget.isUnitTest());
                    buildActionEntry.setBuildForProfiling(!xcodeTarget.isUnitTest());
                    buildActionEntry.setBuildForRunning(!xcodeTarget.isUnitTest());
                    buildActionEntry.setBuildForTesting(xcodeTarget.isUnitTest());
                    buildActionEntry.setBuildableReference(toBuildableReference(xcodeTarget));
                }
            });
        }
    }

    private void configureTestAction(XcodeSchemeFile.TestAction action) {
        action.setBuildConfiguration(BUILD_DEBUG);

        for (final XcodeTarget xcodeTarget : xcodeProject.getTargets()) {
            if (xcodeTarget.isUnitTest()) {
                action.setBuildConfiguration(TEST_DEBUG);
                action.entry(new Action<XcodeSchemeFile.TestableEntry>() {
                    @Override
                    public void execute(XcodeSchemeFile.TestableEntry testableEntry) {
                        testableEntry.setSkipped(false);
                        XcodeSchemeFile.BuildableReference buildableReference = toBuildableReference(xcodeTarget);
                        testableEntry.setBuildableReference(buildableReference);
                    }
                });
            }
        }
    }

    private void configureLaunchAction(XcodeSchemeFile.LaunchAction action) {
        action.setBuildConfiguration(BUILD_DEBUG);
        for (XcodeTarget xcodeTarget : xcodeProject.getTargets()) {
            XcodeSchemeFile.BuildableReference buildableReference = toBuildableReference(xcodeTarget);
            if (xcodeTarget.isRunnable()) {
                action.setBuildableProductRunnable(buildableReference);
            }
            action.setBuildableReference(buildableReference);
            break;
        }
    }

    private void configureArchiveAction(XcodeSchemeFile.ArchiveAction action) {
        action.setBuildConfiguration(BUILD_DEBUG);
    }

    private void configureProfileAction(XcodeSchemeFile.ProfileAction action) {
        action.setBuildConfiguration(BUILD_DEBUG);
    }

    private void configureAnalyzeAction(XcodeSchemeFile.AnalyzeAction action) {
        action.setBuildConfiguration(BUILD_DEBUG);
    }

    @Override
    public File getInputFile() {
        return null;
    }

    @Override
    protected XcodeSchemeFile create() {
        return new XcodeSchemeFile(getXmlTransformer());
    }

    private XcodeSchemeFile.BuildableReference toBuildableReference(XcodeTarget target) {
        XcodeSchemeFile.BuildableReference buildableReference = new XcodeSchemeFile.BuildableReference();
        buildableReference.setBuildableIdentifier("primary");
        buildableReference.setBlueprintIdentifier(target.getId());
        buildableReference.setBuildableName(target.getProductName());
        buildableReference.setBlueprintName(target.getName());
        buildableReference.setContainerRelativePath(getProject().getName() + ".xcodeproj");

        return buildableReference;
    }
}
