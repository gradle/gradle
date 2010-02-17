/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.testng.TestNG;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TestNGTestClassProcessor implements TestClassProcessor {
    private final List<String> testClassNames = new ArrayList<String>();
    private final File testReportDir;
    private final TestNGOptions options;
    private final List<File> suiteFiles;
    private TestNGListenerAdapter listener;

    public TestNGTestClassProcessor(File testReportDir, TestNGOptions options, List<File> suiteFiles) {
        this.testReportDir = testReportDir;
        this.options = options;
        this.suiteFiles = suiteFiles;
    }

    public void startProcessing(TestResultProcessor resultProcessor) {
        listener = new TestNGListenerAdapter(resultProcessor);
    }

    public void processTestClass(TestClassRunInfo testClass) {
        testClassNames.add(testClass.getTestClassName());
    }

    public void endProcessing() {
        try {
            TestNG testNg = new TestNG();
            testNg.setOutputDirectory(testReportDir.getAbsolutePath());
            testNg.setDefaultSuiteName(options.getSuiteName());
            testNg.setDefaultTestName(options.getTestName());
            testNg.setAnnotations(options.getAnnotations());
            if (options.getJavadocAnnotations()) {
                testNg.setSourcePath(GUtil.join(options.getTestResources(), File.pathSeparator));
            }
            testNg.setUseDefaultListeners(options.getUseDefaultListeners());
            testNg.addListener(listener);
            testNg.setVerbose(0);

            if (!suiteFiles.isEmpty()) {
                testNg.setTestSuites(GFileUtils.toPaths(suiteFiles));
            } else {
                ClassLoader applicationClassLoader = Thread.currentThread().getContextClassLoader();
                Class[] classes = new Class[testClassNames.size()];
                for (int i = 0; i < testClassNames.size(); i++) {
                    String className = testClassNames.get(i);
                    classes[i] = applicationClassLoader.loadClass(className);
                }
                testNg.setTestClasses(classes);
            }

            testNg.run();
        } catch (Throwable throwable) {
            throw new GradleException("Could not execute tests.", throwable);
        }
    }
}
