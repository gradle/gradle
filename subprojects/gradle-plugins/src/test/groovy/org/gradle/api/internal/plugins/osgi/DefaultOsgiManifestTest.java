/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.plugins.osgi;

import aQute.lib.osgi.Analyzer;
import org.gradle.api.tasks.bundling.GradleManifest;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.jar.Manifest;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultOsgiManifestTest {
    private DefaultOsgiManifest osgiManifest;
    private AnalyzerFactory analyzerFactoryMock;
    private ContainedVersionAnalyzer analyzerMock;

    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private Manifest testManifest;

    @Before
    public void setUp() {
        osgiManifest = new DefaultOsgiManifest();
        analyzerFactoryMock = context.mock(AnalyzerFactory.class);
        analyzerMock = context.mock(ContainedVersionAnalyzer.class);
        context.checking(new Expectations() {{
            allowing(analyzerFactoryMock).createAnalyzer(); will(returnValue(analyzerMock));
        }});
        osgiManifest.setAnalyzerFactory(analyzerFactoryMock);
        initTestManifest();
    }

    @Test
    public void init() {
        assertEquals(0, osgiManifest.getInstructions().size());
        assertNotNull(osgiManifest.getAnalyzerFactory());
    }

    private void initTestManifest() {
        testManifest = new Manifest();
        testManifest.getMainAttributes().putValue("someName", "someValue");
    }

    @Test
    public void setterGetter() {
        String testValue = "testValue";
        osgiManifest.setDescription(testValue);
        assertEquals(testValue, osgiManifest.getDescription());
        osgiManifest.setDocURL(testValue);
        assertEquals(testValue, osgiManifest.getDocURL());
        osgiManifest.setLicense(testValue);
        assertEquals(testValue, osgiManifest.getLicense());
        osgiManifest.setName(testValue);
        assertEquals(testValue, osgiManifest.getName());
        osgiManifest.setSymbolicName(testValue);
        assertEquals(testValue, osgiManifest.getSymbolicName());
        osgiManifest.setVendor(testValue);
        assertEquals(testValue, osgiManifest.getVendor());
        osgiManifest.setVersion(testValue);
        assertEquals(testValue, osgiManifest.getVersion());
    }

    @Test
    public void addInstruction() {
        String testInstructionName = "someInstruction";
        String instructionValue1 = "value1";
        String instructionValue2 = "value2";
        String instructionValue3 = "value3";
        assertSame(osgiManifest, osgiManifest.instruction(testInstructionName, instructionValue1, instructionValue2));
        assertEquals(WrapUtil.toList(instructionValue1, instructionValue2), osgiManifest.getInstructions().get(testInstructionName));
        osgiManifest.instruction(testInstructionName, instructionValue3);
        assertEquals(WrapUtil.toList(instructionValue1, instructionValue2, instructionValue3),
                osgiManifest.getInstructions().get(testInstructionName));
    }

    @Test
    public void addInstructionFirst() {
        String testInstructionName = "someInstruction";
        String instructionValue1 = "value1";
        String instructionValue2 = "value2";
        String instructionValue3 = "value3";
        assertSame(osgiManifest, osgiManifest.instructionFirst(testInstructionName, instructionValue1, instructionValue2));
        assertEquals(WrapUtil.toList(instructionValue1, instructionValue2), osgiManifest.getInstructions().get(testInstructionName));
        osgiManifest.instructionFirst(testInstructionName, instructionValue3);
        assertEquals(WrapUtil.toList(instructionValue3, instructionValue1, instructionValue2),
                osgiManifest.getInstructions().get(testInstructionName));
    }

    @Test
    public void instructionValue() {
        String testInstructionName = "someInstruction";
        String instructionValue1 = "value1";
        String instructionValue2 = "value2";
        osgiManifest.instruction(testInstructionName, instructionValue1, instructionValue2);
        assertEquals(WrapUtil.toList(instructionValue1, instructionValue2), osgiManifest.instructionValue(testInstructionName));
    }

    @Test
    public void generate() throws Exception {
        setUpOsgiManifest();
        prepareMock();
        assertEquals(testManifest, osgiManifest.generateManifest());
    }

    @Test
    public void overwrite() throws Exception {
        setUpOsgiManifest();
        prepareMock();
        GradleManifest gradleManifest = new GradleManifest(new Manifest());
        gradleManifest.getBaseManifest().getMainAttributes().putValue("somekey", "somevalue");
        gradleManifest.mainAttributes(WrapUtil.toMap("somekey2", "somevalue2"));
        osgiManifest.overwrite(gradleManifest);
        assertEquals(new Manifest(), gradleManifest.getBaseManifest());
        assertEquals(testManifest, gradleManifest.getManifest());
    }

    @Test
    public void generateWithNull() throws Exception {
        setUpOsgiManifest();
        prepareMockForNullTest();
        osgiManifest.setVersion(null);
        osgiManifest.generateManifest();
    }

    private void setUpOsgiManifest() throws IOException {
        String testSymbolicName = "symbolic";
        String testName = "myName";
        String testVersion = "myVersion";
        String testDescription = "myDescription";
        String testLicense = "myLicense";
        String testVendor = "myVendor";
        String testDocURL = "myDocUrl";
        String[] testExportPackages = {"pack1", "pack2"};
        String[] testImportPackages = {"pack3", "pack4"};
        osgiManifest.setSymbolicName(testSymbolicName);
        osgiManifest.setName(testName);
        osgiManifest.setVersion(testVersion);
        osgiManifest.setDescription(testDescription);
        osgiManifest.setLicense(testLicense);
        osgiManifest.setVendor(testVendor);
        osgiManifest.setDocURL(testDocURL);
        osgiManifest.instruction(Analyzer.EXPORT_PACKAGE, testExportPackages);
        osgiManifest.instruction(Analyzer.IMPORT_PACKAGE, testImportPackages);
    }

    private void prepareMock() throws Exception {
        context.checking(new Expectations() {{
            one(analyzerMock).setProperty(Analyzer.BUNDLE_VERSION, osgiManifest.getVersion());
        }});
        prepareMockForNullTest();
    }

    private void prepareMockForNullTest() throws Exception {
        context.checking(new Expectations() {{
            one(analyzerMock).setProperty(Analyzer.BUNDLE_SYMBOLICNAME, osgiManifest.getSymbolicName());
            one(analyzerMock).setProperty(Analyzer.BUNDLE_NAME, osgiManifest.getName());
            one(analyzerMock).setProperty(Analyzer.BUNDLE_DESCRIPTION, osgiManifest.getDescription());
            one(analyzerMock).setProperty(Analyzer.BUNDLE_LICENSE, osgiManifest.getLicense());
            one(analyzerMock).setProperty(Analyzer.BUNDLE_VENDOR, osgiManifest.getVendor());
            one(analyzerMock).setProperty(Analyzer.BUNDLE_DOCURL, osgiManifest.getDocURL());
            one(analyzerMock).setProperty(Analyzer.EXPORT_PACKAGE, GUtil.join(osgiManifest.instructionValue(Analyzer.EXPORT_PACKAGE), ","));
            one(analyzerMock).setProperty(Analyzer.IMPORT_PACKAGE, GUtil.join(osgiManifest.instructionValue(Analyzer.IMPORT_PACKAGE), ","));
            one(analyzerMock).setJar(osgiManifest.getClassesDir());
            one(analyzerMock).setClasspath(osgiManifest.getClasspath().toArray(new File[osgiManifest.getClasspath().size()]));
            allowing(analyzerMock).calcManifest(); will(returnValue(testManifest));
        }});
    }
}
