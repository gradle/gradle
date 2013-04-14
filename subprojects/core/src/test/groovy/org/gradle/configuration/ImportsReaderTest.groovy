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
 
package org.gradle.configuration

import org.gradle.groovy.scripts.ScriptSource
import org.gradle.util.Resources
import org.junit.Rule
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class ImportsReaderTest extends Specification {
    @Rule public Resources resources = new Resources()
    ImportsReader reader = new ImportsReader()

    public void testReadImportsFromResource() {
        expect:
        reader.imports.contains('import org.gradle.api.*')
    }

    public void testCreatesScriptSource() {
        def source = [:] as ScriptSource

        when:
        def importsSource = reader.withImports(source)

        then:
        importsSource instanceof ImportsScriptSource
        importsSource.source == source
        importsSource.importsReader == reader
    }
}
