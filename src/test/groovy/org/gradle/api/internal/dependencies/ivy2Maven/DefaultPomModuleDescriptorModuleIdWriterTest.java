/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.dependencies.ivy2Maven;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;

import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * @author Hans Dockter
 */
public class DefaultPomModuleDescriptorModuleIdWriterTest {
    private DefaultPomModuleDescriptorModuleIdWriter moduleIdConverter;
    private StringWriter stringWriter;
    private static final String NL = System.getProperty("line.separator");
    private static final String TEST_ORG = "testOrg";
    private static final String TEST_NAME = "testName";
    private static final String TEST_REVISION = "testRevision";
    private static final String TEST_HOME_PAGE = "testHomePage";
    private static final String TEST_PACKAGING = "testPackaging";

    @Before
    public void setUp() {
        stringWriter = new StringWriter();
        moduleIdConverter = new DefaultPomModuleDescriptorModuleIdWriter();
    }

    @Test
    public void convert() {
        DefaultModuleDescriptor moduleDescriptor = DefaultModuleDescriptor.newDefaultInstance(
                ModuleRevisionId.newInstance(TEST_ORG, TEST_NAME, TEST_REVISION)
        );
        moduleDescriptor.setHomePage(TEST_HOME_PAGE);
        moduleIdConverter.convert(moduleDescriptor, TEST_PACKAGING, new PrintWriter(stringWriter));
        assertEquals(createAllText(TEST_ORG, TEST_NAME, TEST_REVISION, TEST_PACKAGING, TEST_HOME_PAGE), stringWriter.toString());
    }

    @Test
    public void convertWithNullValuesForOptionals() {
        DefaultModuleDescriptor moduleDescriptor = DefaultModuleDescriptor.newDefaultInstance(
                ModuleRevisionId.newInstance(TEST_ORG, TEST_NAME, TEST_REVISION)
        );
        moduleDescriptor.setHomePage(null);
        moduleIdConverter.convert(moduleDescriptor, null, new PrintWriter(stringWriter));
        assertEquals(createBaseText(TEST_ORG, TEST_NAME, TEST_REVISION), stringWriter.toString());
    }

    private String createBaseText(String org, String name, String revision) {
        return XmlHelper.enclose(PomModuleDescriptorWriter.DEFAULT_INDENT, PomModuleDescriptorWriter.GROUP_ID, org) + NL +
                XmlHelper.enclose(PomModuleDescriptorWriter.DEFAULT_INDENT, PomModuleDescriptorWriter.ARTIFACT_ID, name) + NL +
                XmlHelper.enclose(PomModuleDescriptorWriter.DEFAULT_INDENT, PomModuleDescriptorWriter.VERSION, revision) + NL;
    }

    private String createAllText(String org, String name, String revision, String packaging, String homePage) {
        return createBaseText(org, name, revision) +
                XmlHelper.enclose(PomModuleDescriptorWriter.DEFAULT_INDENT, PomModuleDescriptorWriter.PACKAGING, packaging)
                + NL + XmlHelper.enclose(PomModuleDescriptorWriter.DEFAULT_INDENT, PomModuleDescriptorWriter.HOME_PAGE, homePage) + NL;
    }
}
