/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.groovy.scripts.internal

import spock.lang.Specification
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.Transformer
import org.gradle.groovy.scripts.Script
import org.gradle.groovy.scripts.TestScript

class CachingScriptClassCompilerTest extends Specification {
    private final ScriptClassCompiler target = Mock()
    private final CachingScriptClassCompiler compiler = new CachingScriptClassCompiler(target)

    def "caches the script class for a given script class and classloader and transformer and baseclass"() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('script')
        ClassLoader parentClassLoader = Mock()
        Transformer transformer = transformer()

        when:
        def c1 = compiler.compile(script1, parentClassLoader, transformer, Script.class)
        def c2 = compiler.compile(script2, parentClassLoader, transformer, Script.class)

        then:
        c1 == c2
        1 * target.compile(script1, parentClassLoader, transformer, Script.class) >> Script.class
        0 * target._
    }

    def "does not cache script class for different script class"() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('other')
        ClassLoader parentClassLoader = Mock()
        Transformer transformer = transformer()

        when:
        def c1 = compiler.compile(script1, parentClassLoader, transformer, Script.class)
        def c2 = compiler.compile(script2, parentClassLoader, transformer, Script.class)

        then:
        1 * target.compile(script1, parentClassLoader, transformer, Script.class) >> Script.class
        1 * target.compile(script2, parentClassLoader, transformer, Script.class) >> Script.class
    }

    def "does not cache script class for different transformers"() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('script')
        ClassLoader parentClassLoader = Mock()
        Transformer transformer1 = transformer('t1')
        Transformer transformer2 = transformer('t2')

        when:
        def c1 = compiler.compile(script1, parentClassLoader, transformer1, Script.class)
        def c2 = compiler.compile(script2, parentClassLoader, transformer2, Script.class)

        then:
        1 * target.compile(script1, parentClassLoader, transformer1, Script.class) >> Script.class
        1 * target.compile(script2, parentClassLoader, transformer2, Script.class) >> Script.class
    }

    def "does not cache script class for different classloaders"() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('script')
        ClassLoader parentClassLoader1 = Mock()
        ClassLoader parentClassLoader2 = Mock()
        Transformer transformer = transformer()

        when:
        def c1 = compiler.compile(script1, parentClassLoader1, transformer, Script.class)
        def c2 = compiler.compile(script2, parentClassLoader2, transformer, Script.class)

        then:
        1 * target.compile(script1, parentClassLoader1, transformer, Script.class) >> Script.class
        1 * target.compile(script2, parentClassLoader2, transformer, Script.class) >> Script.class
    }

    def "does not cache script class for different base classes"() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('script')
        ClassLoader parentClassLoader = Mock()
        Transformer transformer = transformer()

        when:
        def c1 = compiler.compile(script1, parentClassLoader, transformer, Script.class)
        def c2 = compiler.compile(script2, parentClassLoader, transformer, TestScript.class)

        then:
        1 * target.compile(script1, parentClassLoader, transformer, Script.class) >> Script.class
        1 * target.compile(script2, parentClassLoader, transformer, TestScript.class) >> TestScript.class
    }

    def scriptSource(String className = 'script') {
        ScriptSource script = Mock()
        _ * script.className >> className
        script
    }

    def transformer(String id = 'id') {
        Transformer transformer = Mock()
        _ * transformer.id >> id
        transformer
    }
}
