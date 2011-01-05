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
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class ImportsReaderTest {
    @Rule public Resources resources = new Resources()
    ImportsReader testObj = new ImportsReader()

    @Test public void testReadImportsFromResource() {
        String result = testObj.getImports()
        assertEquals(resources.getResource('default-imports.txt').text, result)
    }

    @Test public void testCreatesScriptSource() {
        ScriptSource source = [:] as ScriptSource
        ScriptSource importsSource = testObj.withImports(source)
        assertThat(importsSource, instanceOf(ImportsScriptSource.class))
        assertThat(importsSource.source, sameInstance(source))
        assertThat(importsSource.importsReader, sameInstance(testObj))
    }
}
