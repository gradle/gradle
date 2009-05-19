package org.gradle.external.testng;

import org.gradle.api.testing.execution.AbstractTestFrameworkDetector;
import org.gradle.api.testing.execution.TestClassVisitor;
import org.gradle.api.GradleException;
import org.objectweb.asm.ClassReader;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
class TestNGDetector extends AbstractTestFrameworkDetector<TestNGTestClassDetecter> {
    private static final Logger logger = LoggerFactory.getLogger(TestNGDetector.class);

    TestNGDetector(File testClassesDirectory, List<File> testClasspath) {
        super(testClassesDirectory, testClasspath);
    }

    protected TestNGTestClassDetecter createClassVisitor() {
        return new TestNGTestClassDetecter(this);
    }

    public boolean processPossibleTestClass(File testClassFile) {
        final TestClassVisitor classVisitor = createClassVisitor();

        InputStream classStream = null;
        try {
            classStream = new BufferedInputStream(new FileInputStream(testClassFile));
            final ClassReader classReader = new ClassReader(classStream);
            classReader.accept(classVisitor, true);
        }
        catch ( Throwable e ) {
            throw new GradleException("failed to read class file " + testClassFile.getAbsolutePath(), e);
        }
        finally {
            IOUtils.closeQuietly(classStream);
        }

        boolean isTest = classVisitor.isTest();

        if (!isTest) {
            final String superClassName = classVisitor.getSuperClassName();

            if ( !"java/lang/Object".equals(superClassName) && !"groovy.lang.GroovyObject".equals(superClassName) ) {
                final File superClassFile = getSuperTestClassFile(superClassName);
                if ( superClassFile != null ) {
                    isTest = processPossibleTestClass(superClassFile);
                }
                else
                    logger.warn("test-class-scan : failed to scan parent class {}, could not find the class file", superClassName);
            }
        }

        if ( isTest && !classVisitor.isAbstract() )
            testClassNames.add(classVisitor.getClassName() + ".class");

        return isTest;
    }
}
