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
package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.internal.tasks.testing.detection.AbstractTestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;
import org.gradle.api.internal.tasks.testing.detection.TestClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class JUnitDetector extends AbstractTestFrameworkDetector<JUnitTestClassDetecter> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JUnitDetector.class);

    public JUnitDetector(ClassFileExtractionManager classFileExtractionManager) {
        super(classFileExtractionManager);
    }

    protected JUnitTestClassDetecter createClassVisitor() {
        return new JUnitTestClassDetecter(this);
    }

    protected boolean processTestClass(final File testClassFile, boolean superClass) {
        final TestClassVisitor classVisitor = classVisitor(testClassFile);

        boolean isTest = classVisitor.isTest();

        if (!isTest) { // scan parent class
            final String superClassName = classVisitor.getSuperClassName();

            if (isKnownTestCaseClassName(superClassName)) {
                isTest = true;
            } else {
                final File superClassFile = getSuperTestClassFile(superClassName);

                if (superClassFile != null) {
                    isTest = processSuperClass(superClassFile);
                } else {
                    LOGGER.debug("test-class-scan : failed to scan parent class {}, could not find the class file",
                            superClassName);
                }
            }
        }

        publishTestClass(isTest, classVisitor, superClass);

        return isTest;
    }
}
