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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.testng.AntTestNGExecute;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.api.testing.fabric.AbstractTestFrameworkInstance;
import org.gradle.util.exec.ExecHandleBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestFrameworkInstance extends AbstractTestFrameworkInstance<TestNGTestFramework> {

    private AntTestNGExecute antTestNGExecute;
    private TestNGOptions options;
    private TestNGDetector detector;

    protected TestNGTestFrameworkInstance(AbstractTestTask testTask, TestNGTestFramework testFramework) {
        super(testTask, testFramework);
    }

    public void initialize(Project project, AbstractTestTask testTask) {
        antTestNGExecute = new AntTestNGExecute();
        options = new TestNGOptions(testFramework, project.getProjectDir());

        options.setAnnotationsOnSourceCompatibility(JavaVersion.toVersion(project.property("sourceCompatibility")));

        detector = new TestNGDetector(testTask.getTestClassesDir(), testTask.getClasspath());
    }

    public void execute(Project project, AbstractTestTask testTask, Collection<String> includes, Collection<String> excludes) {
        options.setTestResources(testTask.getTestSrcDirs());

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

    public void report(Project project, AbstractTestTask testTask) {
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

    public TestNGDetector getDetector() {
        return detector;
    }

    public void applyForkArguments(ExecHandleBuilder forkHandleBuilder) {

        if (StringUtils.isNotEmpty(options.getJvm())) {
            forkHandleBuilder.execCommand(options.getJvm());
        } else {
            useDefaultJvm(forkHandleBuilder);
        }

        useDefaultDirectory(forkHandleBuilder);
    }

    public void applyForkJvmArguments(ExecHandleBuilder forkHandleBuilder) {
        final List<String> jvmArgs = options.getJvmArgs();
        if (jvmArgs != null && !jvmArgs.isEmpty()) {
            forkHandleBuilder.arguments(jvmArgs);
        }

        final Map<String, String> environment = options.getEnvironment();
        if (environment != null && !environment.isEmpty())
            forkHandleBuilder.environment(environment);
    }
}
