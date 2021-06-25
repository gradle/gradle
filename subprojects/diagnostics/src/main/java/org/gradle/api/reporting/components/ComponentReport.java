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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.reporting.components.internal.ComponentReportRenderer;
import org.gradle.api.reporting.components.internal.TypeAwareBinaryRenderer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;

import static org.gradle.model.internal.type.ModelTypes.modelMap;

/**
 * Displays some details about the software components produced by the project.
 */
@Deprecated
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
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

    @Inject
    protected TypeAwareBinaryRenderer getBinaryRenderer() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void report() {
        ProjectInternal project = (ProjectInternal) getProject();
        project.prepareForRuleBasedPlugins();

        StyledTextOutput textOutput = getTextOutputFactory().create(ComponentReport.class);
        ComponentReportRenderer renderer = new ComponentReportRenderer(getFileResolver(), getBinaryRenderer());
        renderer.setOutput(textOutput);

        ProjectDetails projectDetails = ProjectDetails.of(project);
        renderer.startProject(projectDetails);

        Collection<ComponentSpec> components = new ArrayList<>();
        ComponentSpecContainer componentSpecs = modelElement("components", ComponentSpecContainer.class);
        if (componentSpecs != null) {
            components.addAll(componentSpecs.values());
        }

        ModelMap<ComponentSpec> testSuites = modelElement("testSuites", modelMap(ComponentSpec.class));
        if (testSuites != null) {
            components.addAll(testSuites.values());
        }

        renderer.renderComponents(components);

        ProjectSourceSet sourceSets = modelElement("sources", ProjectSourceSet.class);
        if (sourceSets != null) {
            renderer.renderSourceSets(sourceSets);
        }
        BinaryContainer binaries = modelElement("binaries", BinaryContainer.class);
        if (binaries != null) {
            renderer.renderBinaries(binaries.values());
        }

        renderer.completeProject(projectDetails);
        renderer.complete();
    }

    private <T> T modelElement(String path, Class<T> clazz) {
        return getModelRegistry().find(path, clazz);
    }

    private <T> T modelElement(String path, ModelType<T> modelType) {
        return getModelRegistry().find(path, modelType);
    }
}
