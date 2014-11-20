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

package org.gradle.play.internal.coffeescript

import org.gradle.api.tasks.WorkResult
import org.gradle.play.internal.javascript.engine.JavascriptEngine
import org.gradle.play.internal.javascript.engine.ScriptResult
import spock.lang.Specification

/**
 *
 */
class CoffeeScriptCompilerTest extends Specification {
    def sourceDirName = "/some/source/dir"
    def sourceDir = new File(sourceDirName)
    def targetDirName = "/some/targetdir"
    def targetDir = new File(targetDirName)
    def fileNames = [ "a/x", "b", "c/y/z" ]
    def files = fileNames.collect { new File(sourceDir, it) }
    CoffeeScriptCompileSpec spec
    JavascriptEngine engine
    CoffeeScriptCompiler compiler

    def setup() {
        spec = Stub(CoffeeScriptCompileSpec) {
            _ * getSources() >> { files }
            _ * getSourceDirectory() >> { sourceDir }
            _ * getDestinationDir() >> { targetDir }
        }
        engine = Mock(JavascriptEngine)
        compiler = new CoffeeScriptCompiler(engine)
    }

    def "compiles sources from spec" () {
        def sourceFileNames = []

        when:
        WorkResult result = compiler.execute(spec)

        then:
        3 * engine.execute(CoffeeScriptCompiler.WRAPPER_SCRIPT_NAME, _, _) >> { String scriptName, String script, Object[] args ->
            sourceFileNames << args[0]
            assert args[1] == toTargetDir(new File(args[0]), sourceDir, targetDir)
            return Stub(ScriptResult) {
                _ * getStatus() >> ScriptResult.SUCCESS
            }
        }

        and:
        sourceFileNames.sort() == files.collect { it.path }.sort()
        result.didWork
    }

    def "propagates error on compile failure" () {
        def failure = new Exception("From test")

        when:
        compiler.execute(spec)

        then:
        1 * engine.execute(_,_,_) >> {
            Stub(ScriptResult) {
                _ * getStatus() >> -1
                _ * getException() >> failure
            }
        }

        and:
        def e = thrown(CoffeeScriptCompileException)
        e.cause == failure
    }

    def "does no work with empty sources" () {
        spec = Stub(CoffeeScriptCompileSpec) {
            _ * getSources() >> []
        }

        when:
        WorkResult result = compiler.execute(spec)

        then:
        0 * engine.execute(_,_,_)
        ! result.didWork
    }

    def toTargetDir(File source, File sourceDir, File targetDir) {
        return targetDir.path + (source.path - sourceDir.path)
    }
}
