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
package org.gradle.wrapper;

import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.SetSystemProperties;
import org.gradle.util.TemporaryFolder;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class WrapperTest {
    Wrapper wrapper;
    BootstrapMainStarter bootstrapMainStarterMock;
    Install installMock;
    final JUnit4Mockery context = new JUnit4GroovyMockery();
    @Rule
    public final SetSystemProperties systemProperties = new SetSystemProperties();
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();
    private File propertiesDir = tmpDir.getDir();
    private File propertiesFile = new File(propertiesDir, "wrapper.properties");

    @Before
    public void setUp() throws IOException {
        wrapper = new Wrapper();
        bootstrapMainStarterMock = context.mock(BootstrapMainStarter.class);
        installMock = context.mock(Install.class);
        Properties testProperties = new Properties();
        testProperties.load(WrapperTest.class.getResourceAsStream("/org/gradle/wrapper/wrapper.properties"));
        testProperties.store(new FileOutputStream(propertiesFile), null);
        System.setProperty(Wrapper.WRAPPER_PROPERTIES_PROPERTY, propertiesFile.getCanonicalPath());
    }

    @Test
    public void execute() throws Exception {
        final String[] expectedArgs = { "arg1", "arg2" };
        final File expectedGradleHome = new File("somepath");
        context.checking(new Expectations() {{
          one(installMock).createDist(
                  new URI("http://server/test/gradle.zip"),
                  "testDistBase",
                  "testDistPath",
                  "testZipBase",
                  "testZipPath"
          ); will(returnValue(expectedGradleHome));
          one(bootstrapMainStarterMock).start(expectedArgs, expectedGradleHome);
        }});
        wrapper.execute(expectedArgs, installMock, bootstrapMainStarterMock);
    }
}
