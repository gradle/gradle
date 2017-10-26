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
import org.gradle.api.Transformer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.testing.AbstractTestTask;

import java.io.File;

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
    private static final Transformer<File, Directory> TO_FILE_TRANSFORMER = new Transformer<File, Directory>() {
        @Override
        public File transform(Directory directory) {
            return directory.getAsFile();
        }
    };

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(ReportingBasePlugin.class);
        project.getTasks().withType(AbstractTestTask.class, new Action<AbstractTestTask>() {
            @Override
            public void execute(final AbstractTestTask test) {
                test.getReports().getHtml().setDestination(getTestReportsDir(project, test).map(TO_FILE_TRANSFORMER));
                test.getReports().getJunitXml().setDestination(getTestResultsDir(project, test).map(TO_FILE_TRANSFORMER));
                test.getBinaryResultsDirectory().set(getTestResultsDir(project, test).map(new Transformer<Directory, Directory>() {
                    @Override
                    public Directory transform(Directory directory) {
                        return directory.dir("binary");
                    }
                }));
            }
        });
    }

    private Provider<Directory> getTestResultsDir(Project project, AbstractTestTask test) {
        return project.getLayout().getBuildDirectory().dir(TEST_RESULTS_DIR_NAME + "/" + test.getName());
    }

    private Provider<Directory> getTestReportsDir(Project project, final AbstractTestTask test) {
        DirectoryProperty baseDirectory = project.getExtensions().getByType(ReportingExtension.class).getBaseDirectory();
        return baseDirectory.dir(TESTS_DIR_NAME + "/" + test.getName());
    }
}
