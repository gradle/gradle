/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.plugins.HelpTasksPlugin;
import org.gradle.api.tasks.diagnostics.AbstractReportTask;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;

import javax.inject.Inject;
import java.io.IOException;

public class ShowToolchainsTask extends AbstractReportTask {

    private final ToolchainReportRenderer toolchainRenderer = new ToolchainReportRenderer();

    @Override
    protected ReportRenderer getRenderer() {
        return toolchainRenderer;
    }

    @Override
    protected void generate(Project project) throws IOException {
        toolchainRenderer.getTextOutput().println("Hello toolchains");
    }

    public static class ShowToolchainsTaskAction implements Action<ShowToolchainsTask> {

        private String projectName;

        public ShowToolchainsTaskAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(ShowToolchainsTask task) {
            task.setDescription("Displays the java toolchains available for " + projectName + ".");
            task.setGroup(HelpTasksPlugin.HELP_GROUP);
            task.setImpliesSubProjects(true);
        }
    }

    @Inject
    protected SharedJavaInstallationRegistry getInstallationRegistry() {
        throw new UnsupportedOperationException();
    }

}
