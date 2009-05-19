package org.gradle.api.testing.execution;

import org.gradle.api.testing.TestFrameworkDetector;

import java.io.*;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestFrameworkDetector<T extends TestClassVisitor> implements TestFrameworkDetector {
    private final File testClassesDirectory;
    protected final List<File> testClassDirectories;
    protected final Set<String> testClassNames;

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
