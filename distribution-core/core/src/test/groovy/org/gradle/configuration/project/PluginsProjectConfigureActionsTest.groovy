/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.configuration.project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.service.DefaultServiceLocator
import spock.lang.Specification

class PluginsProjectConfigureActionsTest extends Specification {
    final def pluginsClassLoader = Mock(ClassLoader)

    private PluginsProjectConfigureActions createActions() {
        PluginsProjectConfigureActions.from(new DefaultServiceLocator(pluginsClassLoader))
    }

    def "executes all implicit configuration actions"() {
        def project = Mock(ProjectInternal)

        when:
        def evaluator = createActions()
        evaluator.execute(project)
        evaluator.execute(project)

        then:
        1 * pluginsClassLoader.getResources('META-INF/services/org.gradle.configuration.project.ProjectConfigureAction') >> resources()
        1 * pluginsClassLoader.loadClass('ConfigureActionClass') >> TestConfigureAction
        2 * project.setVersion(12)
        0 * _._
    }

    def resources() {
        URLStreamHandler handler = Mock()
        URLConnection connection = Mock()
        URL url = new URL("custom", "host", 12, "file", handler)
        _ * handler.openConnection(url) >> connection
        _ * connection.getInputStream() >> new ByteArrayInputStream("ConfigureActionClass".bytes)
        return Collections.enumeration([url])
    }

    static class TestConfigureAction implements ProjectConfigureAction {
        void execute(ProjectInternal project) {
            project.version = 12
        }
    }
}
