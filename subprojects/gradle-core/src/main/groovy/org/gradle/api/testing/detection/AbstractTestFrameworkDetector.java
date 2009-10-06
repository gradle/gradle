package org.gradle.api.testing.detection;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;
import org.gradle.api.GradleException;
import org.gradle.api.testing.fabric.TestFrameworkDetector;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestFrameworkDetector<T extends TestClassVisitor> implements TestFrameworkDetector {
    private static final Logger logger = LoggerFactory.getLogger(AbstractTestFrameworkDetector.class);

    protected static final String JAVA_LANG = "java/lang";
    protected static final String GROOVY_LANG = "groovy/lang";
    protected static final String CLASS_FILE_EXT = ".class";

    private final File testClassesDirectory;
    protected final List<File> testClassDirectories;
    protected final ClassFileExtractionManager classFileExtractionManager;
    protected final TestClassReceiver testClassReceiver;
    protected final Map<File, Boolean> superClasses;

    protected AbstractTestFrameworkDetector(File testClassesDirectory, List<File> testClasspath, final TestClassReceiver testClassReceiver) {
        this.testClassesDirectory = testClassesDirectory;
        this.classFileExtractionManager = new ClassFileExtractionManager();
        this.testClassReceiver = testClassReceiver;
        this.superClasses = new HashMap<File, Boolean>();

        testClassDirectories = new ArrayList<File>();

        testClassDirectories.add(testClassesDirectory);

        if (testClasspath != null && !testClasspath.isEmpty()) {
            for (final File testClasspathItem : testClasspath) {
                if (testClasspathItem.isDirectory()) {
                    testClassDirectories.add(testClasspathItem);
                } else if (testClasspathItem.getName().endsWith(".jar")) {
                    classFileExtractionManager.addLibraryJar(testClasspathItem);
                }
            }
        }
    }

    public File getTestClassesDirectory() {
        return testClassesDirectory;
    }

    protected abstract T createClassVisitor();

    protected File getSuperTestClassFile(String superClassName) {
        if (StringUtils.isEmpty(superClassName)) throw new IllegalArgumentException("superClassName is empty!");
        if (isLangPackageClassName(superClassName)) {
            return null;  // Object or GroovyObject class reached - no super class that has to be scanned
        } else {
            final Iterator<File> testClassDirectoriesIt = testClassDirectories.iterator();

            File superTestClassFile = null;
            while (superTestClassFile == null && testClassDirectoriesIt.hasNext()) {
                final File testClassDirectory = testClassDirectoriesIt.next();
                final File superTestClassFileCandidate = new File(testClassDirectory, superClassName + ".class");
                if (superTestClassFileCandidate.exists())
                    superTestClassFile = superTestClassFileCandidate;
            }

            if (superTestClassFile != null) {
                return superTestClassFile;
            } else { // super test class file not in test class directories
                return classFileExtractionManager.getLibraryClassFile(superClassName);
            }
        }
    }

    protected TestClassVisitor classVisitor(final File testClassFile) {
        final TestClassVisitor classVisitor = createClassVisitor();

        InputStream classStream = null;
        try {
            classStream = new BufferedInputStream(new FileInputStream(testClassFile));
            final ClassReader classReader = new ClassReader(classStream);
            classReader.accept(classVisitor, true);
        }
        catch (Throwable e) {
            throw new GradleException("failed to read class file " + testClassFile.getAbsolutePath(), e);
        }
        finally {
            IOUtils.closeQuietly(classStream);
        }

        return classVisitor;
    }

    protected boolean isLangPackageClassName(final String className) {
        return className.startsWith(JAVA_LANG) || className.startsWith(GROOVY_LANG);
    }

    protected String classVisitorToClassFilename(final TestClassVisitor classVisitor) {
        final StrBuilder classFilenameBuilder = new StrBuilder();

        classFilenameBuilder.append(classVisitor.getClassName());
        classFilenameBuilder.append(CLASS_FILE_EXT);

        return classFilenameBuilder.toString();
    }

    public boolean processPossibleTestClass(File testClassFile) {
        return processPossibleTestClass(testClassFile, false);
    }

    protected abstract boolean processPossibleTestClass(File testClasFile, boolean superClass);

    protected boolean processSuperClass(File testClassFile) {
        boolean isTest = false;

        Boolean isSuperTest = superClasses.get(testClassFile);

        if (isSuperTest == null) {
            isTest = processPossibleTestClass(testClassFile, true);

            superClasses.put(testClassFile, isTest);
        } else {
            isTest = isSuperTest;
        }

        return isTest;
    }

    /**
     * In none super class mode a test class is published when the class is a test it is not abstract.
     * In super class mode it musn't publish the class otherwise it will get published multiple times
     * (for each extending class).
     *
     * @param isTest
     * @param classVisitor
     * @param superClass
     */
    protected void publishTestClass(boolean isTest, TestClassVisitor classVisitor, boolean superClass) {
        if (isTest && !classVisitor.isAbstract() && !superClass)
            testClassReceiver.receiveTestClass(classVisitorToClassFilename(classVisitor));
    }

    public void manualTestClass(String testClassName) {
        testClassReceiver.receiveTestClass(testClassName);
    }
}
