package org.gradle.api.testing.execution;

import org.gradle.api.testing.TestFrameworkDetector;
import org.gradle.api.artifacts.indexing.JarFilePackageLister;
import org.gradle.api.artifacts.indexing.JarFilePackageListener;
import org.gradle.api.GradleException;
import org.gradle.util.JarUtil;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestFrameworkDetector<T extends TestClassVisitor> implements TestFrameworkDetector {
    private static final Logger logger = LoggerFactory.getLogger(AbstractTestFrameworkDetector.class);
    private final File testClassesDirectory;
    protected final List<File> testClassDirectories;
    protected final Map<String, Set<File>> packageJarFilesMappings;
    protected final Map<String, File> extractedJarClasses;
    protected final Set<String> testClassNames;

    protected AbstractTestFrameworkDetector(File testClassesDirectory, List<File> testClasspath) {
        this.testClassesDirectory = testClassesDirectory;
        this.testClassNames = new HashSet<String>();
        this.packageJarFilesMappings = new HashMap<String, Set<File>>();
        this.extractedJarClasses = new HashMap<String, File>();

        testClassDirectories = new ArrayList<File>();
        testClassDirectories.add(testClassesDirectory);
        if ( testClasspath != null && !testClasspath.isEmpty() ) {
            for (File testClasspathItem : testClasspath) {
                if ( testClasspathItem.isDirectory() ) {
                    testClassDirectories.add(testClasspathItem);
                }
                else if ( testClasspathItem.getName().endsWith(".jar") ) {
                    final List<String> jarFilePackages = new ArrayList<String>();

                    new JarFilePackageLister().listJarPackages(
                            testClasspathItem,
                            new JarFilePackageListener() {
                                public void receivePackage(String packageName) {
                                    jarFilePackages.add(packageName);
                                }
                            }
                    );

                    for ( final String packageName : jarFilePackages ) {
                        Set<File> jarFiles = packageJarFilesMappings.get(packageName);
                        if ( jarFiles == null ) {
                            jarFiles = new HashSet<File>();
                        }
                        jarFiles.add(testClasspathItem);

                        packageJarFilesMappings.put(packageName, jarFiles);
                    }
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
        if  ( StringUtils.isEmpty(superClassName) ) throw new IllegalArgumentException("superClassName is empty!");
        if (    !superClassName.startsWith("java/lang") &&
                !superClassName.startsWith("groovy/lang") ) {
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
                File extractedSuperClassFile = extractedJarClasses.get(superClassName);
                if ( extractedSuperClassFile == null ) {
                    final int lastSlashIndex = superClassName.lastIndexOf('/');
                    if ( lastSlashIndex == -1 ) {
                        return null; // class in root package - should not happen
                    }
                    else {
                        final String superClassPackage = superClassName.substring(0, lastSlashIndex+1);
                        final Set<File> packageJarFiles = packageJarFilesMappings.get(superClassPackage);
                        File classSourceJar = null;
                        if ( packageJarFiles != null && !packageJarFiles.isEmpty() ) {
                            final Iterator<File> packageJarFilesIt = packageJarFiles.iterator();
                            boolean classFileExtracted = false;
                            try {
                                extractedSuperClassFile = File.createTempFile("jar_extract_", "_tmp");
                                extractedSuperClassFile.deleteOnExit();

                                while ( !classFileExtracted && packageJarFilesIt.hasNext() ) {
                                    final File jarFile = packageJarFilesIt.next();
                                    try {
                                        classFileExtracted = JarUtil.extractZipEntry(jarFile, superClassName + ".class", extractedSuperClassFile);
                                        if ( classFileExtracted )
                                            classSourceJar = jarFile;
                                    }
                                    catch ( IOException e ) {
                                        throw new GradleException("failed to extract class file from jar ("+jarFile+")", e);
                                    }
                                }
                            }
                            catch ( IOException e ) {
                                throw new GradleException("failed to create temp file to extract class from jar into", e);
                            }

                            if ( classFileExtracted ) {
                                logger.debug("test-class-scan : [extracted] : extracted super class "+superClassName+" from " + classSourceJar.getName());
                                extractedJarClasses.put(superClassName, extractedSuperClassFile);
                                return extractedSuperClassFile;
                            }
                            else
                                return null; // only null when the extract failed
                        }
                        else {
                            return null;// super class not on the classpath - unable to scan parent class
                        }
                    }
                }
                else {
                    return extractedSuperClassFile;
                }
            }
        }
        else {
            return null; // Object or GroovyObject class reached - no super class that has to be scanned
        }
    }
}
