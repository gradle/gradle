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

package org.gradle.configurationcache.inputs.process.instrument

/**
 * Test cases for two-argument {@code Runtime.exec}:
 * <pre>
 *     Runtime.getRuntime().exec("echo 123", env)
 *     Runtime.getRuntime().exec(["echo", "123"], env)
 * </pre>
 */
class RuntimeExec2InstrumentationInDynamicGroovyWithIndyIntegrationTest extends AbstractProcessInstrumentationInDynamicGroovyIntegrationTest {
    @Override
    def indyModes() {
        return [true]
    }

    @Override
    def testCases() {
        return [
            // varInitializer | processCreator | expectedPwdSuffix | expectedEnvVar
            // Runtime.exec() overloads
            [fromString(), "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'})", "", "foobar"],
            [fromGroovyString(), "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'})", "", "foobar"],
            [fromStringArray(), "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'})", "", "foobar"],
            // Null argument handling
            [fromString(), "Runtime.getRuntime().exec(command, null)", "", ""],
            // Spread calls
            [fromString(), "Runtime.getRuntime().exec(*[command, new String[] {'FOOBAR=foobar'}])", "", "foobar"],
            // Typed nulls
            [fromString(), "Runtime.getRuntime().exec(command, null as String[])", "", ""],
            // type-wrapped arguments
            [fromGroovyString(), "Runtime.getRuntime().exec(command as String, null)", "", ""],
            [fromObjectList(), "Runtime.getRuntime().exec(command as String[], null)", "", ""],
            // Null-safe calls
            [fromString(), "Runtime.getRuntime()?.exec(command, new String[] {'FOOBAR=foobar'})", "", "foobar"],
        ]
    }
}
