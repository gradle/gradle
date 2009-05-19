package org.gradle.api.testing.execution;

import org.gradle.api.testing.TestFrameworkDetector;
import org.gradle.api.GradleException;
import org.objectweb.asm.ClassReader;
import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.*;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestFrameworkDetector<T extends TestClassVisitor> implements TestFrameworkDetector {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTestFrameworkDetector.class);

    private final File testClassesDirectory;
    protected final List<File> testClassDirectories;
    private final Set<String> testClassNames;

    protected AbstractTestFrameworkDetector(File testClassesDirectory, List<File> testClasspath) {
        this.testClassesDirectory = testClassesDirectory;
        this.testClassNames = new HashSet<String>();

        testClassDirectories = new ArrayList<File>();
        testClassDirectories.add(testClassesDirectory);
        if ( testClasspath != null && !testClasspath.isEmpty() ) {
            for (File testClasspathItem : testClasspath) {
                if ( testClasspathItem.isDirectory() ) {
                    testClassDirectories.add(testClasspathItem);
                }
            }
        }
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
            if ( "junit.framework.TestCase".equals(superClassName) || "groovy.util.GroovyTestCase".equals(superClassName) ) {
                isTest = true;
            }
            else if ( !"java/lang/Object".equals(superClassName) && !"groovy.lang.GroovyObject".equals(superClassName) ) {
                final File superClassFile = getSuperTestClassFile(superClassName);
                if ( superClassFile != null ) {
                    isTest = processPossibleTestClass(superClassFile);
                }
                else
                    LOG.warn("test-class-scan : failed to scan parent class {}, could not find the class file", superClassName);
            }
        }

        if ( isTest && !classVisitor.isAbstract() )
            testClassNames.add(classVisitor.getClassName() + ".class");

        return isTest;
    }

    public File getTestClassesDirectory() {
        return testClassesDirectory;
    }

    public Set<String> getTestClassNames() {
        return testClassNames;
    }

    protected abstract T createClassVisitor();

    protected File getSuperTestClassFile(String superClassName) {
        if ( !"java/lang/Object".equals(superClassName) && !"groovy.lang.GroovyObject".equals(superClassName) ) {
            final Iterator<File> testClassDirectoriesIt = testClassDirectories.iterator();

            File superTestClassFile = null;
            while ( superTestClassFile == null && testClassDirectoriesIt.hasNext() ) {
                final File testClassDirectory = testClassDirectoriesIt.next();
                final File superTestClassFileCandidate = new File(testClassDirectory, superClassName + ".class");
                if ( superTestClassFileCandidate.exists() )
                    superTestClassFile = superTestClassFileCandidate;
            }

            if ( superTestClassFile != null ) {
                return superTestClassFile;
            }
            else { // super test class file not in test class directories
                return null;// TODO possibly in jars or other directories on the classpath
            }
        }
        else {
            return null;
        }
    }
}
