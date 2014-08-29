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
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.test.TestSuiteContainer;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecContainer;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;

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

    @Inject
    protected ModelRegistry getModelRegistry() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void report() {
        Project project = getProject();

        StyledTextOutput textOutput = getTextOutputFactory().create(ComponentReport.class);
        ComponentReportRenderer renderer = new ComponentReportRenderer(getFileResolver());
        renderer.setOutput(textOutput);

        renderer.startProject(project);

        Collection<ComponentSpec> components = new ArrayList<ComponentSpec>();
        ComponentSpecContainer componentSpecs = project.getExtensions().findByType(ComponentSpecContainer.class);
        if (componentSpecs != null) {
            components.addAll(componentSpecs);
        }

        try {
            TestSuiteContainer testSuites = getModelRegistry().get(ModelPath.path("testSuites"), ModelType.of(TestSuiteContainer.class));
            components.addAll(testSuites);
        } catch (IllegalStateException e) {
            // TODO - need a better contract here
            // Ignore for now
        }

        renderer.renderComponents(components);

        ProjectSourceSet sourceSets = project.getExtensions().findByType(ProjectSourceSet.class);
        if (sourceSets != null) {
            renderer.renderSourceSets(sourceSets);
        }
        BinaryContainer binaries = project.getExtensions().findByType(BinaryContainer.class);
        if (binaries != null) {
            renderer.renderBinaries(binaries);
        }

        renderer.completeProject(project);
        renderer.complete();
    }
}
