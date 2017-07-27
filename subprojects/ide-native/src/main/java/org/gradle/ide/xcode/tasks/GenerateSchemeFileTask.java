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
        action.entry(new Action<XcodeSchemeFile.BuildActionEntry>() {
            @Override
            public void execute(XcodeSchemeFile.BuildActionEntry buildActionEntry) {
                buildActionEntry.setBuildForAnalysing(true);
                buildActionEntry.setBuildForArchiving(true);
                buildActionEntry.setBuildForProfiling(true);
                buildActionEntry.setBuildForRunning(true);
                buildActionEntry.setBuildForTesting(true);
                buildActionEntry.setBuildableReference(toBuildableReference(xcodeProject.getTarget()));
            }
        });
    }

    private void configureTestAction(XcodeSchemeFile.TestAction action) {
        action.setBuildConfiguration("Debug");
    }

    private void configureLaunchAction(XcodeSchemeFile.LaunchAction action) {
        action.setBuildConfiguration("Debug");
        action.setRunnablePath(xcodeProject.getTarget().getOutputFile().getAbsolutePath());
    }

    private void configureArchiveAction(XcodeSchemeFile.ArchiveAction action) {
        action.setBuildConfiguration("Debug");
    }

    private void configureProfileAction(XcodeSchemeFile.ProfileAction action) {
        action.setBuildConfiguration("Debug");
    }

    private void configureAnalyzeAction(XcodeSchemeFile.AnalyzeAction action) {
        action.setBuildConfiguration("Debug");
    }

    @Override
    protected XcodeSchemeFile create() {
        return new XcodeSchemeFile(getXmlTransformer());
    }

    private XcodeSchemeFile.BuildableReference toBuildableReference(XcodeTarget target) {
        XcodeSchemeFile.BuildableReference buildableReference = new XcodeSchemeFile.BuildableReference();
        buildableReference.setBuildableIdentifier("primary");
        buildableReference.setBlueprintIdentifier(target.getId());
        buildableReference.setBuildableName(target.getName());
        buildableReference.setBlueprintName(target.getName());
        buildableReference.setContainerRelativePath(getProject().getName() + ".xcodeproj");

        return buildableReference;
    }
}
