/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativecode.cdt.model

import spock.lang.*

import org.gradle.api.Project
import org.gradle.util.HelperUtil

// very loose test, but I'm not expecting it to stay around
@Ignore
class CprojectSettingsSpec extends Specification {

    Project project = HelperUtil.createRootProject()

    def descriptor = new CprojectDescriptor()
    def settings = new CprojectSettings()

    def "wire in includes"() {
        given:
        project.apply plugin: 'cpp-exe'
        settings.binary = project.executables.main
        descriptor.loadDefaults()

        expect:
        descriptor.getRootCppCompilerTools().each { compiler ->
            def includePathsOption = descriptor.getOrCreateIncludePathsOption(compiler)
            assert includePathsOption.listOptionValue.size() == 0
        }

        when:
        settings.applyTo(descriptor)
        def baos = new ByteArrayOutputStream()
        descriptor.store(baos)
        descriptor.load(new ByteArrayInputStream(baos.toByteArray()))

        then:
        descriptor.getRootCppCompilerTools().each { compiler ->
            def includePathsOption = descriptor.getOrCreateIncludePathsOption(compiler)
            assert includePathsOption.listOptionValue.size() == 1
        }
    }


}