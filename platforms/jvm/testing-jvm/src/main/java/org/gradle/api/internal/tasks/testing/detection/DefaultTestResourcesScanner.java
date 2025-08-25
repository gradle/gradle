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

package org.gradle.api.internal.tasks.testing.detection;

import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.ReproducibleFileVisitor;
import org.gradle.api.internal.file.RelativeFile;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformResourceBasedDetector;

public final class DefaultTestResourcesScanner implements TestDetector {
    private final FileTree testResourceFiles;
    private final JUnitPlatformResourceBasedDetector testDetector;
    private final TestClassProcessor testClassProcessor;

    public DefaultTestResourcesScanner(FileTree testResourceFiles, JUnitPlatformResourceBasedDetector testDetector, TestClassProcessor testClassProcessor) {
        this.testDetector = testDetector;
        this.testClassProcessor = testClassProcessor;
        this.testResourceFiles = testResourceFiles;
    }

    @Override
    public void detect() {
        detectionScan();
    }

    private void detectionScan() {
        testDetector.startDetection(testClassProcessor);
        testResourceFiles.visit(new ResourceFileVisitor());
    }

    private class ResourceFileVisitor extends EmptyFileVisitor implements ReproducibleFileVisitor {
        @Override
        public void visitFile(FileVisitDetails fileDetails) {
            if (isTestResource(fileDetails)) {
                visitResourceFile(fileDetails);
            }
        }

        public boolean isTestResource(FileVisitDetails fileVisitDetails) {
            return testDetector.isTestResource(fileVisitDetails);
        }

        public void visitResourceFile(FileVisitDetails fileDetails) {
            testDetector.processTestClass(new RelativeFile(fileDetails.getFile(), fileDetails.getRelativePath()));
        }

        @Override
        public boolean isReproducibleFileOrder() {
            return true;
        }
    }
}
