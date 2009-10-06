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
