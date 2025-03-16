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

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.ide.xcode.XcodeProject;
import org.gradle.ide.xcode.internal.DefaultXcodeProject;
import org.gradle.ide.xcode.internal.XcodeTarget;
import org.gradle.ide.xcode.tasks.internal.XcodeSchemeFile;
import org.gradle.internal.serialization.Cached;
import org.gradle.plugins.ide.api.XmlGeneratorTask;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.ide.xcode.internal.DefaultXcodeProject.BUILD_DEBUG;
import static org.gradle.ide.xcode.internal.DefaultXcodeProject.TEST_DEBUG;

/**
 * Task for generating a Xcode scheme file (e.g. {@code Foo.xcodeproj/xcshareddata/xcschemes/Foo.xcscheme}). An Xcode scheme defines a collection of targets to build, a configuration to use when building, and a collection of tests to execute.
 *
 * <p>You can have as many schemes as you want, but only one can be active at a time. You can include a scheme in a project—in which case it’s available in every workspace that includes that project, or in the workspace—in which case it’s available only in that workspace.</p>
 *
 * <p>This task is used in conjunction with {@link org.gradle.ide.xcode.tasks.GenerateXcodeProjectFileTask}.</p>
 *
 * @see org.gradle.ide.xcode.XcodeProject
 * @since 4.2
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class GenerateSchemeFileTask extends XmlGeneratorTask<XcodeSchemeFile> {
    private transient DefaultXcodeProject xcodeProject;
    private final Cached<SchemeFileSpec> spec = Cached.of(this::calculateSpec);

    @Internal
    public XcodeProject getXcodeProject() {
        return xcodeProject;
    }

    public void setXcodeProject(XcodeProject xcodeProject) {
        this.xcodeProject = (DefaultXcodeProject) xcodeProject;
    }

    private SchemeFileSpec calculateSpec() {
        List<TargetSpec> targets = new ArrayList<>();
        for (XcodeTarget target : xcodeProject.getTargets()) {
            targets.add(new TargetSpec(
                target.getName(),
                target.getId(),
                target.getProductName(),
                target.isRunnable(),
                target.isUnitTest(),
                target.getDefaultConfigurationName()
            ));
        }
        return new SchemeFileSpec(
            getProject().getName(),
            targets
        );
    }

    @Override
    protected void configure(XcodeSchemeFile schemeFile) {
        SchemeFileSpec spec = this.spec.get();
        configureBuildAction(spec, schemeFile.getBuildAction());
        configureTestAction(spec, schemeFile.getTestAction());
        configureLaunchAction(spec, schemeFile.getLaunchAction());
        configureArchiveAction(schemeFile.getArchiveAction());
        configureAnalyzeAction(schemeFile.getAnalyzeAction());
        configureProfileAction(schemeFile.getProfileAction());
    }

    private void configureBuildAction(SchemeFileSpec spec, XcodeSchemeFile.BuildAction action) {
        for (final TargetSpec xcodeTarget : spec.targets) {
            action.entry(buildActionEntry -> {
                buildActionEntry.setBuildForAnalysing(!xcodeTarget.unitTest);
                buildActionEntry.setBuildForArchiving(!xcodeTarget.unitTest);
                buildActionEntry.setBuildForProfiling(!xcodeTarget.unitTest);
                buildActionEntry.setBuildForRunning(!xcodeTarget.unitTest);
                buildActionEntry.setBuildForTesting(xcodeTarget.unitTest);
                buildActionEntry.setBuildableReference(toBuildableReference(spec, xcodeTarget));
            });
        }
    }

    private void configureTestAction(SchemeFileSpec spec, XcodeSchemeFile.TestAction action) {
        action.setBuildConfiguration(BUILD_DEBUG);

        for (final TargetSpec xcodeTarget : spec.targets) {
            if (xcodeTarget.unitTest) {
                action.setBuildConfiguration(TEST_DEBUG);
                action.entry(testableEntry -> {
                    testableEntry.setSkipped(false);
                    XcodeSchemeFile.BuildableReference buildableReference = toBuildableReference(spec, xcodeTarget);
                    testableEntry.setBuildableReference(buildableReference);
                });
            }
        }
    }

    private void configureLaunchAction(SchemeFileSpec spec, XcodeSchemeFile.LaunchAction action) {
        action.setBuildConfiguration(spec.targets.iterator().next().defaultConfigurationName.get());
        for (TargetSpec xcodeTarget : spec.targets) {
            XcodeSchemeFile.BuildableReference buildableReference = toBuildableReference(spec, xcodeTarget);
            if (xcodeTarget.runnable) {
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

    private XcodeSchemeFile.BuildableReference toBuildableReference(SchemeFileSpec spec, TargetSpec target) {
        XcodeSchemeFile.BuildableReference buildableReference = new XcodeSchemeFile.BuildableReference();
        buildableReference.setBuildableIdentifier("primary");
        buildableReference.setBlueprintIdentifier(target.id);
        buildableReference.setBuildableName(target.productName);
        buildableReference.setBlueprintName(target.name);
        buildableReference.setContainerRelativePath(spec.projectName + ".xcodeproj");

        return buildableReference;
    }

    private static class TargetSpec {
        final String name;
        final boolean runnable;
        final boolean unitTest;
        final String id;
        final String productName;
        final Provider<String> defaultConfigurationName;

        public TargetSpec(String name, String id, String productName, boolean runnable, boolean unitTest, Provider<String> defaultConfigurationName) {
            this.name = name;
            this.runnable = runnable;
            this.unitTest = unitTest;
            this.id = id;
            this.productName = productName;
            this.defaultConfigurationName = defaultConfigurationName;
        }
    }

    private static class SchemeFileSpec {
        final String projectName;
        final List<TargetSpec> targets;

        public SchemeFileSpec(String projectName, List<TargetSpec> targets) {
            this.projectName = projectName;
            this.targets = targets;
        }
    }
}
