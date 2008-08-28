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
import org.gradle.api.Project;
import org.gradle.api.dependencies.ProjectDependency;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.util.GFileUtils;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
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
public class EclipseClasspathTest extends AbstractTaskTest {

    private EclipseClasspath eclipseClasspath;

    private ProjectDependency projectDependencyMock;

    private Project testProject;

    private List<Object> expectedSrcDirs;

    private List<Object> expectedTestSrcDirs;

    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);    
    }};

    public AbstractTask getTask() {
        return eclipseClasspath;
    }

    @Before
    public void setUp() {
        super.setUp();
        expectedSrcDirs = WrapUtil.<Object>toList("src/main/java", "src/main/resources");
        expectedTestSrcDirs = WrapUtil.<Object>toList("src/test/java", "src/test/resources");
        File projectDir = HelperUtil.makeNewTestDir();
        createProjectDirs(expectedSrcDirs, expectedTestSrcDirs);
        projectDependencyMock = context.mock(ProjectDependency.class);
        testProject = HelperUtil.createRootProject(new File("dependent"));
        context.checking(new Expectations() {{
            allowing(projectDependencyMock).getDependencyProject(); will(returnValue(testProject));
        }});
        eclipseClasspath = new EclipseClasspath(getProject(), AbstractTaskTest.TEST_TASK_NAME, getTasksGraph());
    }

    private void createProjectDirs(List<Object>... fileLists) {
        for (List<Object> fileList : fileLists) {
            for (Object path : fileList) {
                getProject().file(path).mkdirs();
            }
        }
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @Test
    public void generateClasspath() throws IOException {
        configureEclipse();
        eclipseClasspath.execute();
        checkClasspathFile();
    }

    private void checkClasspathFile() throws IOException {
        File classpath = new File(getProject().getProjectDir(), EclipseClasspath.CLASSPATH_FILE_NAME);
        assertTrue(classpath.isFile());
        assertThat(GFileUtils.readFileToString(classpath),
                Matchers.equalTo(IOUtils.toString(this.getClass().getResourceAsStream("expectedClasspathFile.txt"))));
    }

    private void configureEclipse() {
        eclipseClasspath.setOutputDirectory("bin");
        eclipseClasspath.setTestOutputDirectory("testbin");
        eclipseClasspath.setSrcDirs(expectedSrcDirs);
        eclipseClasspath.setTestSrcDirs(expectedTestSrcDirs);
        eclipseClasspath.setClasspathLibs(WrapUtil.<Object>toList("lib\\b.jar", "lib/a.jar"));
        eclipseClasspath.setProjectDependencies(WrapUtil.toList(projectDependencyMock));
    }
}
