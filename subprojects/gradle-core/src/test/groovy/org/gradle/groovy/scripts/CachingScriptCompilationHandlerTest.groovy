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
package org.gradle.groovy.scripts

import spock.lang.Specification

class CachingScriptCompilationHandlerTest extends Specification {
    private final ScriptCompilationHandler target = Mock()
    private final CachingScriptCompilationHandler handler = new CachingScriptCompilationHandler(target)

    def cachesScriptClassForGivenClassDirAndParentClassLoader() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('script')
        ClassLoader parentClassLoader = Mock()
        File cacheDir = new File('cacheDir')

        when:
        def c1 = handler.loadFromDir(script1, parentClassLoader, cacheDir, Script.class)
        def c2 = handler.loadFromDir(script2, parentClassLoader, cacheDir, Script.class)

        then:
        1 * target.loadFromDir(script1, parentClassLoader, cacheDir, Script.class) >> Script.class
        0 * target._
    }

    def doesNotCacheForDifferentScriptClass() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('other')
        ClassLoader parentClassLoader = Mock()
        File cacheDir = new File('cacheDir')

        when:
        def c1 = handler.loadFromDir(script1, parentClassLoader, cacheDir, Script.class)
        def c2 = handler.loadFromDir(script2, parentClassLoader, cacheDir, Script.class)

        then:
        1 * target.loadFromDir(script1, parentClassLoader, cacheDir, Script.class) >> Script.class
        1 * target.loadFromDir(script2, parentClassLoader, cacheDir, Script.class) >> Script.class
    }

    def doesNotCacheForDifferentClassDir() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('script')
        ClassLoader parentClassLoader = Mock()
        File cacheDir1 = new File('cacheDir')
        File cacheDir2 = new File('cacheDir2')

        when:
        def c1 = handler.loadFromDir(script1, parentClassLoader, cacheDir1, Script.class)
        def c2 = handler.loadFromDir(script2, parentClassLoader, cacheDir2, Script.class)

        then:
        1 * target.loadFromDir(script1, parentClassLoader, cacheDir1, Script.class) >> Script.class
        1 * target.loadFromDir(script2, parentClassLoader, cacheDir2, Script.class) >> Script.class
    }

    def doesNotCacheForDifferentParentClassLoader() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('script')
        ClassLoader parentClassLoader1 = Mock()
        ClassLoader parentClassLoader2 = Mock()
        File cacheDir = new File('cacheDir')

        when:
        def c1 = handler.loadFromDir(script1, parentClassLoader1, cacheDir, Script.class)
        def c2 = handler.loadFromDir(script2, parentClassLoader2, cacheDir, Script.class)

        then:
        1 * target.loadFromDir(script1, parentClassLoader1, cacheDir, Script.class) >> Script.class
        1 * target.loadFromDir(script2, parentClassLoader2, cacheDir, Script.class) >> Script.class
    }
    
    def scriptSource(String className = 'script') {
        ScriptSource script = Mock()
        _ * script.className >> className
        script
    }
}
