/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.compile


import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultJavaCompileSpecTest extends Specification {

    def "parses module-path from compileArgs with #description"() {
        given:
        CompileOptions options = TestUtil.newInstance(CompileOptions, TestUtil.objectFactory())
        options.compilerArgs.addAll(modulePathParameters)
        DefaultJavaCompileSpec compileSpec = new DefaultJavaCompileSpec()
        compileSpec.setCompileOptions(options)

        when:
        def modulePath = compileSpec.modulePath

        then:
        modulePath == [new File("/some/path"), new File("/some/path2")]

        where:
        description               | modulePathParameters
        "--module-path=<modules>" | ["--module-path=/some/path$File.pathSeparator/some/path2"]
        "--module-path <modules>" | ["--module-path", "/some/path$File.pathSeparator/some/path2"]
        "-p <modules>"            | ["-p", "/some/path$File.pathSeparator/some/path2"]
    }
}
