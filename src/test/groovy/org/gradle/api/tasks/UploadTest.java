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

package org.gradle.api.tasks;

import groovy.mock.interceptor.MockFor;
import org.gradle.api.DependencyManager;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Bundle;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.hasItems;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class UploadTest extends AbstractTaskTest {
    private static final String RESOLVER_NAME_1 = "resolver1";
    private static final String RESOLVER_NAME_2 = "resolver2";

    private Upload upload;
    private File projectRootDir;
    private MockFor ivyMocker;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before public void setUp() {
        super.setUp();
        upload = new Upload(getProject(), AbstractTaskTest.TEST_TASK_NAME, getTasksGraph());
        (projectRootDir = new File(HelperUtil.makeNewTestDir(), "root")).mkdir();
    }

    public AbstractTask getTask() {
        return upload;
    }

    @Test public void testUpload() {
        assertFalse(upload.isUploadModuleDescriptor());
        assertNotNull(upload.getUploadResolvers());
        assertEquals(new ArrayList(), upload.getConfigurations());
    }

    @Test public void testUploading() {
        final DependencyManager dependencyManagerMock = context.mock(DependencyManager.class);
        Map resolver1 = WrapUtil.toMap("name", RESOLVER_NAME_1);
        resolver1.put("url", "http://www.url1.com");
        Map resolver2 = WrapUtil.toMap("name", RESOLVER_NAME_2);
        resolver2.put("url", "http://www.url2.com");
        final List<String> expectedConfigurations = WrapUtil.toList("conf1");
        upload.setConfigurations(expectedConfigurations);
        upload.setProject(HelperUtil.createRootProject(projectRootDir));
        ((AbstractProject) upload.getProject()).setDependencies(dependencyManagerMock);
        Bundle bundle = new Bundle(upload.getProject(), "bundle", getTasksGraph());
        context.checking(new Expectations() {{
            one(dependencyManagerMock).publish(expectedConfigurations, upload.getUploadResolvers(), false);
        }});
        upload.execute();
    }
}
