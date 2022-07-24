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
import org.gradle.api.XmlProvider;
import org.gradle.api.tasks.Nested;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject;
import org.gradle.ide.visualstudio.tasks.internal.RelativeFileNameTransformer;
import org.gradle.ide.visualstudio.tasks.internal.VisualStudioFiltersFile;
import org.gradle.plugins.ide.api.XmlGeneratorTask;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

/**
 * Task for generating a Visual Studio filters file (e.g. {@code foo.vcxproj.filters}).
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
public class GenerateFiltersFileTask extends XmlGeneratorTask<VisualStudioFiltersFile> {
    private DefaultVisualStudioProject visualStudioProject;

    @Override
    protected boolean getIncremental() {
        return true;
    }

    public void setVisualStudioProject(VisualStudioProject vsProject) {
        this.visualStudioProject = (DefaultVisualStudioProject) vsProject;
    }

    @Nested
    public VisualStudioProject getVisualStudioProject() {
        return visualStudioProject;
    }

    @Override
    public File getInputFile() {
        return null;
    }

    @Override
    public File getOutputFile() {
        return visualStudioProject.getFiltersFile().getLocation();
    }

    @Override
    protected void configure(final VisualStudioFiltersFile filtersFile) {
        DefaultVisualStudioProject vsProject = visualStudioProject;

        for (File sourceFile : vsProject.getSourceFiles()) {
            filtersFile.addSource(sourceFile);
        }

        for (File headerFile : vsProject.getHeaderFiles()) {
            filtersFile.addHeader(headerFile);
        }

        for (Action<? super XmlProvider> xmlAction : vsProject.getFiltersFile().getXmlActions()) {
            getXmlTransformer().addAction(xmlAction);
        }
    }

    @Override
    protected VisualStudioFiltersFile create() {
        return new VisualStudioFiltersFile(getXmlTransformer(), RelativeFileNameTransformer.forFile(getProject().getRootDir(), visualStudioProject.getFiltersFile().getLocation()));
    }
}
