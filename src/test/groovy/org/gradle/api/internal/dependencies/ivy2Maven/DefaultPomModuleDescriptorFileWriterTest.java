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

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.util.FileUtil;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.hamcrest.Description;
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.Conf2ScopeMappingContainer;

import java.io.*;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultPomModuleDescriptorFileWriterTest {
    private File _dest = new File("build/test/test-write.xml");

    private JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void testOptional() throws Exception {
        final PomModuleDescriptorWriter moduleDescriptorWriterMock = context.mock(PomModuleDescriptorWriter.class);
        final Conf2ScopeMappingContainer conf2ScopeMappingContainerMock = context.mock(Conf2ScopeMappingContainer.class);
        
        final ModuleDescriptor testMd = DefaultModuleDescriptor.newDefaultInstance(ModuleRevisionId.newInstance("org", "name", "revision"));
        final String expectedPomText = "somePomXml";
        final String testLicenseText = "licenseText";
        final String testPackaging = "packaging";
        context.checking(new Expectations() {
            {
                one(moduleDescriptorWriterMock).convert(with(same(testMd)), with(equal(testPackaging)), with(equal(testLicenseText)),
                        with(same(conf2ScopeMappingContainerMock)), with(any(PrintWriter.class)));
                will(new WriteAction(expectedPomText));
            }
        });

        new DefaultPomModuleDescriptorFileWriter(moduleDescriptorWriterMock).write(testMd, testPackaging, testLicenseText,
                conf2ScopeMappingContainerMock, _dest);
        assertTrue(_dest.exists());

        String wrote = FileUtil.readEntirely(new BufferedReader(new FileReader(_dest)));
        assertEquals(expectedPomText + System.getProperty("line.separator"), wrote);
    }

    @Before
    public void setUp() {
        if (_dest.exists()) {
            _dest.delete();
        }
        if (!_dest.getParentFile().exists()) {
            _dest.getParentFile().mkdirs();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (_dest.exists()) {
            _dest.delete();
        }
    }

    public static class WriteAction implements Action {
        String textToWrite;

        public WriteAction(String textToWrite) {
            this.textToWrite = textToWrite;
        }

        public void describeTo(Description description) {
            description.appendText("writes");
        }

        public Object invoke(Invocation invocation) throws Throwable {
            PrintWriter printWriter = (PrintWriter) invocation.getParameter(4);
            printWriter.println(textToWrite);
            return null;
        }
    }
}


