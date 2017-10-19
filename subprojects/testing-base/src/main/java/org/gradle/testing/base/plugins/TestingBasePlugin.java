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

package org.gradle.testing.base.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.testing.AbstractTestTask;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Base plugin for testing.
 *
 * - Adds default locations for test reporting
 *
 * @since 4.4
 */
@Incubating
public class TestingBasePlugin implements Plugin<Project> {
    public static final String TEST_RESULTS_DIR_NAME = "test-results";
    public static final String TESTS_DIR_NAME = "tests";

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(ReportingBasePlugin.class);
        project.getTasks().withType(AbstractTestTask.class, new Action<AbstractTestTask>() {
            @Override
            public void execute(final AbstractTestTask test) {
                DslObject htmlReport = new DslObject(test.getReports().getHtml());
                DslObject xmlReport = new DslObject(test.getReports().getJunitXml());

                xmlReport.getConventionMapping().map("destination", new Callable<Object>() {
                    public Object call() throws Exception {
                        return getTestResultsDir(project, test);
                    }
                });
                htmlReport.getConventionMapping().map("destination", new Callable<Object>() {
                    public Object call() throws Exception {
                        return getTestReportsDir(project, test);
                    }
                });
                test.getConventionMapping().map("binResultsDir", new Callable<Object>() {
                    public Object call() throws Exception {
                        return new File(getTestResultsDir(project, test), "binary");
                    }
                });
            }
        });
    }

    private File getTestResultsDir(Project project, AbstractTestTask test) {
        return project.getLayout().getBuildDirectory().dir(TEST_RESULTS_DIR_NAME + "/" + test.getName()).get().getAsFile();
    }

    private File getTestReportsDir(Project project, AbstractTestTask test) {
        return new File(getReportsDir(project), TESTS_DIR_NAME + "/" + test.getName());
    }

    private File getReportsDir(Project project) {
        return project.getExtensions().getByType(ReportingExtension.class).getBaseDir();
    }
}
