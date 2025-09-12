/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.RelativeFile;
import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.model.ObjectFactory;

import java.io.File;
import java.util.List;
import java.util.Set;

public class JUnitPlatformResourceBasedDetector implements TestFrameworkDetector {
    private final ConfigurableFileCollection testReourcesFiles;
    private TestClassProcessor testClassProcessor; // TODO: Resource processor?

    // TODO: Perhaps make this a service?  Get rid of need to pass ObjectFactory.
    public JUnitPlatformResourceBasedDetector(ObjectFactory objectFactory) {
        testReourcesFiles = objectFactory.fileCollection();
    }

    @Override
    public void startDetection(TestClassProcessor testClassProcessor) {
        this.testClassProcessor = testClassProcessor;
    }

    // TODO: need a "processTestResource" method, similar to "processTestClass" in ClassBasedTestDetector, in the hierarchy of test detectors
    @Override
    public boolean processTestClass(RelativeFile testClassFile) {
        // This method is called for each test resource file, which is a .rbt file.
        testClassProcessor.processTestClass(new DefaultTestClassRunInfo("Resource-Based tests from: " + testClassFile.getFile().getName()));
        return true;
    }

    @Override
    public void setTestClasses(List<File> testClasses) {

    }

    @Override
    public void setTestResources(Set<File> resourceFiles) {
        testReourcesFiles.setFrom(resourceFiles);
    }

    @Override
    public void setTestClasspath(List<File> classpath) {

    }

    // TODO: This would belong on an intermediate interface, e.g., ResourceBasedTestDetector.
    public boolean isTestResource(FileVisitDetails fileVisitDetails) {
        return !fileVisitDetails.isDirectory() && fileVisitDetails.getFile().getName().endsWith(".rbt"); // .rbt is arbitrary = Resource Based Test
    }
}
