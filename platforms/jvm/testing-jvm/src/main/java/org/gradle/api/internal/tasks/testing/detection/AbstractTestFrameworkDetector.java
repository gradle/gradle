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
import org.gradle.api.internal.file.RelativeFile;
import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.IoActions;
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

import static org.gradle.internal.FileUtils.hasExtension;

public abstract class AbstractTestFrameworkDetector<T extends TestClassVisitor> implements TestFrameworkDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTestFrameworkDetector.class);
    private static final String JAVA_LANG_OBJECT = "java/lang/Object";

    private List<File> testClassDirectories;
    private final ClassFileExtractionManager classFileExtractionManager;
    private final Map<File, Boolean> superClasses;
    private TestClassProcessor testClassProcessor;

    private List<File> testClassesDirectories;
    private List<File> testClasspath;

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
    public void setTestClasses(List<File> testClassesDirectories) {
        this.testClassesDirectories = testClassesDirectories;
    }

    @Override
    public void setTestClasspath(List<File> testClasspath) {
        this.testClasspath = testClasspath;
    }

    private TestClass readClassFile(File testClassFile, Factory<String> fallbackClassNameProvider) {
        final TestClassVisitor classVisitor = createClassVisitor();

        InputStream classStream = null;
        try {
            classStream = new BufferedInputStream(new FileInputStream(testClassFile));
            final ClassReader classReader = new ClassReader(IOUtils.toByteArray(classStream));
            classReader.accept(classVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            return TestClass.forParseableFile(classVisitor);
        } catch (Throwable e) {
            LOGGER.debug("Failed to read class file " + testClassFile.getAbsolutePath() + "; assuming it's a test class and continuing", e);
            return TestClass.forUnparseableFile(fallbackClassNameProvider.create());
        } finally {
            IoActions.closeQuietly(classStream);
        }
    }

    @Override
    public boolean processTestClass(final RelativeFile testClassFile) {
        return processTestClass(testClassFile.getFile(), false, new Factory<String>() {
            @Override
            public String create() {
                return testClassFile.getRelativePath().getPathString().replace(".class", "");
            }
        });
    }

    /**
     * Uses a TestClassVisitor to detect whether the class in the testClassFile is a test class.
     * <p>
     * If the class is not a test, this function will go up the inheritance tree to check if a parent
     * class is a test class. First the package of the parent class is checked, if it is a java.lang or groovy.lang the class can't be a test class, otherwise the parent class is scanned.
     * <p>
     * When a parent class is a test class all the extending classes are marked as test classes.
     */
    private boolean processTestClass(File testClassFile, boolean superClass, Factory<String> fallbackClassNameProvider) {
        TestClass testClass = readClassFile(testClassFile, fallbackClassNameProvider);

        boolean isTest = testClass.isTest();

        if (!isTest) { // scan parent class
            String superClassName = testClass.getSuperClassName();

            if (isKnownTestCaseClassName(superClassName)) {
                isTest = true;
            } else {
                File superClassFile = getSuperTestClassFile(superClassName);

                if (superClassFile != null) {
                    isTest = processSuperClass(superClassFile, superClassName);
                } else {
                    LOGGER.debug("test-class-scan : failed to scan parent class {}, could not find the class file",
                        superClassName);
                }
            }
        }

        publishTestClass(isTest, testClass, superClass);

        return isTest;
    }

    protected abstract boolean isKnownTestCaseClassName(String testCaseClassName);

    private boolean processSuperClass(File testClassFile, String superClassName) {
        boolean isTest;

        Boolean isSuperTest = superClasses.get(testClassFile);

        if (isSuperTest == null) {
            isTest = processTestClass(testClassFile, true, Factories.constant(superClassName));

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
    private void publishTestClass(boolean isTest, TestClass testClass, boolean superClass) {
        if (isTest && !testClass.isAbstract() && !superClass) {
            String className = Type.getObjectType(testClass.getClassName()).getClassName();
            testClassProcessor.processTestClass(new DefaultTestClassRunInfo(className));
        }
    }

    @Override
    public void startDetection(TestClassProcessor testClassProcessor) {
        this.testClassProcessor = testClassProcessor;
    }

    private static class TestClass {
        private final boolean test;
        private final boolean isAbstract;
        private final String className;
        private final String superClassName;

        static TestClass forParseableFile(TestClassVisitor testClassVisitor) {
            return new TestClass(testClassVisitor.isTest(), testClassVisitor.isAbstract(), testClassVisitor.getClassName(), testClassVisitor.getSuperClassName());
        }

        static TestClass forUnparseableFile(String className) {
            return new TestClass(true, false, className, null);
        }

        private TestClass(boolean test, boolean isAbstract, String className, String superClassName) {
            this.test = test;
            this.isAbstract = isAbstract;
            this.className = className;
            this.superClassName = superClassName;
        }

        boolean isTest() {
            return test;
        }

        boolean isAbstract() {
            return isAbstract;
        }

        String getClassName() {
            return className;
        }

        String getSuperClassName() {
            return superClassName;
        }
    }

}
