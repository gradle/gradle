package org.gradle.external.junit;

import org.gradle.api.testing.detection.AbstractTestFrameworkDetector;
import org.gradle.api.testing.detection.TestClassReceiver;
import org.gradle.api.testing.detection.TestClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class JUnitDetector extends AbstractTestFrameworkDetector<JUnitTestClassDetecter> {
    private static final Logger logger = LoggerFactory.getLogger(JUnitDetector.class);

    protected static final String TEST_CASE = "junit/framework/TestCase";
    protected static final String GROOVY_TEST_CASE = "groovy/util/GroovyTestCase";

    JUnitDetector(File testClassesDirectory, List<File> testClasspath, TestClassReceiver testClassReceiver) {
        super(testClassesDirectory, testClasspath, testClassReceiver);
    }

    protected JUnitTestClassDetecter createClassVisitor() {
        return new JUnitTestClassDetecter(this);
    }

    protected boolean processPossibleTestClass(File testClassFile, boolean superClass) {
        final TestClassVisitor classVisitor = classVisitor(testClassFile);

        boolean isTest = classVisitor.isTest();

        if (!isTest) { // scan parent class
            final String superClassName = classVisitor.getSuperClassName();

            if (isLangPackageClassName(superClassName)) {
                isTest = false;
            } else if (isTestCaseClassName(superClassName)) {
                isTest = true;
            } else {
                final File superClassFile = getSuperTestClassFile(superClassName);

                if (superClassFile != null) {
                    isTest = processSuperClass(superClassFile);
                } else
                    logger.debug("test-class-scan : failed to scan parent class {}, could not find the class file", superClassName);
            }
        }

        publishTestClass(isTest, classVisitor, superClass);

        return isTest;
    }

    protected boolean isTestCaseClassName(final String className) {
        return TEST_CASE.equals(className) || GROOVY_TEST_CASE.equals(className);
    }
}
