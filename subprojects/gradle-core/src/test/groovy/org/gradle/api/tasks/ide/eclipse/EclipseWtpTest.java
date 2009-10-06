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
package org.gradle.api.tasks.ide.eclipse;

import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.util.GFileUtils;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class EclipseWtpTest extends AbstractTaskTest {

    private EclipseWtp eclipseWtp;

    private DefaultProjectDependency projectDependencyMock;

    private Project testProject;

    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    public AbstractTask getTask() {
        return eclipseWtp;
    }

    @Before
    public void setUp() {
        super.setUp();
        projectDependencyMock = context.mock(DefaultProjectDependency.class);
        testProject = HelperUtil.createRootProject(new File("dependent"));
        context.checking(new Expectations() {{
            allowing(projectDependencyMock).getDependencyProject(); will(returnValue(testProject));
        }});
        eclipseWtp = createTask(EclipseWtp.class);
    }

    @Test
    public void generateWtp() throws IOException {
        configureEclipse();
        eclipseWtp.execute();
        checkWtpFile();
    }

    @Test(expected = GradleException.class)
    public void generateWtpWithAbsoluteSourcePathInResourceMappings() throws IOException {
        configureEclipse();
        eclipseWtp.getWarResourceMappings().put("someDeployPath",
                WrapUtil.<Object>toList(new File(getProject().getProjectDir() + "xxx", "conf3").getAbsolutePath()));
        eclipseWtp.execute();
    }

    private void checkWtpFile() throws IOException {
        File wtp = new File(getProject().getProjectDir(), EclipseWtp.WTP_FILE_DIR + "/" + EclipseWtp.WTP_FILE_NAME);
        assertTrue(wtp.isFile());
        assertThat(GFileUtils.readFileToString(wtp),
                Matchers.equalTo(IOUtils.toString(this.getClass().getResourceAsStream("expectedWtpFile.txt"))));
    }

    private void configureEclipse() {
        eclipseWtp.setDeployName("name");
        eclipseWtp.setOutputDirectory("bin");
        eclipseWtp.setWarLibs(WrapUtil.<Object>toList("lib\\b.jar", "lib/a.jar"));
        eclipseWtp.setProjectDependencies(WrapUtil.toList(projectDependencyMock));
        eclipseWtp.setWarResourceMappings(WrapUtil.<String, List<Object>>toMap("WEB-INF/lib",
                WrapUtil.<Object>toList(new File(getProject().getProjectDir(), "conf1"), "conf2/child")));
    }
}
