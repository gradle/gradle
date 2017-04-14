/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.configuration

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.DependencyInjectingServiceLoader
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.progress.TestBuildOperationExecutor
import org.gradle.internal.resource.StringTextResource
import spock.lang.Specification
import spock.lang.Unroll

class ScriptPluginFactorySelectorTest extends Specification {

    def defaultScriptPluginFactory = Mock(ScriptPluginFactory)
    def serviceLoader = Mock(DependencyInjectingServiceLoader)
    def selector = new ScriptPluginFactorySelector(defaultScriptPluginFactory, serviceLoader, new TestBuildOperationExecutor())

    def scriptHandler = Mock(ScriptHandler)
    def targetScope = Mock(ClassLoaderScope)
    def baseScope = Mock(ClassLoaderScope)

    def defaultScriptPlugin = Mock(ScriptPlugin)

    def setup() {
        _ * defaultScriptPluginFactory.create(*_) >> defaultScriptPlugin
    }

    @Unroll
    def "selects default scripting support short circuiting provider lookup for #fileName"() {
        given:
        0 * serviceLoader._
        def scriptSource = scriptSourceFor(fileName)
        def target = new Object()

        when:
        def scriptPlugin = selector.create(scriptSource, scriptHandler, targetScope, baseScope, false)

        and:
        scriptPlugin.apply(target)

        then:
        1 * defaultScriptPlugin.source >> scriptSource
        1 * defaultScriptPlugin.apply(target)

        where:
        fileName << [Project.DEFAULT_BUILD_FILE, Settings.DEFAULT_SETTINGS_FILE, 'anything.dot.gradle']
    }

    def "given no scripting provider then falls back to default scripting support for any extension"() {
        given:
        1 * serviceLoader.load(*_) >> []
        def scriptSource = scriptSourceFor('build.any')
        def target = new Object()

        when:
        def scriptPlugin = selector.create(scriptSource, scriptHandler, targetScope, baseScope, false)

        and:
        scriptPlugin.apply(target)

        then:
        1 * defaultScriptPlugin.source >> scriptSource
        1 * defaultScriptPlugin.apply(target)
    }

    def "given matching scripting provider then selects it"() {
        given:
        def fooScriptPlugin = Mock(ScriptPlugin)
        def fooScriptPluginFactoryProvider = scriptPluginFactoryProviderFor('foo', fooScriptPlugin)
        1 * serviceLoader.load(*_) >> [fooScriptPluginFactoryProvider]
        def scriptSource = scriptSourceFor('build.foo')
        def target = new Object()

        when:
        def scriptPlugin = selector.create(scriptSource, scriptHandler, targetScope, baseScope, false)

        and:
        scriptPlugin.apply(target)

        then:
        1 * fooScriptPlugin.source >> scriptSource
        1 * fooScriptPlugin.apply(target)
    }

    private ScriptSource scriptSourceFor(String fileName, String content = '') {
        def resource = new StringTextResource(fileName, content)
        return Mock(ScriptSource) {
            getFileName() >> fileName
            getResource() >> resource
        }
    }

    private ScriptPluginFactoryProvider scriptPluginFactoryProviderFor(String extension, ScriptPlugin scriptPluginMock) {
        def scriptPluginFactory = Mock(ScriptPluginFactory) {
            create(*_) >> scriptPluginMock
        }
        return Mock(ScriptPluginFactoryProvider) {
            getFor(_) >> { String fileName -> fileName.endsWith(extension) ? scriptPluginFactory : null }
        }
    }
}
