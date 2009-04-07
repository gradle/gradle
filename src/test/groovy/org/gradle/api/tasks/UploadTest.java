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

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishInstruction;
import org.gradle.api.internal.AbstractTask;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class UploadTest extends AbstractTaskTest {
    private Upload upload;
    private File projectRootDir;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before public void setUp() {
        super.setUp();
        upload = new Upload(getProject(), AbstractTaskTest.TEST_TASK_NAME);
        (projectRootDir = new File(HelperUtil.makeNewTestDir(), "root")).mkdir();
    }

    public AbstractTask getTask() {
        return upload;
    }

    @Test public void testUpload() {
        assertNull(upload.getPublishInstruction());
        assertNotNull(upload.getRepositories());
    }

    @Test public void testUploading() {
        final Configuration configurationMock = context.mock(Configuration.class);
        final PublishInstruction publishInstruction = new PublishInstruction();
        upload.setPublishInstruction(publishInstruction);
        upload.setConfiguration(configurationMock);
        upload.setProject(HelperUtil.createRootProject(projectRootDir));
        final DependencyResolver repository = upload.getRepositories().addMavenRepo();
        context.checking(new Expectations() {{
            one(configurationMock).publish(WrapUtil.toList(repository), publishInstruction);
        }});
        upload.execute();
    }

    @Test public void testRepositories() {
        final DependencyResolver repository = upload.repositories(HelperUtil.toClosure("{ addMavenRepo() }")).getResolverList().get(0);
        assertThat(upload.getRepositories().getResolverList(), Matchers.equalTo(WrapUtil.toList(repository)));
    }
}
