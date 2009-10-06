package org.gradle.api.testing.fabric;

import org.gradle.api.Project;
import org.gradle.api.tasks.testing.AbstractTestFrameworkOptions;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.testing.detection.TestClassReceiver;
import org.gradle.util.exec.ExecHandleBuilder;

import java.io.File;
import java.util.Collection;

/**
 * @author Tom Eyckmans
 */
public interface TestFrameworkInstance<T extends TestFramework> {
    T getTestFramework();

    void initialize(Project project, AbstractTestTask testTask);

    void prepare(Project project, AbstractTestTask testTask, TestClassReceiver testClassReceiver);

    void execute(Project project, AbstractTestTask testTask, Collection<String> includes, Collection<String> excludes);

    void report(Project project, AbstractTestTask testTask);

    AbstractTestFrameworkOptions getOptions();

    boolean processPossibleTestClass(File testClassFile);

    void manualTestClass(String testClassName);

    void applyForkArguments(ExecHandleBuilder forkHandleBuilder);

    void applyForkJvmArguments(ExecHandleBuilder forkHandleBuilder);
}
