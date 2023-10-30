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

package org.gradle.api.plugins

import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class DefaultBasePluginConventionTest extends AbstractProjectBuilderSpec {

    private BasePluginConvention convention
    private BasePluginExtension extension

    def setup() {
        project.pluginManager.apply(BasePlugin)
        convention = project.convention.plugins.base
        extension =  project.extensions.base
    }

    def "default values"() {
        expect:
        convention.archivesBaseName == project.name
        convention.distsDirName == 'distributions'
        convention.distsDirectory.getAsFile().get() == project.layout.buildDirectory.dir('distributions').get().asFile
        convention.libsDirName == 'libs'
        convention.libsDirectory.getAsFile().get() == project.layout.buildDirectory.dir('libs').get().asFile
    }

    def "dirs relative to build dir"() {
        when:
        project.buildDir = project.file('mybuild')
        convention.distsDirName = 'mydists'
        convention.libsDirName = 'mylibs'

        then:
        convention.distsDirectory.getAsFile().get() == project.file('mybuild/mydists')
        convention.libsDirectory.getAsFile().get() == project.file('mybuild/mylibs')
        extension.distsDirectory.getAsFile().get() == project.file('mybuild/mydists')
        extension.libsDirectory.getAsFile().get() == project.file('mybuild/mylibs')
    }

    def "dirs are cached properly"() {
        when:
        project.buildDir = project.file('mybuild')
        convention.distsDirName = 'mydists'

        then:
        convention.distsDirectory.getAsFile().get() == project.file('mybuild/mydists')
        extension.distsDirectory.getAsFile().get() == project.file('mybuild/mydists')

        when:
        convention.libsDirName = 'mylibs'

        then:
        convention.libsDirectory.getAsFile().get() == project.file('mybuild/mylibs')
        extension.libsDirectory.getAsFile().get() == project.file('mybuild/mylibs')

        when:
        convention.distsDirName = 'mydists2'

        then:
        convention.distsDirectory.getAsFile().get() == project.file('mybuild/mydists2')
        extension.distsDirectory.getAsFile().get() == project.file('mybuild/mydists2')

        when:
        convention.libsDirName = 'mylibs2'

        then:
        convention.libsDirectory.getAsFile().get() == project.file('mybuild/mylibs2')
        extension.libsDirectory.getAsFile().get() == project.file('mybuild/mylibs2')

        when:
        project.buildDir = project.file('mybuild2')

        then:
        convention.libsDirectory.getAsFile().get() == project.file('mybuild2/mylibs2')
        extension.libsDirectory.getAsFile().get() == project.file('mybuild2/mylibs2')

        when:
        project.buildDir = project.file('mybuild')
        convention.distsDirName = 'mydists'

        then:
        convention.distsDirectory.getAsFile().get() == project.file('mybuild/mydists')
        extension.distsDirectory.getAsFile().get() == project.file('mybuild/mydists')

        when:
        convention.libsDirName = 'mylibs'

        then:
        convention.libsDirectory.getAsFile().get() == project.file('mybuild/mylibs')
        extension.libsDirectory.getAsFile().get() == project.file('mybuild/mylibs')

        when:
        convention.distsDirName = 'mydists2'

        then:
        convention.distsDirectory.getAsFile().get() == project.file('mybuild/mydists2')
        extension.distsDirectory.getAsFile().get() == project.file('mybuild/mydists2')

        when:
        convention.libsDirName = 'mylibs2'

        then:
        convention.libsDirectory.getAsFile().get() == project.file('mybuild/mylibs2')
        extension.libsDirectory.getAsFile().get() == project.file('mybuild/mylibs2')

        when:
        project.buildDir = project.file('mybuild2')

        then:
        convention.libsDirectory.getAsFile().get() == project.file('mybuild2/mylibs2')
        extension.libsDirectory.getAsFile().get() == project.file('mybuild2/mylibs2')
    }
}
