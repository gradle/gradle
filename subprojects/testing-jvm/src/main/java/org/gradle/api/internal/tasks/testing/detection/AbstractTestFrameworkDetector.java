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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

public abstract class AbstractTestFrameworkDetector<T extends TestClassVisitor> implements TestFrameworkDetector {
    protected static final String TEST_CASE = "junit/framework/TestCase";
    protected static final String GROOVY_TEST_CASE = "groovy/util/GroovyTestCase";

    private List<File> testClassDirectories;
    private final ClassFileExtractionManager classFileExtractionManager;
    private final Map<File, Boolean> superClasses;
    private TestClassProcessor testClassProcessor;
    private final List<String> knownTestCaseClassNames;

    private File testClassesDirectory;
    private FileCollection testClasspath;

    protected AbstractTestFrameworkDetector(ClassFileExtractionManager classFileExtractionManager) {
        assert classFileExtractionManager != null;
        this.classFileExtractionManager = classFileExtractionManager;
        this.superClasses = new HashMap<File, Boolean>();
        this.knownTestCaseClassNames = new ArrayList<String>();
        addKnownTestCaseClassNames(TEST_CASE, GROOVY_TEST_CASE);
    }

    protected abstract T createClassVisitor();

    protected File getSuperTestClassFile(String superClassName) {
        prepareClasspath();
        if (StringUtils.isEmpty(superClassName)) {
            throw new IllegalArgumentException("superClassName is empty!");
        }

        final Iterator<File> testClassDirectoriesIt = testClassDirectories.iterator();

        File superTestClassFile = null;
        while (superTestClassFile == null && testClassDirectoriesIt.hasNext()) {
            final File testClassDirectory = testClassDirectoriesIt.next();
            final File superTestClassFileCandidate = new File(testClassDirectory, superClassName + ".class");
            if (superTestClassFileCandidate.exists()) {
                superTestClassFile = superTestClassFileCandidate;
            }
        }

        if (superTestClassFile != null) {
            return superTestClassFile;
        } else { // super test class file not in test class directories
            return classFileExtractionManager.getLibraryClassFile(superClassName);
        }
    }

    private void prepareClasspath() {
        if (testClassDirectories != null) {
            return;
        }

        testClassDirectories = new ArrayList<File>();

        if (testClassesDirectory != null) {
            testClassDirectories.add(testClassesDirectory);
        }
        if (testClasspath != null) {
            for (File file : testClasspath) {
                if (file.isDirectory()) {
                    testClassDirectories.add(file);
                } else if (file.isFile() && file.getName().endsWith(".jar")) {
                    classFileExtractionManager.addLibraryJar(file);
                }
            }
        }
    }

    public void setTestClassesDirectory(File testClassesDirectory) {
        this.testClassesDirectory = testClassesDirectory;
    }

    public void setTestClasspath(FileCollection testClasspath) {
        this.testClasspath = testClasspath;
    }

    protected TestClassVisitor classVisitor(final File testClassFile) {
        final TestClassVisitor classVisitor = createClassVisitor();

        InputStream classStream = null;
        try {
            classStream = new BufferedInputStream(new FileInputStream(testClassFile));
            final ClassReader classReader = new ClassReader(classStream);
            classReader.accept(classVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        } catch (Throwable e) {
            throw new GradleException("failed to read class file " + testClassFile.getAbsolutePath(), e);
        } finally {
            IOUtils.closeQuietly(classStream);
        }

        return classVisitor;
    }

    public boolean processTestClass(File testClassFile) {
        return processTestClass(testClassFile, false);
    }

    protected abstract boolean processTestClass(File testClassFile, boolean superClass);

    protected boolean processSuperClass(File testClassFile) {
        boolean isTest = false;

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
    protected void publishTestClass(boolean isTest, TestClassVisitor classVisitor, boolean superClass) {
        if (isTest && !classVisitor.isAbstract() && !superClass) {
            String className = Type.getObjectType(classVisitor.getClassName()).getClassName();
            testClassProcessor.processTestClass(new DefaultTestClassRunInfo(className));
        }
    }

    public void startDetection(TestClassProcessor testClassProcessor) {
        this.testClassProcessor = testClassProcessor;
    }

    public void addKnownTestCaseClassNames(String... knownTestCaseClassNames) {
        if (knownTestCaseClassNames != null && knownTestCaseClassNames.length != 0) {
            for (String knownTestCaseClassName : knownTestCaseClassNames) {
                if (StringUtils.isNotEmpty(knownTestCaseClassName)) {
                    this.knownTestCaseClassNames.add(knownTestCaseClassName.replaceAll("\\.", "/"));
                }
            }
        }
    }

    protected boolean isKnownTestCaseClassName(String testCaseClassName) {
        boolean isKnownTestCase = false;

        if (StringUtils.isNotEmpty(testCaseClassName)) {
            isKnownTestCase = knownTestCaseClassNames.contains(testCaseClassName);
        }

        return isKnownTestCase;
    }
}
