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
package org.gradle.api.internal.artifacts.publish.maven;

import org.apache.commons.io.FileUtils;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.maven.MavenPom;
import org.hamcrest.Description;
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

import java.io.File;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultPomFileWriterTest {
    private File _dest = new File("build/test/test-write.xml");

    private JUnit4Mockery context = new JUnit4Mockery();

    private Set<Configuration> testConfigurations;

    @Test
    public void testOptional() throws Exception {
        testConfigurations = new HashSet<Configuration>();
        final PomWriter writerMock = context.mock(PomWriter.class);
        
        final MavenPom testPom = context.mock(MavenPom.class);
        final String expectedPomText = "somePomXml";
        context.checking(new Expectations() {
            {
                one(writerMock).convert(with(same(testPom)), with(same(testConfigurations)), with(any(PrintWriter.class)));
                will(new WriteAction(expectedPomText));
            }
        });

        new DefaultPomFileWriter(writerMock).write(testPom, testConfigurations, _dest);
        assertTrue(_dest.exists());

        String wrote = FileUtils.readFileToString(_dest);
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
            PrintWriter printWriter = (PrintWriter) invocation.getParameter(2);
            printWriter.println(textToWrite);
            return null;
        }
    }
}


