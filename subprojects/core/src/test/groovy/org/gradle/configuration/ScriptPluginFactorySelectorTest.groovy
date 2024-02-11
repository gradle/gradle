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
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.internal.code.DefaultUserCodeApplicationContext
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.resource.StringTextResource
import org.gradle.groovy.scripts.internal.ScriptSourceListener
import spock.lang.Specification

class ScriptPluginFactorySelectorTest extends Specification {

    def providerInstantiator = Mock(ScriptPluginFactorySelector.ProviderInstantiator)
    def defaultScriptPluginFactory = Mock(ScriptPluginFactory)
    def remoteScriptListener = Mock(ScriptSourceListener)
    def selector = new ScriptPluginFactorySelector(defaultScriptPluginFactory, providerInstantiator, new TestBuildOperationExecutor(), new DefaultUserCodeApplicationContext(), remoteScriptListener)

    def scriptHandler = Mock(ScriptHandler)
    def targetScope = Mock(ClassLoaderScope)
    def baseScope = Mock(ClassLoaderScope)

    def defaultScriptPlugin = Mock(ScriptPlugin)

    def setup() {
        _ * defaultScriptPluginFactory.create(*_) >> defaultScriptPlugin
    }

    def "selects default scripting support short circuiting provider lookup for #fileName"() {
        given:
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

    def "given `.gradle.kts` file extension, it selects scripting provider then selects it"() {
        given:
        def fooScriptPlugin = Mock(ScriptPlugin)
        def fooScriptPluginFactory = scriptPluginFactoryFor(fooScriptPlugin)
        1 * providerInstantiator.instantiate('org.gradle.kotlin.dsl.provider.KotlinScriptPluginFactory') >> fooScriptPluginFactory

        def scriptSource = scriptSourceFor('build.gradle.kts')
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

    private ScriptPluginFactory scriptPluginFactoryFor(ScriptPlugin scriptPluginMock) {
        return Mock(ScriptPluginFactory) {
            create(*_) >> scriptPluginMock
        }
    }
}
