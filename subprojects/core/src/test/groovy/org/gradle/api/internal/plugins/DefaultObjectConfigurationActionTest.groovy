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
import org.gradle.configuration.ScriptApplicator
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.resource.TextResourceLoader
import org.junit.Test
import spock.lang.Specification

class DefaultObjectConfigurationActionTest extends Specification {
    def defaultTarget = new Object()
    def file = new URI('script:something')

    def resolver = Mock(FileResolver)
    def scriptApplicator = Mock(ScriptApplicator)
    def scriptCompileScope = Mock(ClassLoaderScope)
    def parentCompileScope = Mock(ClassLoaderScope)
    def textResourceLoader = Mock(TextResourceLoader)

    def action = new DefaultObjectConfigurationAction(resolver, scriptApplicator, parentCompileScope, textResourceLoader, defaultTarget)

    void doesNothingWhenNothingSpecified() {
        expect:
        action.execute()
    }

    @Test
    void appliesScriptsToDefaultTargetObject() {
        given:
        1 * resolver.resolveUri('script') >> file
        1 * parentCompileScope.createChild("script-$file") >> scriptCompileScope
        1 * scriptApplicator.applyTo(setOf(defaultTarget), _ as ScriptSource, scriptCompileScope, parentCompileScope)

        when:
        action.from('script')

        then:
        action.execute()
    }

    @Test
    void appliesScriptsToTargetObjects() {
        when:
        def target1 = new Object()
        def target2 = new Object()
        1 * resolver.resolveUri('script') >> file
        1 * scriptApplicator.applyTo(setOf(target1, target2), _ as ScriptSource, scriptCompileScope, parentCompileScope)
        1 * parentCompileScope.createChild("script-$file") >> scriptCompileScope

        then:
        action.from('script')
        action.to(target1)
        action.to(target2)
        action.execute()
    }

    @Test
    void flattensCollections() {
        when:
        def target1 = new Object()
        def target2 = new Object()
        1 * resolver.resolveUri('script') >> file
        1 * scriptApplicator.applyTo(setOf(target1, target2), _ as ScriptSource, scriptCompileScope, parentCompileScope)
        1 * parentCompileScope.createChild("script-$file") >> scriptCompileScope

        then:
        action.from('script')
        action.to([[target1], target2])
        action.execute()
    }

    static Set<Object> setOf(Object... xs) {
        xs as Set<Object>
    }

}

