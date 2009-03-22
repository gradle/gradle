/*
 * Copyright 2007 the original author or authors.
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
import org.gradle.api.tasks.testing.AbstractTestFrameworkOptions
import org.gradle.util.GFileUtils
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion

/**
 * @author Tom Eyckmans
 */

public class TestNGOptions extends AbstractTestFrameworkOptions {

    public static final String JDK_ANNOTATIONS = 'JDK'
    public static final String JAVADOC_ANNOTATIONS = 'Javadoc'

    /**
     * Either the string "JDK" or "Javadoc". Defines which kind of annotations are used in these tests. If you use "Javadoc", you will also need to specify "sourcedir".
     *
     * Not required.
     *
     * Defaults to "JDK" if you're using the JDK 5 jar and to "Javadoc" if you're using the JDK 1.4 jar.
     */
    String annotations

    /**
     * A reference to a FileSet structure of the test classes to be run. 
     */
    // String classfilesetref

    /**
     * A PATH-like structure for the tests to be run.
     */
    // String classpath

    /**
     * A reference to a PATH-like structure for the tests to be run. 
     */
    // String classpathref

    /**
     * List of all directories containing Test sources. Should be set if annotations is 'Javadoc'.
     */
    List testResources

    /**
     * Print the TestNG launcher command.
     *
     * Not required. 
     *
     * Defaults to false
     */
    boolean dumpCommand = false

    /**
     * Enables JDK 1.4 assertion.
     *
     * Not required.
     *
     * Defaults to true 
     */
    boolean enableAssert = true

    /**
     * The name of a property to set in the event of a failure. It is used only if the haltonfailure is not set.
     *
     * Not required.
     */
    //String failureProperty = Test.FAILURES_OR_ERRORS_PROPERTY

    /**
     * Stop the build process if a failure has occurred during the test run.
     *
     * Not required.
     *
     * Defaults to false 
     */
    // boolean haltonfailure = false

    /**
     * Stop the build process if there is at least on skipped test.
     *
     * Not required. 
     *
     * Default to false
     */
    // boolean haltonskipped = false

    /**
     * The list of groups to run, separated by spaces or commas.
     */
    List includeGroups = []

    /**
     * The list of groups to exclude, separated by spaces or commas.
     */
    List excludeGroups = []

    /**
     * The JVM to use, which will be run by Runtime.exec()
     *
     * Default to 'java'
     */
    String jvm = null

    /**
     * A comma or space-separated list of fully qualified classes that are TestNG listeners (for example 
     * org.testng.ITestListener or org.testng.IReporter)
     *
     * Not required.
     */
    List listeners = []

    /**
     * Directory for reports output.
     */
    //String outputDir

    /**
     * The name of a property to set in the event of a skipped test. It is used only if the haltonskipped is not set.
     *
     * Not required.
     */
    String skippedProperty = null

    /**
     * A PATH-like structure for JDK 1.4 tests (using JavaDoc-like annotations)
     */
    // List sourcedir => covered by testResources

    /**
     * A reference to a PATH-like structure for JDK 1.4 tests (using JavaDoc-like annotations).
     */
    // String sourcedirref = null => covered by testResources

    /**
     * A fully qualified name of a TestNG starter.
     *
     * Not required.
     *
     * Defaults to  org.testng.TestNG
     */
    String suiteRunnerClass = null

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
     * Path to a jar containing tests and a suite definition. 
     */
    // String testJar

    /**
     * Whether the default listeners and reporters should be used.
     *
     * Defaults to true.
     */
    // boolean useDefaultListeners = true

    /**
     * The maximum time out in milliseconds that all the tests should run under.
     */
    long timeOut = Long.MAX_VALUE

    /**
     * The directory where the ant task should change to before running TestNG.
     */
    //String workingDir
    
    /**
     * A reference to a FileSet structure for the suite definitions to be run.
     */
    //String xmlfilesetref = null

    /**
     * Sets the default name of the test suite, if one is not specified in a suite xml file or in the source code.
     */
    String suiteName = null

    /**
     * Sets the default name of the test, if one is not specified in a suite xml file or in the source code.
     *
     * Not required. 
     *
     * Defaults to "Ant test"
     */
    String testName = null

    List jvmArgs = []
    Map systemProperties = [:]
    Map environment = [:]

    /**
     * The suiteXmlFiles to use for running TestNG.
     *
     * Note: The suiteXmlFiles can be used in conjunction with the suiteXmlBuilder.
     */
    List<File> suiteXmlFiles = []

    StringWriter suiteXmlWriter = null
    MarkupBuilder suiteXmlBuilder = null
    private File projectDir

    public TestNGOptions(TestNGTestFramework testngTestFramework, File projectDir) {
        super(testngTestFramework)
        this.projectDir = projectDir
    }

    void setAnnotationsOnSourceCompatibility(JavaVersion sourceCompatibilityProp) {
        if (sourceCompatibilityProp >= JavaVersion.VERSION_1_5) {
            jdkAnnotations()
        } else {
            javadocAnnotations()
        }
    }

    List excludedFieldsFromOptionMap() {
        List excludedFieldsFromOptionMap = [   'testResources', 'projectDir', 
            'systemProperties', 'jvmArgs', 'environment',
            'suiteXmlFiles','suiteXmlWriter','suiteXmlBuilder']

        if ( includeGroups.empty ) excludedFieldsFromOptionMap << 'includeGroups'
        if ( excludeGroups.empty ) excludedFieldsFromOptionMap << 'excludeGroups'
        if ( jvm == null ) excludedFieldsFromOptionMap << 'jvm'
        if ( listeners.empty ) excludedFieldsFromOptionMap << 'listeners'
        if ( skippedProperty == null ) excludedFieldsFromOptionMap << 'skippedProperty'
        if ( suiteRunnerClass == null ) excludedFieldsFromOptionMap << 'suiteRunnerClass'
        if ( parallel == null ) {
            excludedFieldsFromOptionMap << 'parallel'
            excludedFieldsFromOptionMap << 'threadCount'
        }
        if ( timeOut == Long.MAX_VALUE ) excludedFieldsFromOptionMap << 'timeOut'
        if ( suiteName == null ) excludedFieldsFromOptionMap << 'suiteName'
        if ( testName == null ) excludedFieldsFromOptionMap << 'testName'

        return excludedFieldsFromOptionMap
    }

    Map fieldName2AntMap() {
        [
            outputDir: 'outputdir',
            includeGroups : 'groups',
            excludeGroups : 'excludedGroups',
            suiteName : 'suitename',
            testName : 'testname'
        ]
    }

    Map optionMap() {
        super.optionMap()
    }

    MarkupBuilder suiteXmlBuilder() {
        suiteXmlWriter = new StringWriter()
        suiteXmlBuilder = new MarkupBuilder(suiteXmlWriter)
        return suiteXmlBuilder
    }

    /**
     * Add suite files by Strings. Each suiteFile String should be a path relative to the project root.
     */
    void suites(String ... suiteFiles) {
        suiteFiles.each { it ->
            suiteXmlFiles.add(new File(projectDir, it))
        }
    }

    /**
    * Add suite files by File objects.
     */
    void suites(File ... suiteFiles) {
        suiteXmlFiles.addAll(Arrays.asList(suiteFiles))
    }

    List<File> getSuites(File testSuitesDir) {
        List<File> suites = []

        // Suites need to be in one directory because the suites can only be passed to the testng ant task as an ant fileset.
        suiteXmlFiles.each { File it ->
            final File targetSuiteFile = new File(testSuitesDir, it.getName())

            if ( !targetSuiteFile.delete() ) {
                throw new GradleException("Failed to delete TestNG suite XML file " + targetSuiteFile.absolutePath);
            }

            GFileUtils.copyFile(it, targetSuiteFile)

            suites.add(targetSuiteFile)
        }

        if ( suiteXmlBuilder != null ) {
            final File buildSuiteXml = new File(testSuitesDir.absolutePath, "build-suite.xml");

            if ( buildSuiteXml.exists() ) {
                if ( !buildSuiteXml.delete() )
                    throw new RuntimeException("failed to remove already existing build-suite.xml file");
            }

            buildSuiteXml.append('<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">');
            buildSuiteXml.append(suiteXmlWriter.toString());

            suites.add(buildSuiteXml);
        }

        return suites
    }

    TestNGOptions dumpCommand() {
        this.dumpCommand = true

        return this
    }

    TestNGOptions jdkAnnotations() {
        this.annotations = JDK_ANNOTATIONS

        return this
    }

    TestNGOptions javadocAnnotations() {
        this.annotations = JAVADOC_ANNOTATIONS

        return this
    }

    public TestNGOptions includeGroups(String...includeGroups) {
        this.includeGroups.addAll(Arrays.asList(includeGroups))
        return this
    }

    public TestNGOptions excludeGroups(String...excludeGroups) {
        this.excludeGroups.addAll(Arrays.asList(excludeGroups))
        return this;
    }

    public def propertyMissing(String name) {
        if ( suiteXmlBuilder != null ) {
            return suiteXmlBuilder.getMetaClass()."${name}"
        }
        else {
            super.propertyMissing(name)
        }
    }

    public def methodMissing(String name, args) {
        if ( suiteXmlBuilder != null ) {
            return suiteXmlBuilder.getMetaClass().invokeMethod(suiteXmlBuilder, name, args);
        }
        else {
            super.methodMissing(name, args)
        }
    }
}
