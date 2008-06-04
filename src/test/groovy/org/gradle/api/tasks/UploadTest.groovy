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
import org.gradle.api.Task
import org.gradle.api.internal.dependencies.DefaultDependencyManager
import org.gradle.api.internal.dependencies.ModuleDescriptorConverter
import org.gradle.api.dependencies.ResolverContainer
import org.gradle.api.plugins.JavaConvention
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Bundle
import org.gradle.util.HelperUtil
import org.gradle.api.dependencies.ResolverContainer

/**
 * @author Hans Dockter
 */
class UploadTest extends AbstractConventionTaskTest {
    static final String RESOLVER_NAME_1 = 'resolver1'
    static final String RESOLVER_NAME_2 = 'resolver2'

    Upload upload
    File projectRootDir
    MockFor dependenciesMocker
    MockFor ivyMocker
    MockFor publishEngineMocker
    MockFor moduleDescriptorConverterMocker

    void setUp() {
        super.setUp()
        upload = new Upload(project, AbstractTaskTest.TEST_TASK_NAME)
        (projectRootDir = new File(HelperUtil.makeNewTestDir(), 'root')).mkdir()
        dependenciesMocker = new MockFor(DefaultDependencyManager)
        ivyMocker = new MockFor(Ivy)
        publishEngineMocker = new MockFor(PublishEngine)
        moduleDescriptorConverterMocker = new MockFor(ModuleDescriptorConverter)
    }

    Task getTask() {
        upload
    }

    void testUpload() {
        assert !upload.uploadModuleDescriptor
        assert upload.uploadResolvers
        assertEquals([], upload.configurations)
        assertEquals([] as Set, upload.bundles)
    }

    void testUploading() {
        upload.uploadResolvers.add([name: RESOLVER_NAME_1, url: 'http://www.url1.com'])
        upload.uploadResolvers.add([name: RESOLVER_NAME_2, url: 'http://www.url2.com'])
        List expectedConfigurations = ['conf1']
        upload.configurations = expectedConfigurations
        upload.project = HelperUtil.createRootProject(projectRootDir)
        upload.project.dependencies = new DefaultDependencyManager()
        Bundle bundle = new Bundle(upload.project, 'bundle')
        bundle.tasksBaseName = 'basename'
        bundle.defaultArchiveTypes = JavaConvention.DEFAULT_ARCHIVE_TYPES
        AbstractArchiveTask zip1 = bundle.zip(baseName: 'zip1')
        AbstractArchiveTask zip2 = bundle.zip(baseName: 'zip2')
        AbstractArchiveTask zip3 = bundle.zip(baseName: 'zip3')
        zip1.configurations('zip', 'zip1')
        zip2.configurations('zip2')
        zip2.publish = false
        zip3.configurations('zip', 'zip3')
        upload.bundles = [bundle]
        dependenciesMocker.demand.publish(1..1) {List configurations, ResolverContainer resolvers, boolean uploadModuleDescriptor ->
            assertEquals(expectedConfigurations + ['zip', 'zip1', 'zip3'] as Set, configurations as Set)
            assert resolvers.is(upload.uploadResolvers)
            assert !uploadModuleDescriptor
        }
        dependenciesMocker.use(upload.project.dependencies) {
            upload.execute()
        }
    }
}
