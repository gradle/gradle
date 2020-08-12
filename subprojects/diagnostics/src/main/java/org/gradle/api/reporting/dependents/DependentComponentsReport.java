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

package org.gradle.api.reporting.dependents;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.reporting.dependents.internal.TextDependentComponentsReportRenderer;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

import static org.gradle.api.reporting.dependents.internal.DependentComponentsUtils.getAllComponents;
import static org.gradle.api.reporting.dependents.internal.DependentComponentsUtils.getAllTestSuites;

/**
 * Displays dependent components.
 */
@Incubating
public class DependentComponentsReport extends DefaultTask {

    private boolean showNonBuildable;
    private boolean showTestSuites;
    private List<String> components;

    /**
     * Should this include non-buildable components in the report?
     */
    @Console
    public boolean isShowNonBuildable() {
        return showNonBuildable;
    }

    @Option(option = "non-buildable", description = "Show non-buildable components.")
    public void setShowNonBuildable(boolean showNonBuildable) {
        this.showNonBuildable = showNonBuildable;
    }

    /**
     * Should this include test suites in the report?
     */
    @Console
    public boolean isShowTestSuites() {
        return showTestSuites;
    }

    @Option(option = "test-suites", description = "Show test suites components.")
    public void setShowTestSuites(boolean showTestSuites) {
        this.showTestSuites = showTestSuites;
    }

    /**
     * Should this include both non-buildable and test suites in the report?
     */
    @Console
    public boolean getShowAll() {
        return showNonBuildable && showTestSuites;
    }

    /**
     * Set this to include both non buildable components and test suites in the report.
     */
    @Option(option = "all", description = "Show all components (non-buildable and test suites).")
    public void setShowAll(boolean showAll) {
        this.showNonBuildable = showAll;
        this.showTestSuites = showAll;
    }

    /**
     * Returns the components to generate the report for.
     * Defaults to all components of this project.
     *
     * @return the components.
     */
    @Console
    public List<String> getComponents() {
        return components;
    }

    /**
     * Sets the components to generate the report for.
     *
     * @param components the components.
     */
    @Option(option = "component", description = "Component to generate the report for (can be specified more than once).")
    public void setComponents(List<String> components) {
        this.components = components;
    }

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ModelRegistry getModelRegistry() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected WorkerLeaseService getWorkerLeaseService() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void report() {
        // Once we are here, the project lock is held. If we synchronize to avoid cross-project operations, we will have a dead lock.
        getWorkerLeaseService().withoutProjectLock(() -> {
            // Output reports per execution, not mixed.
            // Cross-project ModelRegistry operations do not happen concurrently.
            synchronized (DependentComponentsReport.class) {
                ((ProjectInternal) getProject()).getMutationState().applyToMutableState(project -> {
                    ModelRegistry modelRegistry = getModelRegistry();

                    DependentBinariesResolver dependentBinariesResolver = modelRegistry.find("dependentBinariesResolver", DependentBinariesResolver.class);

                    StyledTextOutput textOutput = getTextOutputFactory().create(DependentComponentsReport.class);
                    TextDependentComponentsReportRenderer reportRenderer = new TextDependentComponentsReportRenderer(dependentBinariesResolver, showNonBuildable, showTestSuites);

                    reportRenderer.setOutput(textOutput);
                    reportRenderer.startProject(project);

                    Set<ComponentSpec> allComponents = getAllComponents(modelRegistry);
                    if (showTestSuites) {
                        allComponents.addAll(getAllTestSuites(modelRegistry));
                    }
                    reportRenderer.renderComponents(getReportedComponents(allComponents));
                    reportRenderer.renderLegend();

                    reportRenderer.completeProject(project);

                    reportRenderer.complete();
                });
            }
        });
    }

    private Set<ComponentSpec> getReportedComponents(Set<ComponentSpec> allComponents) {
        if (components == null || components.isEmpty()) {
            return allComponents;
        }
        Set<ComponentSpec> reportedComponents = Sets.newLinkedHashSet();
        List<String> notFound = Lists.newArrayList(components);
        for (ComponentSpec candidate : allComponents) {
            String candidateName = candidate.getName();
            if (components.contains(candidateName)) {
                reportedComponents.add(candidate);
                notFound.remove(candidateName);
            }
        }
        if (!notFound.isEmpty()) {
            onComponentsNotFound(notFound);
        }
        return reportedComponents;
    }

    private void onComponentsNotFound(List<String> notFound) {
        StringBuilder error = new StringBuilder("Component");
        if (notFound.size() == 1) {
            error.append(" '").append(notFound.get(0));
        } else {
            String last = notFound.remove(notFound.size() - 1);
            error.append("s '").append(Joiner.on("', '").join(notFound)).append("' and '").append(last);
        }
        error.append("' not found.");
        throw new InvalidUserDataException(error.toString());
    }
}
