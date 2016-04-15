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
package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.internal.tasks.testing.detection.AbstractTestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;
import org.gradle.api.internal.tasks.testing.detection.TestClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

class TestNGDetector extends AbstractTestFrameworkDetector<TestNGTestClassDetecter> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestNGDetector.class);

    TestNGDetector(ClassFileExtractionManager classFileExtractionManager) {
        super(classFileExtractionManager);
    }

    @Override
    protected TestNGTestClassDetecter createClassVisitor() {
        return new TestNGTestClassDetecter(this);
    }

    /**
     * Uses a TestClassVisitor to detect whether the class in the testClassFile is a test class. <p/> If the class is not a test, this function will go up the inheritance tree to check if a parent
     * class is a test class. First the package of the parent class is checked, if it is a java.lang or groovy.lang the class can't be a test class, otherwise the parent class is scanned. <p/> When a
     * parent class is a test class all the extending classes are marked as test classes.
     */
    @Override
    protected boolean processTestClass(final File testClassFile, boolean superClass) {
        final TestClassVisitor classVisitor = classVisitor(testClassFile);

        boolean isTest = classVisitor.isTest();

        if (!isTest) {
            final String superClassName = classVisitor.getSuperClassName();

            final File superClassFile = getSuperTestClassFile(superClassName);

            if (superClassFile != null) {
                isTest = processSuperClass(superClassFile);
            } else {
                LOGGER.debug("test-class-scan : failed to scan parent class {}, could not find the class file",
                        superClassName);
            }
        }

        publishTestClass(isTest, classVisitor, superClass);

        return isTest;
    }
}
