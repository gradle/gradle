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

package org.gradle.api.internal.tasks.testing.detection;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.FileUtils.hasExtension;

public abstract class AbstractTestFrameworkDetector<T extends TestClassVisitor> implements TestFrameworkDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTestFrameworkDetector.class);
    private static final String JAVA_LANG_OBJECT = "java/lang/Object";

    private List<File> testClassDirectories;
    private final ClassFileExtractionManager classFileExtractionManager;
    private final Map<File, Boolean> superClasses;
    private TestClassProcessor testClassProcessor;

    private Set<File> testClassesDirectories;
    private Set<File> testClasspath;

    protected AbstractTestFrameworkDetector(ClassFileExtractionManager classFileExtractionManager) {
        assert classFileExtractionManager != null;
        this.classFileExtractionManager = classFileExtractionManager;
        this.superClasses = new HashMap<File, Boolean>();
    }

    protected abstract T createClassVisitor();

    private File getSuperTestClassFile(String superClassName) {
        prepareClasspath();
        if (StringUtils.isEmpty(superClassName)) {
            throw new IllegalArgumentException("superClassName is empty!");
        }

        File superTestClassFile = null;
        for (File testClassDirectory : testClassDirectories) {
            File candidate = new File(testClassDirectory, superClassName + ".class");
            if (candidate.exists()) {
                superTestClassFile = candidate;
            }
        }

        if (superTestClassFile != null) {
            return superTestClassFile;
        } else if (JAVA_LANG_OBJECT.equals(superClassName)) {
            // java.lang.Object found, which is not a test class
            return null;
        } else {
            // super test class file not in test class directories
            return classFileExtractionManager.getLibraryClassFile(superClassName);
        }
    }

    private void prepareClasspath() {
        if (testClassDirectories != null) {
            return;
        }

        testClassDirectories = new ArrayList<File>();

        if (testClassesDirectories != null) {
            testClassDirectories.addAll(testClassesDirectories);
        }
        if (testClasspath != null) {
            for (File file : testClasspath) {
                if (file.isDirectory()) {
                    testClassDirectories.add(file);
                } else if (file.isFile() && hasExtension(file, ".jar")) {
                    classFileExtractionManager.addLibraryJar(file);
                }
            }
        }
    }

    @Override
    public void setTestClasses(Set<File> testClassesDirectories) {
        this.testClassesDirectories = testClassesDirectories;
    }

    @Override
    public void setTestClasspath(Set<File> testClasspath) {
        this.testClasspath = testClasspath;
    }

    protected TestClassVisitor classVisitor(final File testClassFile) {
        final TestClassVisitor classVisitor = createClassVisitor();

        InputStream classStream = null;
        try {
            classStream = new BufferedInputStream(new FileInputStream(testClassFile));
            final ClassReader classReader = new ClassReader(IOUtils.toByteArray(classStream));
            classReader.accept(classVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        } catch (Throwable e) {
            throw new GradleException("failed to read class file " + testClassFile.getAbsolutePath(), e);
        } finally {
            IOUtils.closeQuietly(classStream);
        }

        return classVisitor;
    }

    @Override
    public boolean processTestClass(File testClassFile) {
        return processTestClass(testClassFile, false);
    }

    /**
     * Uses a TestClassVisitor to detect whether the class in the testClassFile is a test class. <p/> If the class is not a test, this function will go up the inheritance tree to check if a parent
     * class is a test class. First the package of the parent class is checked, if it is a java.lang or groovy.lang the class can't be a test class, otherwise the parent class is scanned. <p/> When a
     * parent class is a test class all the extending classes are marked as test classes.
     */
    private boolean processTestClass(final File testClassFile, boolean superClass) {
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

    protected abstract boolean isKnownTestCaseClassName(String testCaseClassName);

    private boolean processSuperClass(File testClassFile) {
        boolean isTest;

        Boolean isSuperTest = superClasses.get(testClassFile);

        if (isSuperTest == null) {
            isTest = processTestClass(testClassFile, true);

            superClasses.put(testClassFile, isTest);
        } else {
            isTest = isSuperTest;
        }

        return isTest;
    }

    /**
     * In none super class mode a test class is published when the class is a test and it is not abstract. In super class mode it must not publish the class otherwise it will get published multiple
     * times (for each extending class).
     */
    private void publishTestClass(boolean isTest, TestClassVisitor classVisitor, boolean superClass) {
        if (isTest && !classVisitor.isAbstract() && !superClass) {
            String className = Type.getObjectType(classVisitor.getClassName()).getClassName();
            testClassProcessor.processTestClass(new DefaultTestClassRunInfo(className));
        }
    }

    @Override
    public void startDetection(TestClassProcessor testClassProcessor) {
        this.testClassProcessor = testClassProcessor;
    }
}
