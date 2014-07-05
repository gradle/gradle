/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.components;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.reporting.components.internal.ComponentReportRenderer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.nativebinaries.ProjectNativeComponent;
import org.gradle.runtime.base.ProjectComponent;
import org.gradle.runtime.base.ProjectComponentContainer;

import javax.inject.Inject;

/**
 * Displays some details about the software components produced by the project.
 */
@Incubating
public class ComponentReport extends DefaultTask {
    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void report() {
        Project project = getProject();
        StyledTextOutput textOutput = getTextOutputFactory().create(ComponentReport.class);
        ComponentReportRenderer renderer = new ComponentReportRenderer(getFileResolver());
        renderer.setOutput(textOutput);

        renderer.startProject(project);

        ProjectComponentContainer components = project.getExtensions().findByType(ProjectComponentContainer.class);
        if (components != null) {
            for (ProjectComponent component : components) {
                renderer.startComponent(component);
                for (LanguageSourceSet sourceSet : component.getSource()) {
                    renderer.renderSourceSet(sourceSet);
                }
                renderer.completeSourceSets();

                // TODO - hoist 'component with binaries' up and remove dependency on cpp project
                if (component instanceof ProjectNativeComponent) {
                    ProjectNativeComponent nativeComponent = (ProjectNativeComponent) component;
                    for (NativeBinary binary : nativeComponent.getBinaries()) {
                        renderer.renderBinary(binary);
                    }
                }
                renderer.completeBinaries();
            }
        }

        renderer.completeProject(project);
        renderer.complete();
    }
}
