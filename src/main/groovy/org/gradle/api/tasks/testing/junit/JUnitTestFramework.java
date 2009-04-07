/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.tasks.testing.junit;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.tasks.testing.AbstractTestFramework;
import org.gradle.api.tasks.testing.ForkMode;
import org.gradle.api.tasks.testing.JunitForkOptions;
import org.gradle.api.tasks.testing.Test;

import java.util.Set;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestFramework extends AbstractTestFramework {
    private AntJUnitExecute antJUnitExecute = null;
    private AntJUnitReport antJUnitReport = null;
    private JUnitOptions options = null;

    public JUnitTestFramework() {
        super("JUnit");
    }

    public void initialize(Project project, Test testTask) {
        antJUnitExecute = new AntJUnitExecute();
        antJUnitReport = new AntJUnitReport();
        options = new JUnitOptions(this);

        final Set<Class<? extends Plugin>> appliedPlugins = project.getAppliedPlugins();

        final JunitForkOptions forkOptions = options.getForkOptions();

        // only fork per test if no tests are implemented in Groovy
        if ( appliedPlugins != null && !appliedPlugins.contains(GroovyPlugin.class) ) {
            options.setFork(true);
            forkOptions.setForkMode(ForkMode.PER_TEST);
            forkOptions.setDir(project.getProjectDir());
        }
        else {
            forkOptions.setForkMode(ForkMode.ONCE);
        }
    }

    public void execute(Project project, Test testTask) {
        configureDefaultIncludesExcludes(project, testTask);

        antJUnitExecute.execute(testTask.getTestClassesDir(), testTask.getClasspath(), testTask.getTestResultsDir(), testTask.getIncludes(),
                testTask.getExcludes(), options, project.getAnt());

    }

    public void report(Project project, Test testTask)
    {
        antJUnitReport.execute(testTask.getTestResultsDir(), testTask.getTestReportDir(), project.getAnt());
    }

    public JUnitOptions getOptions() {
        return options;
    }

    void setOptions(JUnitOptions options) {
        this.options = options;
    }

    AntJUnitExecute getAntJUnitExecute() {
        return antJUnitExecute;
    }

    void setAntJUnitExecute(AntJUnitExecute antJUnitExecute) {
        this.antJUnitExecute = antJUnitExecute;
    }

    AntJUnitReport getAntJUnitReport() {
        return antJUnitReport;
    }

    void setAntJUnitReport(AntJUnitReport antJUnitReport) {
        this.antJUnitReport = antJUnitReport;
    }


}
