/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.plugins

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.configuration.ScriptPlugin
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.internal.resource.TextUriResourceLoader
import spock.lang.Specification

class DefaultObjectConfigurationActionTest extends Specification {
    Object target = new Object()
    URI file = new URI('script:something')

    def resolver = Mock(FileResolver)
    def scriptPluginFactory = Mock(ScriptPluginFactory)
    def scriptHandlerFactory = Mock(ScriptHandlerFactory)
    def scriptHandler = Mock(ScriptHandlerInternal)
    def scriptCompileScope = Mock(ClassLoaderScope)
    def parentCompileScope = Mock(ClassLoaderScope)
    def textResourceLoaderFactory = Mock(TextUriResourceLoader.Factory)
    def configurer = Mock(ScriptPlugin)

    DefaultObjectConfigurationAction action = new DefaultObjectConfigurationAction(resolver, scriptPluginFactory, scriptHandlerFactory, parentCompileScope, textResourceLoaderFactory, target)

    def setup() {
       textResourceLoaderFactory.create(_) >> Mock(TextUriResourceLoader)
    }

    void doesNothingWhenNothingSpecified() {
        expect:
        action.execute()
    }

    void appliesScriptsToDefaultTargetObject() {
        given:
        1 * resolver.resolveUri('script') >> file
        1 * parentCompileScope.createChild("script-$file", null) >> scriptCompileScope
        1 * scriptHandlerFactory.create(_, scriptCompileScope, _) >> scriptHandler
        1 * scriptPluginFactory.create(_, scriptHandler, scriptCompileScope, parentCompileScope, false) >> configurer

        when:
        action.from('script')

        then:
        action.execute()
    }

    void appliesScriptsToTargetObjects() {
        when:
        Object target1 = new Object()
        Object target2 = new Object()
        1 * resolver.resolveUri('script') >> file
        1 * scriptHandlerFactory.create(_, scriptCompileScope, _) >> scriptHandler
        1 * scriptPluginFactory.create(_, scriptHandler, scriptCompileScope, parentCompileScope, false) >> configurer
        1 * configurer.apply(target1)
        1 * configurer.apply(target2)
        1 * parentCompileScope.createChild("script-$file", null) >> scriptCompileScope

        then:
        action.from('script')
        action.to(target1)
        action.to(target2)
        action.execute()
    }

    void flattensCollections() {
        when:
        Object target1 = new Object()
        Object target2 = new Object()
        1 * resolver.resolveUri('script') >> file
        1 * scriptHandlerFactory.create(_, scriptCompileScope, _) >> scriptHandler
        1 * scriptPluginFactory.create(_, scriptHandler, scriptCompileScope, parentCompileScope, false) >> configurer
        1 * configurer.apply(target1)
        1 * configurer.apply(target2)
        1 * parentCompileScope.createChild("script-$file", null) >> scriptCompileScope

        then:
        action.from('script')
        action.to([[target1], target2])
        action.execute()
    }

}

