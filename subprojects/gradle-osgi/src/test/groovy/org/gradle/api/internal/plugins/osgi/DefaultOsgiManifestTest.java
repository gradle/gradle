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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.internal.DefaultAttributes;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.jar.Manifest;

import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultOsgiManifestTest {
    private static final String ARBITRARY_SECTION = "A-Different-Section";
    private static final String ARBITRARY_ATTRIBUTE = "Silly-Attribute";
    private static final String ANOTHER_ARBITRARY_ATTRIBUTE = "Serious-Attribute";

    private DefaultOsgiManifest osgiManifest;
    private Factory<ContainedVersionAnalyzer> analyzerFactoryMock;
    private ContainedVersionAnalyzer analyzerMock;

    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private FileResolver fileResolver = context.mock(FileResolver.class);

    @Before
    public void setUp() {
        osgiManifest = new DefaultOsgiManifest(fileResolver);
        analyzerFactoryMock = context.mock(Factory.class);
        analyzerMock = context.mock(ContainedVersionAnalyzer.class);
        context.checking(new Expectations() {{
            allowing(analyzerFactoryMock).create();
            will(returnValue(analyzerMock));
        }});
        osgiManifest.setAnalyzerFactory(analyzerFactoryMock);
    }

    @Test
    public void init() {
        assertEquals(0, osgiManifest.getInstructions().size());
        assertNotNull(osgiManifest.getAnalyzerFactory());
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
    public void getEffectiveManifest() throws Exception {
        setUpOsgiManifest();
        prepareMock();

        DefaultManifest manifest = osgiManifest.getEffectiveManifest();
        DefaultManifest defaultManifest = getDefaultManifestWithOsgiValues();
        DefaultManifest expectedManifest = new DefaultManifest(fileResolver).attributes(defaultManifest.getAttributes());
        for(Map.Entry<String, Attributes> ent : defaultManifest.getSections().entrySet()) {
            expectedManifest.attributes(ent.getValue(), ent.getKey());
        }

        assertThat(manifest.getAttributes(), Matchers.equalTo(expectedManifest.getAttributes()));
        assertThat(manifest.getSections(), Matchers.equalTo(expectedManifest.getSections()));
    }

    @Test
    public void merge() throws Exception {
        setUpOsgiManifest();
        prepareMock();
        DefaultManifest otherManifest = new DefaultManifest(fileResolver);
        otherManifest.mainAttributes(WrapUtil.toMap("somekey", "somevalue"));
        otherManifest.mainAttributes(WrapUtil.toMap(Analyzer.BUNDLE_VENDOR, "mergeVendor"));
        osgiManifest.from(otherManifest);
        DefaultManifest defaultManifest = getDefaultManifestWithOsgiValues();
        DefaultManifest expectedManifest = new DefaultManifest(fileResolver).attributes(defaultManifest.getAttributes());
        for(Map.Entry<String, Attributes> ent : defaultManifest.getSections().entrySet()) {
            expectedManifest.attributes(ent.getValue(), ent.getKey());
        }
        expectedManifest.attributes(otherManifest.getAttributes());

        DefaultManifest manifest = osgiManifest.getEffectiveManifest();
        assertTrue(manifest.isEqualsTo(expectedManifest));
    }

    @Test
    public void generateWithNull() throws Exception {
        setUpOsgiManifest();
        prepareMockForNullTest();
        osgiManifest.setVersion(null);
        osgiManifest.getEffectiveManifest();
    }

    private void setUpOsgiManifest() throws IOException {
        final FileCollection fileCollection = context.mock(FileCollection.class);
        context.checking(new Expectations() {{
            allowing(fileCollection).getFiles();
            will(returnValue(WrapUtil.toSet(new File("someFile"))));
        }});
        osgiManifest.setSymbolicName("symbolic");
        osgiManifest.setName("myName");
        osgiManifest.setVersion("myVersion");
        osgiManifest.setDescription("myDescription");
        osgiManifest.setLicense("myLicense");
        osgiManifest.setVendor("myVendor");
        osgiManifest.setDocURL("myDocUrl");
        osgiManifest.instruction(Analyzer.EXPORT_PACKAGE, new String[]{"pack1", "pack2"});
        osgiManifest.instruction(Analyzer.IMPORT_PACKAGE, new String[]{"pack3", "pack4"});
        osgiManifest.setClasspath(fileCollection);
        osgiManifest.setClassesDir(new File("someDir"));
        addPlainAttributesAndSections(osgiManifest);
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
            one(analyzerMock).setClasspath(osgiManifest.getClasspath().getFiles().toArray(new File[osgiManifest.getClasspath().getFiles().size()]));
            Manifest testManifest = new Manifest();
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_SYMBOLICNAME, osgiManifest.getSymbolicName());
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_NAME, osgiManifest.getName());
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_DESCRIPTION, osgiManifest.getDescription());
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_LICENSE, osgiManifest.getLicense());
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_VENDOR, osgiManifest.getVendor());
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_DOCURL, osgiManifest.getDocURL());
            testManifest.getMainAttributes().putValue(Analyzer.EXPORT_PACKAGE, GUtil.join(osgiManifest.instructionValue(Analyzer.EXPORT_PACKAGE), ","));
            testManifest.getMainAttributes().putValue(Analyzer.IMPORT_PACKAGE, GUtil.join(osgiManifest.instructionValue(Analyzer.IMPORT_PACKAGE), ","));
            allowing(analyzerMock).calcManifest();
            will(returnValue(testManifest));
        }});
    }

    private DefaultManifest getDefaultManifestWithOsgiValues() {
        DefaultManifest manifest = new DefaultManifest(fileResolver);
        manifest.getAttributes().put(Analyzer.BUNDLE_SYMBOLICNAME, osgiManifest.getSymbolicName());
        manifest.getAttributes().put(Analyzer.BUNDLE_NAME, osgiManifest.getName());
        manifest.getAttributes().put(Analyzer.BUNDLE_DESCRIPTION, osgiManifest.getDescription());
        manifest.getAttributes().put(Analyzer.BUNDLE_LICENSE, osgiManifest.getLicense());
        manifest.getAttributes().put(Analyzer.BUNDLE_VENDOR, osgiManifest.getVendor());
        manifest.getAttributes().put(Analyzer.BUNDLE_DOCURL, osgiManifest.getDocURL());
        manifest.getAttributes().put(Analyzer.EXPORT_PACKAGE, GUtil.join(osgiManifest.instructionValue(Analyzer.EXPORT_PACKAGE), ","));
        manifest.getAttributes().put(Analyzer.IMPORT_PACKAGE, GUtil.join(osgiManifest.instructionValue(Analyzer.IMPORT_PACKAGE), ","));
        addPlainAttributesAndSections(manifest);
        return manifest;
    }

    private void addPlainAttributesAndSections(DefaultManifest manifest) {
        manifest.getAttributes().put(ARBITRARY_ATTRIBUTE, "I like green eggs and ham.");
        final Attributes section_atts = new DefaultAttributes();
        section_atts.put(ANOTHER_ARBITRARY_ATTRIBUTE, "Death is the great equalizer.");
        manifest.getSections().put(ARBITRARY_SECTION, section_atts);
    }
}
