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

package org.gradle.plugin.devel.plugins

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.internal.plugins.PluginDescriptor
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.plugin.devel.PluginDeclaration
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.junit.Rule

class JavaGradlePluginPluginTest extends AbstractProjectBuilderSpec {
    final ResettableOutputEventListener outputEventListener = new ResettableOutputEventListener()

    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    final static String NO_DESCRIPTOR_WARNING = JavaGradlePluginPlugin.NO_DESCRIPTOR_WARNING_MESSAGE.substring(4)
    final static String DECLARED_PLUGIN_MISSING_PREFIX = JavaGradlePluginPlugin.DECLARED_PLUGIN_MISSING_MESSAGE.substring(4).split('[%]')[0]
    final static String BAD_IMPL_CLASS_WARNING_PREFIX = JavaGradlePluginPlugin.BAD_IMPL_CLASS_WARNING_MESSAGE.substring(4).split('[%]')[0]
    final static String INVALID_DESCRIPTOR_WARNING_PREFIX = JavaGradlePluginPlugin.INVALID_DESCRIPTOR_WARNING_MESSAGE.substring(4).split('[%]')[0]

    def "PluginDescriptorCollectorAction correctly identifies plugin descriptor file"(String contents, String expectedPluginImpl, boolean expectedEmpty) {
        setup:
        List<PluginDescriptor> descriptors = new ArrayList<PluginDescriptor>()
        Action<FileCopyDetails> findPluginDescriptor = new JavaGradlePluginPlugin.PluginDescriptorCollectorAction(descriptors)
        File descriptorFile = project.file('test-plugin.properties')
        descriptorFile << contents
        FileCopyDetails stubDetails = Stub(FileCopyDetails) {
            getFile() >> descriptorFile
        }

        expect:
        findPluginDescriptor.execute(stubDetails)
        descriptors.isEmpty() == expectedEmpty
        descriptors.isEmpty() || descriptors.get(0).implementationClassName == expectedPluginImpl

        where:
        contents                             | expectedPluginImpl | expectedEmpty
        'implementation-class=xxx.SomeClass' | 'xxx.SomeClass'    | false
        'implementation-class='              | ''                 | false
        ''                                   | null               | true
    }

    def "ClassManifestCollector captures class name"() {
        setup:
        Set<String> classList = new HashSet<String>()
        Action<FileCopyDetails> classManifestCollector = new JavaGradlePluginPlugin.ClassManifestCollectorAction(classList)
        FileCopyDetails stubDetails = Stub(FileCopyDetails) {
            getRelativePath() >> { new RelativePath(true, 'com', 'xxx', 'TestPlugin.class') }
        }

        when:
        classManifestCollector.execute(stubDetails)

        then:
        classList.contains('com/xxx/TestPlugin.class')
    }

    def "PluginValidationAction finds fully qualified class"(List classList, String fqClass, boolean expectedValue) {
        setup:
        Action<Task> pluginValidationAction = new JavaGradlePluginPlugin.PluginValidationAction([], [], classList as Set<String>)

        expect:
        pluginValidationAction.hasFullyQualifiedClass(fqClass) == expectedValue

        where:
        classList                        | fqClass              | expectedValue
        ['com/xxx/TestPlugin.class']     | 'com.xxx.TestPlugin' | true
        ['TestPlugin.class']             | 'TestPlugin'         | true
        []                               | 'com.xxx.TestPlugin' | false
        ['com/xxx/yyy/TestPlugin.class'] | 'com.xxx.TestPlugin' | false
    }

    def "PluginValidationAction logs correct warning messages for broken plugins"(String impl, String implFile, String expectedMessage) {
        setup:
        Task stubTask = Stub(Task)
        List<PluginDescriptor> descriptors = []
        if (impl != null) {
            descriptors.add(Stub(PluginDescriptor) {
                _ * getPropertiesFileUrl() >> { new URL("file:///test-plugin-${impl}.properties") }
                _ * getImplementationClassName() >> { impl }
            })
        }
        Set<String> classes = new HashSet<String>()
        if (implFile) {
            classes.add(implFile)
        }
        Action<Task> pluginValidationAction = new JavaGradlePluginPlugin.PluginValidationAction([], descriptors, classes)
        outputEventListener.reset()

        expect:
        pluginValidationAction.execute(stubTask)
        expectedMessage == null || outputEventListener.toString().contains(expectedMessage)

        where:
        impl    | implFile      | expectedMessage
        null    | null          | NO_DESCRIPTOR_WARNING
        ''      | null          | INVALID_DESCRIPTOR_WARNING_PREFIX
        'x.y.z' | null          | BAD_IMPL_CLASS_WARNING_PREFIX
        'x.y.z' | 'z.class'     | BAD_IMPL_CLASS_WARNING_PREFIX
        'x.y.z' | 'x/y/z.class' | null
    }

    def "PluginValidationAction logs correct warning messages for missing plugins"(String id, String impl, String expectedMessage) {
        setup:
        Task stubTask = Stub(Task)
        List<PluginDescriptor> descriptors = []
        if (impl != null) {
            descriptors << Stub(PluginDescriptor) {
                getPropertiesFileUrl() >> { new URL("file:///test-plugin/${impl}.properties") }
            }
        }
        List<PluginDeclaration> declarations = []
        if (id) {
            declarations << Stub(PluginDeclaration) {
                getName() >> id
                getId() >> id
            }
        }
        Action<Task> pluginValidationAction = new JavaGradlePluginPlugin.PluginValidationAction(declarations, descriptors, [] as Set)
        outputEventListener.reset()

        expect:
        pluginValidationAction.execute(stubTask)
        expectedMessage == null || outputEventListener.toString().contains(expectedMessage)

        where:
        id      | impl      | expectedMessage
        'x.y.z' | 'a.b.c'   | DECLARED_PLUGIN_MISSING_PREFIX
        'x.y.z' | 'x.y.z'   | null
        null    | 'x.y.z'   | null
    }

    def "apply adds java plugin"() {
        when:
        project.pluginManager.apply(JavaGradlePluginPlugin)

        then:
        project.plugins.findPlugin(JavaPlugin)
    }

    def "apply adds gradleApi dependency to compile"() {
        when:
        project.pluginManager.apply(JavaGradlePluginPlugin)

        then:
        project.configurations
            .getByName(JavaGradlePluginPlugin.COMPILE_CONFIGURATION)
            .dependencies.find {
            it.files == project.dependencies.gradleApi().files
        }
    }

    def "apply configures filesMatching actions on jar spec"() {
        setup:
        project.pluginManager.apply(JavaPlugin)
        def Jar mockJarTask = mockJar(project)

        when:
        project.pluginManager.apply(JavaGradlePluginPlugin)

        then:
        1 * mockJarTask.filesMatching(JavaGradlePluginPlugin.PLUGIN_DESCRIPTOR_PATTERN, { it instanceof JavaGradlePluginPlugin.PluginDescriptorCollectorAction })
        1 * mockJarTask.filesMatching(JavaGradlePluginPlugin.CLASSES_PATTERN, { it instanceof JavaGradlePluginPlugin.ClassManifestCollectorAction })
    }

    def "apply configures doLast action on jar"() {
        setup:
        project.pluginManager.apply(JavaPlugin)
        def Jar mockJarTask = mockJar(project)

        when:
        project.pluginManager.apply(JavaGradlePluginPlugin)

        then:
        1 * mockJarTask.appendParallelSafeAction({ it instanceof JavaGradlePluginPlugin.PluginValidationAction })
    }

    def Jar mockJar(project) {
        def Jar mockJar = Mock(Jar) {
            _ * getName() >> { JavaGradlePluginPlugin.JAR_TASK }
            _ * getConventionMapping() >> { Stub(ConventionMapping) }
        }
        project.tasks.remove(project.tasks.getByName(JavaGradlePluginPlugin.JAR_TASK))
        project.tasks.add(mockJar)
        return mockJar
    }

    static class ResettableOutputEventListener implements OutputEventListener {
        final StringBuffer buffer = new StringBuffer()

        void reset() {
            buffer.delete(0, buffer.size())
        }

        @Override
        String toString() {
            return buffer.toString()
        }

        @Override
        synchronized void onOutput(OutputEvent event) {
            LogEvent logEvent = event as LogEvent
            buffer.append(logEvent.message)
        }
    }
}
