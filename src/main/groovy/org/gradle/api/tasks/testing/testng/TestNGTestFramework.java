package org.gradle.api.tasks.testing.testng;

import org.gradle.api.Project;
import org.gradle.api.JavaVersion;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.testing.Test;

import org.gradle.api.tasks.testing.AbstractTestFramework;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestFramework extends AbstractTestFramework {
    private AntTestNGExecute antTestNGExecute;
    private TestNGOptions options;

    public TestNGTestFramework() {
        super("TestNG");
    }

    public void initialize(Project project, Test testTask) {
        antTestNGExecute = new AntTestNGExecute();
        options = new TestNGOptions(this, project.getProjectDir());

        options.setAnnotationsOnSourceCompatibility(JavaVersion.toVersion(project.property("sourceCompatibility")));
    }

    public void execute(Project project, Test testTask) {
        options.setTestResources(testTask.getTestSrcDirs());

        configureDefaultIncludesExcludes(project, testTask);

        antTestNGExecute.execute(
                testTask.getTestClassesDir(),
                testTask.getClasspath(),
                testTask.getTestResultsDir(),
                testTask.getTestReportDir(),
                testTask.getIncludes(),
                testTask.getExcludes(),
                options,
                project.getAnt(),
                testTask.isTestReport());
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
}
