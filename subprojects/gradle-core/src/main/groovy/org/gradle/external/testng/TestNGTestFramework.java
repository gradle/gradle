/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.external.testng;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.AbstractTestFramework;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.testng.AntTestNGExecute;
import org.gradle.api.tasks.testing.testng.TestNGOptions;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestFramework extends AbstractTestFramework {
    private AntTestNGExecute antTestNGExecute;
    private TestNGOptions options;
    private TestNGDetector detector;

    public TestNGTestFramework() {
        super("TestNG");
    }

    public void initialize(Project project, Test testTask) {
        antTestNGExecute = new AntTestNGExecute();
        options = new TestNGOptions(this, project.getProjectDir());

        options.setAnnotationsOnSourceCompatibility(JavaVersion.toVersion(project.property("sourceCompatibility")));
    }

    public void prepare(Project project, Test testTask) {
        detector = new TestNGDetector(testTask.getTestClassesDir(), testTask.getClasspath());
    }

    public void execute(Project project, Test testTask, Collection<String> includes, Collection<String> excludes) {
        options.setTestResources(testTask.getTestSrcDirs());

        if ( !testTask.isTestReport() )
            options.setUseDefaultListeners(false);

        antTestNGExecute.execute(
                testTask.getTestClassesDir(),
                testTask.getClasspath(),
                testTask.getTestResultsDir(),
                testTask.getTestReportDir(),
                includes,
                excludes,
                options,
                project.getAnt());
    }

    public void report(Project project, Test testTask) {
        // TODO currently reports are always generated because the antTestNGExecute task uses the
        // default listeners and these generate reports by default.
    }

    public TestNGOptions getOptions() {
        return options;
    }

    void setOptions(TestNGOptions options) {
        this.options = options;
    }

    AntTestNGExecute getAntTestNGExecute() {
        return antTestNGExecute;
    }

    void setAntTestNGExecute(AntTestNGExecute antTestNGExecute) {
        this.antTestNGExecute = antTestNGExecute;
    }

    public boolean isTestClass(File testClassFile) {
        return detector.processPossibleTestClass(testClassFile);
    }

    public Set<String> getTestClassNames() {
        return detector.getTestClassNames();
    }
}
