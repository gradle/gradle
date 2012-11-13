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
package org.gradle.api.tasks.testing.testng

import groovy.xml.MarkupBuilder
import org.gradle.api.JavaVersion
import org.gradle.api.tasks.testing.TestFrameworkOptions

/**
 * @author Tom Eyckmans
 */
public class TestNGOptions extends TestFrameworkOptions implements Serializable {
    private static final long serialVersionUID = 1

    static final String JDK_ANNOTATIONS = 'JDK'
    static final String JAVADOC_ANNOTATIONS = 'Javadoc'

    /**
     * When true, Javadoc annotations are used for these tests. When false, JDK annotations are used. If you use
     * Javadoc annotations, you will also need to specify "sourcedir".
     *
     * Defaults to JDK annotations if you're using the JDK 5 jar and to Javadoc annotations if you're using the JDK 1.4
     * jar.
     */
    boolean javadocAnnotations

    String getAnnotations() {
        javadocAnnotations ? JAVADOC_ANNOTATIONS : JDK_ANNOTATIONS
    }

    /**
     * List of all directories containing Test sources. Should be set if annotations is 'Javadoc'.
     */
    List testResources

    /**
     * The set of groups to run.
     */
    Set<String> includeGroups = new HashSet<String>()

    /**
     * The set of groups to exclude.
     */
    Set<String> excludeGroups = new HashSet<String>()

    /**
     * The set of fully qualified classes that are TestNG listeners (for example org.testng.ITestListener or
     * org.testng.IReporter)
     */
    Set<String> listeners = new LinkedHashSet<String>()

    /**
     * The parallel mode to use for running the tests - either methods or tests.
     *
     * Not required.
     *
     * If not present, parallel mode will not be selected 
     */
    String parallel = null

    /**
     * The number of threads to use for this run. Ignored unless the parallel mode is also specified
     */
    int threadCount = 1

    /**
     * Whether the default listeners and reporters should be used. Defaults to true.
     *
     * Please refer to the documentation of your version of TestNG what are the default listeners.
     * At the moment of writing this documentation, the default listeners are a set of reporters that generate:
     * TestNG variant of html results, TestNG variant of xml results in JUnit format, emailable html test report,
     * xml results in TestNG format.
     *
     */
    boolean useDefaultListeners = true

    /**
     * Sets the default name of the test suite, if one is not specified in a suite xml file or in the source code.
     */
    String suiteName = 'Gradle suite'

    /**
     * Sets the default name of the test, if one is not specified in a suite xml file or in the source code.
     */
    String testName = 'Gradle test'

    /**
     * The suiteXmlFiles to use for running TestNG.
     *
     * Note: The suiteXmlFiles can be used in conjunction with the suiteXmlBuilder.
     */
    List<File> suiteXmlFiles = []

    transient StringWriter suiteXmlWriter = null
    transient MarkupBuilder suiteXmlBuilder = null
    private final File projectDir

    TestNGOptions(File projectDir) {
        this.projectDir = projectDir
    }

    void setAnnotationsOnSourceCompatibility(JavaVersion sourceCompatibilityProp) {
        if (sourceCompatibilityProp >= JavaVersion.VERSION_1_5) {
            jdkAnnotations()
        } else {
            javadocAnnotations()
        }
    }

    MarkupBuilder suiteXmlBuilder() {
        suiteXmlWriter = new StringWriter()
        suiteXmlBuilder = new MarkupBuilder(suiteXmlWriter)
        suiteXmlBuilder
    }

    /**
     * Add suite files by Strings. Each suiteFile String should be a path relative to the project root.
     */
    void suites(String... suiteFiles) {
        suiteFiles.each {
            suiteXmlFiles.add(new File(projectDir, it))
        }
    }

    /**
     * Add suite files by File objects.
     */
    void suites(File... suiteFiles) {
        suiteXmlFiles.addAll(Arrays.asList(suiteFiles))
    }

    List<File> getSuites(File testSuitesDir) {
        List<File> suites = []

        suites.addAll(suiteXmlFiles)

        if (suiteXmlBuilder != null) {
            File buildSuiteXml = new File(testSuitesDir.absolutePath, "build-suite.xml")

            if (buildSuiteXml.exists()) {
                if (!buildSuiteXml.delete()) {
                    throw new RuntimeException("failed to remove already existing build-suite.xml file")
                }
            }

            buildSuiteXml.append('<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">')
            buildSuiteXml.append(suiteXmlWriter.toString())

            suites.add(buildSuiteXml);
        }

        return suites
    }

    TestNGOptions jdkAnnotations() {
        javadocAnnotations = false
        this
    }

    TestNGOptions javadocAnnotations() {
        javadocAnnotations = true
        this
    }

    TestNGOptions includeGroups(String... includeGroups) {
        this.includeGroups.addAll(Arrays.asList(includeGroups))
        this
    }

    TestNGOptions excludeGroups(String... excludeGroups) {
        this.excludeGroups.addAll(Arrays.asList(excludeGroups))
        this
    }

    TestNGOptions useDefaultListeners() {
        useDefaultListeners = true
        this
    }

    TestNGOptions useDefaultListeners(boolean useDefaultListeners) {
        this.useDefaultListeners = useDefaultListeners
        this
    }

    Object propertyMissing(String name) {
        if (suiteXmlBuilder != null) {
            return suiteXmlBuilder.getMetaClass()."${name}"
        }
        throw new MissingPropertyException(name, getClass());
    }

    Object methodMissing(String name, Object args) {
        if (suiteXmlBuilder != null) {
            return suiteXmlBuilder.getMetaClass().invokeMethod(suiteXmlBuilder, name, args)
        }
        throw new MissingMethodException(name, getClass(), args)
    }
}
