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

import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.AppenderBase
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.internal.plugins.PluginDescriptor
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.logging.ConfigureLogging
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification

class JavaGradlePluginPluginTest extends Specification {
    final ResettableAppender appender = new ResettableAppender()
    @Rule final ConfigureLogging logging = new ConfigureLogging(appender)

    final static String NO_DESCRIPTOR_WARNING = JavaGradlePluginPlugin.NO_DESCRIPTOR_WARNING_MESSAGE
    final static String BAD_IMPL_CLASS_WARNING_PREFIX = JavaGradlePluginPlugin.BAD_IMPL_CLASS_WARNING_MESSAGE.split('%')[0]
    final static String INVALID_DESCRIPTOR_WARNING_PREFIX = JavaGradlePluginPlugin.INVALID_DESCRIPTOR_WARNING_MESSAGE.split('%')[0]

    def project = TestUtil.builder().withName("plugin").build()

    def "FindPluginDescriptor correctly identifies plugin descriptor file" (String contents, String expectedPluginImpl, boolean expectedEmpty) {
        setup:
        Action<FileCopyDetails> findPluginDescriptor = new JavaGradlePluginPlugin.FindPluginDescriptorAction()
        File descriptorFile = project.file('test-plugin.properties')
        descriptorFile << contents
        FileCopyDetails stubDetails = Stub(FileCopyDetails) {
            getFile() >> descriptorFile
        }

        expect:
        findPluginDescriptor.execute(stubDetails)
        findPluginDescriptor.descriptors.isEmpty() == expectedEmpty
        findPluginDescriptor.descriptors.isEmpty() ||
                findPluginDescriptor.descriptors.get(0).implementationClassName == expectedPluginImpl

        where:
        contents                             | expectedPluginImpl | expectedEmpty
        'implementation-class=xxx.SomeClass' | 'xxx.SomeClass'    | false
        'implementation-class='              | ''                 | false
        ''                                   | null               | true
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

   def "PluginValidationAction logs correct warning messages" (String impl, boolean shouldFindClass, String expectedMessage) {
        setup:
        Task stubTask = Stub(Task)
        Action<FileCopyDetails> stubFindPluginDescriptor = Stub(JavaGradlePluginPlugin.FindPluginDescriptorAction) {
            _ * getDescriptors() >> {
                 List<PluginDescriptor> descriptors = []
                if (impl != null) {
                    descriptors.add(Stub(PluginDescriptor) {
                        _ * toString() >> { "file:///test-plugin-${impl}.properties" }
                        _ * getImplementationClassName() >> { impl }
                    })
                }
                return descriptors
            }
        }
        Action<FileCopyDetails> stubClassManifestCollector = Stub(JavaGradlePluginPlugin.ClassManifestCollectorAction) {
            _ * hasFullyQualifiedClass(_) >> { shouldFindClass }
        }
        Action<Task> pluginValidationAction = new JavaGradlePluginPlugin.PluginValidationAction(stubFindPluginDescriptor, stubClassManifestCollector)
        appender.reset()

        expect:
        pluginValidationAction.execute(stubTask)
        expectedMessage == null || appender.toString().contains(expectedMessage)

        where:
        impl    | shouldFindClass | expectedMessage
        null    | false           | NO_DESCRIPTOR_WARNING
        ''      | false           | INVALID_DESCRIPTOR_WARNING_PREFIX
        'x.y.z' | false           | BAD_IMPL_CLASS_WARNING_PREFIX
        'x.y.z' | true            | null
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
        1 * mockJarTask.doLast({ it instanceof JavaGradlePluginPlugin.PluginValidationAction})
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

    static class ResettableAppender extends AppenderBase<LoggingEvent> {
        final StringBuffer buffer = new StringBuffer()

        synchronized void doAppend(LoggingEvent e) {
            append(e)
        }

        @Override
        protected void append(LoggingEvent eventObject) {
            buffer.append(eventObject.formattedMessage)
        }

        void reset() {
            buffer.delete(0,buffer.size())
        }

        @Override
        String toString() {
            return buffer.toString()
        }
    }
}
