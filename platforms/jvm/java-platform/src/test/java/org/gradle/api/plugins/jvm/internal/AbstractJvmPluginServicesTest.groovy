/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.plugins.Convention
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.Callable

abstract class AbstractJvmPluginServicesTest extends Specification {
    def configurations = Mock(ConfigurationContainer)
    def tasks = Mock(TaskContainerInternal)
    def project = Stub(ProjectInternal) {
        getObjects() >> TestUtil.objectFactory()
        getProviders() >> TestUtil.providerFactory()
        provider(_ as Callable<Object>) >> {
            Callable producer -> TestUtil.providerFactory().provider(
                {
                    producer.call()
                }
            )
        }
        getLayout() >> Stub(ProjectLayout) {
            getBuildDirectory() >> TestUtil.objectFactory().directoryProperty()
        }
        getConvention() >> Stub(Convention) {
            findPlugin(_) >> null
        }
        getExtensions() >> Stub(ExtensionContainerInternal) {
            getByType(JavaPluginExtension) >> Mock(JavaPluginExtension)
        }
        getTasks() >> tasks
        getConfigurations() >> configurations
    }

    @Subject
    DefaultJvmPluginServices services = new DefaultJvmPluginServices(
        TestUtil.objectFactory(),
        TestUtil.providerFactory(),
        TestUtil.instantiatorFactory().decorateScheme().instantiator(),
        project
    )
}
