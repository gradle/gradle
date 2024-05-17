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
import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.internal.plugins.PluginDescriptor
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.plugin.devel.PluginDeclaration
import org.gradle.plugin.use.internal.DefaultPluginId
import org.gradle.plugin.use.resolve.internal.local.PluginPublication
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.GradleVersion
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
        def actionsState = new JavaGradlePluginPlugin.PluginValidationActionsState()
        Action<FileCopyDetails> findPluginDescriptor = new JavaGradlePluginPlugin.PluginDescriptorCollectorAction(actionsState)
        File descriptorFile = project.file('test-plugin.properties')
        descriptorFile << contents
        FileCopyDetails stubDetails = Stub(FileCopyDetails) {
            getFile() >> descriptorFile
        }

        expect:
        findPluginDescriptor.execute(stubDetails)
        actionsState.collectedDescriptors.isEmpty() == expectedEmpty
        actionsState.collectedDescriptors.isEmpty() || actionsState.collectedDescriptors.get(0).implementationClassName == expectedPluginImpl

        where:
        contents                             | expectedPluginImpl | expectedEmpty
        'implementation-class=xxx.SomeClass' | 'xxx.SomeClass'    | false
        'implementation-class='              | ''                 | false
        ''                                   | null               | true
    }

    def "ClassManifestCollector captures class name"() {
        setup:
        def actionsState = new JavaGradlePluginPlugin.PluginValidationActionsState()
        Action<FileCopyDetails> classManifestCollector = new JavaGradlePluginPlugin.ClassManifestCollectorAction(actionsState)
        FileCopyDetails stubDetails = Stub(FileCopyDetails) {
            getRelativePath() >> { new RelativePath(true, 'com', 'xxx', 'TestPlugin.class') }
        }

        when:
        classManifestCollector.execute(stubDetails)

        then:
        actionsState.collectedClasses.contains('com/xxx/TestPlugin.class')
    }

    def "PluginValidationAction finds fully qualified class"() {
        setup:
        Action<Task> pluginValidationAction = new JavaGradlePluginPlugin.PluginValidationAction(null, new JavaGradlePluginPlugin.PluginValidationActionsState([], classList as Set<String>))

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
        def declarations = Mock(Provider) {
            get() >> []
        }
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
        Action<Task> pluginValidationAction = new JavaGradlePluginPlugin.PluginValidationAction(declarations, new JavaGradlePluginPlugin.PluginValidationActionsState(descriptors, classes))
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
        Provider<Collection<PluginDeclaration>> declarationsProvider = Mock(Provider) {
            get() >> declarations
        }
        Action<Task> pluginValidationAction = new JavaGradlePluginPlugin.PluginValidationAction(declarationsProvider, new JavaGradlePluginPlugin.PluginValidationActionsState(descriptors, [] as Set))
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

    def "apply adds java-library plugin"() {
        when:
        project.pluginManager.apply(JavaGradlePluginPlugin)

        then:
        project.plugins.findPlugin(JavaLibraryPlugin)
        project.plugins.findPlugin(JavaPlugin)
    }

    def "apply adds gradleApi dependency to api"() {
        when:
        project.pluginManager.apply(JavaGradlePluginPlugin)

        then:
        project.configurations
            .getByName(JavaGradlePluginPlugin.API_CONFIGURATION)
            .dependencies.find {
            it.files == project.dependencies.gradleApi().files
        }
    }

    def "creates tasks with group and description"() {
        when:
        project.pluginManager.apply(JavaGradlePluginPlugin)

        then:
        def pluginUnderTestMetadata = project.tasks.getByName(JavaGradlePluginPlugin.PLUGIN_UNDER_TEST_METADATA_TASK_NAME)
        pluginUnderTestMetadata.group == JavaGradlePluginPlugin.PLUGIN_DEVELOPMENT_GROUP
        pluginUnderTestMetadata.description == JavaGradlePluginPlugin.PLUGIN_UNDER_TEST_METADATA_TASK_DESCRIPTION

        def pluginDescriptors = project.tasks.getByName(JavaGradlePluginPlugin.GENERATE_PLUGIN_DESCRIPTORS_TASK_NAME)
        pluginDescriptors.group == JavaGradlePluginPlugin.PLUGIN_DEVELOPMENT_GROUP
        pluginDescriptors.description == JavaGradlePluginPlugin.GENERATE_PLUGIN_DESCRIPTORS_TASK_DESCRIPTION

        def validateTask = project.tasks.getByName(JavaGradlePluginPlugin.VALIDATE_PLUGINS_TASK_NAME)
        validateTask.group == JavaGradlePluginPlugin.PLUGIN_DEVELOPMENT_GROUP
        validateTask.description == JavaGradlePluginPlugin.VALIDATE_PLUGIN_TASK_DESCRIPTION
    }

    def "registers local publication for each plugin"() {
        when:
        project.pluginManager.apply(JavaGradlePluginPlugin)
        project.gradlePlugin {
            plugins {
                a { id = "a.plugin" }
                b { id = "b.plugin" }
            }
        }

        then:
        def publications = project.services.get(ProjectPublicationRegistry).getPublications(PluginPublication, project.identityPath)
        publications.size() == 2
        publications[0].pluginId == DefaultPluginId.of("a.plugin")
        publications[1].pluginId == DefaultPluginId.of("b.plugin")
    }

    def "sets Gradle plugin API version attribute on classpath of all source sets"() {
        when: "the plugin is applied and a custom source set is created"
        project.pluginManager.apply(JavaGradlePluginPlugin)
        def sourceSets = project.extensions.getByType(JavaPluginExtension).sourceSets
        sourceSets.create("other")

        then: "the Gradle plugin API version attribute should be set on the classpath configurations of all source sets but no other configurations"
        def classpathConfigurations = sourceSets
            .collectMany { [it.compileClasspathConfigurationName, it.runtimeClasspathConfigurationName] }
            .collect(project.configurations::getByName)

        classpathConfigurations.every {
            it.attributes.getAttribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE).name == GradleVersion.current().getVersion()
        }
        project.configurations.minus(classpathConfigurations).every {
            it.attributes.getAttribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE) == null
        }
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
