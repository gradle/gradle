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

package org.gradle.api.reporting.components.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.runtime.base.Binary;
import org.gradle.runtime.base.ProjectComponent;

import static org.gradle.logging.StyledTextOutput.Style.Info;

public class ComponentReportRenderer extends TextReportRenderer {
    private boolean hasComponents;
    private boolean hasSourceSets;
    private boolean hasBinaries;

    @Override
    public void startProject(Project project) {
        super.startProject(project);
        hasComponents = false;
    }

    @Override
    public void completeProject(Project project) {
        if (!hasComponents) {
            getTextOutput().withStyle(Info).println("No components");
        }

        super.completeProject(project);
    }

    public void startComponent(ProjectComponent component) {
        if (hasComponents) {
            getTextOutput().println();
        }
        writeSubheading(StringUtils.capitalize(component.getDisplayName()));
        hasComponents = true;
        hasSourceSets = false;
        hasBinaries = false;
    }

    public void renderSourceSet(LanguageSourceSet sourceSet) {
        if (!hasSourceSets) {
            getTextOutput().println().println("Source sets");
            hasSourceSets = true;
        }
        getTextOutput().formatln("    %s", StringUtils.capitalize(sourceSet.toString()));
    }

    public void completeSourceSets() {
        if (!hasSourceSets) {
            getTextOutput().println().withStyle(Info).println("No source sets");
        }
    }

    public void renderBinary(Binary binary) {
        if (!hasBinaries) {
            getTextOutput().println().println("Binaries");
            hasBinaries = true;
        }
        getTextOutput().formatln("    %s", StringUtils.capitalize(binary.getDisplayName()));
    }

    public void completeBinaries() {
        if (!hasBinaries) {
            getTextOutput().println().withStyle(Info).println("No binaries");
        }
    }
}
