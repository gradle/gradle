/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.plugins.devel

import org.gradle.api.Action
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.util.TestUtil
import spock.lang.Ignore
import spock.lang.Specification

class JavaGradlePluginPluginTest extends Specification {
    def project = TestUtil.builder().withName("plugin").build()

    def "FindPluginDescriptor correctly identifies plugin descriptor file" (String contents, String expectedPluginImpl, boolean expectedFoundValue) {
        setup:
        Action<FileCopyDetails> findPluginDescriptor = new JavaGradlePluginPlugin.FindPluginDescriptorAction()
        FileCopyDetails stubDetails = Stub(FileCopyDetails) {
            open() >> new ByteArrayInputStream(contents.bytes)
        }

        expect:
        findPluginDescriptor.execute(stubDetails)
        findPluginDescriptor.foundDescriptor == expectedFoundValue
        findPluginDescriptor.pluginImplementation == expectedPluginImpl

        where:
        contents                             | expectedPluginImpl | expectedFoundValue
        'implementation-class=xxx.SomeClass' | 'xxx.SomeClass'    | true
        'implementation-class='              | null               | false
        ''                                   | null               | false
    }

    def "ClassManifestCollector captures class name" () {
        setup:
        Action<FileCopyDetails> classManifestCollector = new JavaGradlePluginPlugin.ClassManifestCollectorAction()
        FileCopyDetails stubDetails = Stub(FileCopyDetails) {
            getRelativePath() >> { new RelativePath(true, 'com', 'xxx', 'TestPlugin.class')}
        }

        when:
        classManifestCollector.execute(stubDetails)

        then:
        classManifestCollector.classList.contains('com/xxx/TestPlugin.class')
    }

    def "ClassManifestCollector finds fully qualified class" (List classList, String fqClass, boolean expectedValue) {
        setup:
        Action<FileCopyDetails> classManifestCollector = new JavaGradlePluginPlugin.ClassManifestCollectorAction(classList as Collection<String>)

        expect:
        classManifestCollector.hasFullyQualifiedClass(fqClass) == expectedValue

        where:
        classList                           | fqClass              | expectedValue
        [ 'com/xxx/TestPlugin.class' ]      | 'com.xxx.TestPlugin' | true
        [ 'TestPlugin.class' ]              | 'TestPlugin'         | true
        [ ]                                 | 'com.xxx.TestPlugin' | false
        [ 'com/xxx/yyy/TestPlugin.class']   | 'com.xxx.TestPlugin' | false
    }

    def "apply adds java plugin" () {
        when:
        project.plugins.apply(JavaGradlePluginPlugin)

        then:
        project.plugins.findPlugin(JavaPlugin)
    }

    def "apply adds gradleApi dependency to compile" () {
        when:
        project.plugins.apply(JavaGradlePluginPlugin)

        then:
        project.configurations
                .getByName(JavaGradlePluginPlugin.COMPILE_CONFIGURATION)
                .dependencies.find {
                    project.dependencies.gradleApi().source.files.containsAll(it.source.files)
                }
    }

    def "apply configures filesMatching actions on jar spec" () {
        setup:
        project.plugins.apply(JavaPlugin)
        def Jar mockJarTask = mockJar(project)

        when:
        project.plugins.apply(JavaGradlePluginPlugin)

        then:
        1 * mockJarTask.filesMatching(JavaGradlePluginPlugin.PLUGIN_DESCRIPTOR_PATTERN, { it instanceof JavaGradlePluginPlugin.FindPluginDescriptorAction })
        1 * mockJarTask.filesMatching(JavaGradlePluginPlugin.CLASSES_PATTERN, { it instanceof JavaGradlePluginPlugin.ClassManifestCollectorAction })
    }

    def "apply configures doLast action on jar" () {
        setup:
        project.plugins.apply(JavaPlugin)
        def Jar mockJarTask = mockJar(project)

        when:
        project.plugins.apply(JavaGradlePluginPlugin)

        then:
        1 * mockJarTask.doLast(_)
    }

    @Ignore
    def Jar mockJar(project) {
        def Jar mockJar = Mock(Jar) {
            _ * getName() >> { JavaGradlePluginPlugin.JAR_TASK }
            _ * getConventionMapping() >> { Stub(ConventionMapping) }
        }
        project.tasks.remove(project.tasks.getByName(JavaGradlePluginPlugin.JAR_TASK))
        project.tasks.add(mockJar)
        return mockJar
    }
}
