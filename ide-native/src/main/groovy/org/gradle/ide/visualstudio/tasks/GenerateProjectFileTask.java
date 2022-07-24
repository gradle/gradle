/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.ide.visualstudio.tasks;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Transformer;
import org.gradle.api.XmlProvider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject;
import org.gradle.ide.visualstudio.internal.VisualStudioProjectConfiguration;
import org.gradle.ide.visualstudio.tasks.internal.RelativeFileNameTransformer;
import org.gradle.ide.visualstudio.tasks.internal.VisualStudioProjectFile;
import org.gradle.plugins.ide.api.XmlGeneratorTask;
import org.gradle.plugins.ide.internal.IdePlugin;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * Task for generating a Visual Studio project file (e.g. {@code foo.vcxproj}).
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
public class GenerateProjectFileTask extends XmlGeneratorTask<VisualStudioProjectFile> {
    private DefaultVisualStudioProject visualStudioProject;
    private String gradleExe;
    private String gradleArgs;

    @Override
    protected boolean getIncremental() {
        return true;
    }

    public void initGradleCommand() {
        final File gradlew = new File(IdePlugin.toGradleCommand(getProject()));
        getConventionMapping().map("gradleExe", new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                final String rootDir = getTransformer().transform(getProject().getRootDir());
                String args = "";
                if (!rootDir.equals(".")) {
                    args = " -p \"" + rootDir + "\"";
                }

                if (gradlew.isFile()) {
                    return "\"" + getTransformer().transform(gradlew) + "\"" + args;
                }

                return "\"gradle\"" + args;
            }
        });
    }

    @Internal
    public Transformer<String, File> getTransformer() {
        return RelativeFileNameTransformer.forFile(getProject().getRootDir(), visualStudioProject.getProjectFile().getLocation());
    }

    public void setVisualStudioProject(VisualStudioProject vsProject) {
        this.visualStudioProject = (DefaultVisualStudioProject) vsProject;
    }

    @Nested
    public VisualStudioProject getVisualStudioProject() {
        return visualStudioProject;
    }

    @Override
    @Internal
    public File getInputFile() {
        return null;
    }

    @Override
    @OutputFile
    public File getOutputFile() {
        return visualStudioProject.getProjectFile().getLocation();
    }

    @Override
    protected VisualStudioProjectFile create() {
        return new VisualStudioProjectFile(getXmlTransformer(), getTransformer());
    }

    @Override
    protected void configure(VisualStudioProjectFile projectFile) {
        DefaultVisualStudioProject vsProject = visualStudioProject;
        projectFile.setGradleCommand(buildGradleCommand());
        projectFile.setProjectUuid(DefaultVisualStudioProject.getUUID(getOutputFile()));
        projectFile.setVisualStudioVersion(visualStudioProject.getVisualStudioVersion().get());
        projectFile.setSdkVersion(visualStudioProject.getSdkVersion().get());

        for (File sourceFile : vsProject.getSourceFiles()) {
            projectFile.addSourceFile(sourceFile);
        }

        for (File resourceFile : vsProject.getResourceFiles()) {
            projectFile.addResource(resourceFile);
        }

        for (File headerFile : vsProject.getHeaderFiles()) {
            projectFile.addHeaderFile(headerFile);
        }

        if (vsProject.getConfigurations().stream().noneMatch(it -> it.isBuildable())) {
            getLogger().warn("'" + vsProject.getComponentName() + "' component in project '" + getProject().getPath() + "' is not buildable.");
        }
        for (VisualStudioProjectConfiguration configuration : vsProject.getConfigurations()) {
            projectFile.addConfiguration(configuration);
        }

        for (Action<? super XmlProvider> xmlAction : vsProject.getProjectFile().getXmlActions()) {
            getXmlTransformer().addAction(xmlAction);
        }
    }

    private String buildGradleCommand() {
        String exe = getGradleExe();
        String args = getGradleArgs();
        if (args == null || args.trim().length() == 0) {
            return exe;
        } else {
            return exe + " " + args.trim();
        }

    }

    @Input
    public String getGradleExe() {
        return gradleExe;
    }

    public void setGradleExe(String gradleExe) {
        this.gradleExe = gradleExe;
    }

    @Nullable
    @Optional
    @Input
    public String getGradleArgs() {
        return gradleArgs;
    }

    public void setGradleArgs(@Nullable String gradleArgs) {
        this.gradleArgs = gradleArgs;
    }
}
