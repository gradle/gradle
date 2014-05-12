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

package org.gradle.plugin.use

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.plugin.use.resolve.portal.PluginPortalTestServer
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.junit.Rule

class PluginUseClassloaderIsolationIntegrationSpec extends AbstractIntegrationSpec {

    private static final String ID = "org.gradle.test"
    private static final String GROUP = "org.gradle.test"
    private static final String NAME = "plugin"
    private static final String VERSION = "1.0"
    private static final String PLUGIN_MESSAGE = 'hello from plugin task'
    private static final String USE_PLUGIN = """
        plugins {
            id "$ID" version "$VERSION"
        }
    """
    private static final TASK_CLASS_SIMPLE_NAME = "EchoTask"
    private static final TASK_CLASS_NAME = "org.gradle.test.$TASK_CLASS_SIMPLE_NAME"
    private static final ADD_ECHO_TASK = "task echo(type: $TASK_CLASS_NAME) {}"
    private static final FAILED_TO_LOAD_CLASS_MESSAGE = "failed to load class $TASK_CLASS_NAME"
    private static final LOAD_ECHO_CLASS = """
        try {
            getClass().classLoader.loadClass('$TASK_CLASS_NAME')
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("$FAILED_TO_LOAD_CLASS_MESSAGE")
        }
    """

    @Rule
    PluginPortalTestServer portal = new PluginPortalTestServer(executer, mavenRepo)

    PluginBuilder pb = new PluginBuilder(file("plugin"))

    def setup() {
        portal.start()
        publishEchoPlugin()
    }

    def publishEchoPlugin() {
        pb.groovy("${TASK_CLASS_SIMPLE_NAME}.groovy") << """
            package org.gradle.test

            class $TASK_CLASS_SIMPLE_NAME extends ${DefaultTask.name} {
                @${TaskAction.name}
                void doEcho() {
                    println "$PLUGIN_MESSAGE"
                }
            }
        """

        pb.addPlugin("", ID)
        publishPlugin(pb)
    }

    void publishPlugin(PluginBuilder pluginBuilder) {
        def module = portal.m2repo.module(GROUP, NAME, VERSION)
        module.allowAll()
        def artifact = module.artifact([:])
        module.publish()
        pluginBuilder.publishTo(executer, artifact.file)
        portal.expectPluginQuery(ID, VERSION, GROUP, NAME, VERSION)
    }

    void "can use plugin classes in script"() {
        given:
        buildScript """
          $USE_PLUGIN
          $ADD_ECHO_TASK
        """

        when:
        succeeds "echo"

        then:
        output.contains PLUGIN_MESSAGE
    }

    void "implementation classes of plugin in plugins { } block are not visible to any scripts"() {
        given:
        buildScript """
            $USE_PLUGIN
            $LOAD_ECHO_CLASS
            apply from: "script1.gradle"
        """

        file("script1.gradle") << "$LOAD_ECHO_CLASS"

        when:
        fails "tasks"

        then:
        failure.assertHasFileName("Script '${file('script1.gradle').absolutePath}'")
        failure.assertHasCause(FAILED_TO_LOAD_CLASS_MESSAGE)
    }

    void "implementation classes of plugin in plugins { } block are not visible to subprojects"() {
        given:
        settingsFile << "include 'sub'"

        buildScript """
            $USE_PLUGIN
            $LOAD_ECHO_CLASS
        """

        file("sub/build.gradle") << "$LOAD_ECHO_CLASS"

        when:
        fails "tasks"

        then:
        failure.assertHasFileName("Build file '${file('sub/build.gradle').absolutePath}'")
        failure.assertHasCause(FAILED_TO_LOAD_CLASS_MESSAGE)
    }

    void "plugin has no visibility of classes added by other plugin"() {
        given:
        def pb2 = new PluginBuilder(file("plugin2"))
        def otherId = "org.gradle.other-plugin"
        pb2.addPlugin("$LOAD_ECHO_CLASS", otherId)
        portal.expectPluginQuery(otherId, VERSION, GROUP, "other-plugin", VERSION)
        def module = portal.m2repo.module(GROUP, "other-plugin", VERSION)
        pb2.publishTo(executer, module.artifactFile)
        module.allowAll()

        buildScript """
            $USE_PLUGIN
            plugins {
                id "$otherId" version "$VERSION"
            }
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause(FAILED_TO_LOAD_CLASS_MESSAGE)
    }

}
