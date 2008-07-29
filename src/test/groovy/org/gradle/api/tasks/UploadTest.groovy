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

package org.gradle.api.tasks

import groovy.mock.interceptor.MockFor
import org.apache.ivy.Ivy
import org.apache.ivy.core.publish.PublishEngine
import org.gradle.api.DependencyManager
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.dependencies.ModuleDescriptorConverter
import org.gradle.api.internal.project.AbstractProject
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Bundle
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import static org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock)
class UploadTest extends AbstractTaskTest {
    static final String RESOLVER_NAME_1 = 'resolver1'
    static final String RESOLVER_NAME_2 = 'resolver2'

    Upload upload
    File projectRootDir
    MockFor ivyMocker
    MockFor publishEngineMocker
    MockFor moduleDescriptorConverterMocker

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp() {
        super.setUp()
        upload = new Upload(project, AbstractTaskTest.TEST_TASK_NAME, getTasksGraph())
        (projectRootDir = new File(HelperUtil.makeNewTestDir(), 'root')).mkdir()
        ivyMocker = new MockFor(Ivy)
        publishEngineMocker = new MockFor(PublishEngine)
        moduleDescriptorConverterMocker = new MockFor(ModuleDescriptorConverter)
    }

    AbstractTask getTask() {
        upload
    }

    @Test public void testUpload() {
        assert !upload.uploadModuleDescriptor
        assert upload.uploadResolvers
        assertEquals([], upload.configurations)
        assertEquals([] as Set, upload.bundles)
    }

    @Test public void testUploading() {
        DependencyManager dependencyManagerMock = context.mock(DependencyManager)
        upload.uploadResolvers.add([name: RESOLVER_NAME_1, url: 'http://www.url1.com'])
        upload.uploadResolvers.add([name: RESOLVER_NAME_2, url: 'http://www.url2.com'])
        List expectedConfigurations = ['conf1']
        upload.configurations = expectedConfigurations
        upload.project = HelperUtil.createRootProject(projectRootDir)
        ((AbstractProject) upload.project).setDependencies(dependencyManagerMock)
        Bundle bundle = new Bundle(upload.project, 'bundle', getTasksGraph())
        bundle.defaultArchiveTypes = JavaPluginConvention.DEFAULT_ARCHIVE_TYPES
        AbstractArchiveTask zip1 = bundle.zip(baseName: 'zip1')
        AbstractArchiveTask zip2 = bundle.zip(baseName: 'zip2')
        AbstractArchiveTask zip3 = bundle.zip(baseName: 'zip3')
        zip1.configurations('zip', 'zip1')
        zip2.configurations('zip2')
        zip2.publish = false
        zip3.configurations('zip', 'zip3')
        upload.bundles = [bundle]
        context.checking {
            one(dependencyManagerMock).publish(
                    expectedConfigurations + ['zip1', 'zip', 'zip3'],
                    upload.uploadResolvers,
                    false
            )
        }

        upload.execute()
    }
}
