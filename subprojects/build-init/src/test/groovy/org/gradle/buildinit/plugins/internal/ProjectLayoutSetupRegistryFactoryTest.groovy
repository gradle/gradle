/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider
import org.gradle.api.internal.file.FileResolver
import spock.lang.Specification
import spock.lang.Unroll

class ProjectLayoutSetupRegistryFactoryTest extends Specification {

    ProjectLayoutSetupRegistryFactory projectLayoutSetupRegistry
    DocumentationRegistry documentationRegistry
    MavenSettingsProvider mavenSettingsProvider
    FileResolver fileResolver

    def setup() {
        fileResolver = Mock()
        projectLayoutSetupRegistry = new ProjectLayoutSetupRegistryFactory(mavenSettingsProvider, documentationRegistry, fileResolver);
    }

    @Unroll
    def "supports '#type' project descriptor type"() {
        when:
        ProjectInitDescriptor descriptor = projectLayoutSetupRegistry.createProjectLayoutSetupRegistry().get(type)

        then:
        descriptor != null
        descriptor.class == clazz

        where:
        type                           | clazz
        BuildInitTypeIds.POM          | PomProjectInitDescriptor.class
        BuildInitTypeIds.BASIC        | BasicProjectInitDescriptor.class
        BuildInitTypeIds.JAVA_LIBRARY | JavaLibraryProjectInitDescriptor.class
    }
}
