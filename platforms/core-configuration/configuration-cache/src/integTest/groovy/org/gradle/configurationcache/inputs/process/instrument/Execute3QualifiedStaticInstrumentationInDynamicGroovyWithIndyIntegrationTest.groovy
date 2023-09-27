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
 * Test cases for triple-argument {@code ProcessGroovyMethods.execute}:
 * <pre>
 *     ProcessGroovyMethod.execute("echo 123", env, cwd)
 *     ProcessGroovyMethod.execute(["echo", "123"]", env, cwd)
 * </pre>
 */
class Execute3QualifiedStaticInstrumentationInDynamicGroovyWithIndyIntegrationTest extends AbstractProcessInstrumentationInDynamicGroovyIntegrationTest {
    @Override
    def indyModes() {
        return [true]
    }

    @Override
    def testCases() {
        return [
            // Direct ProcessGroovyMethods calls
            // varInitializer | processCreator | expectedPwdSuffix | expectedEnvVar
            [fromString(), "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromGroovyString(), "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromStringArray(), "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromStringList(), "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromObjectList(), "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromString(), "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            [fromGroovyString(), "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            [fromStringArray(), "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            [fromStringList(), "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            [fromObjectList(), "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            // Null argument handling
            [fromString(), "ProcessGroovyMethods.execute(command, null, null)", "", ""],
            [fromString(), "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], null)", "", "foobar"],
            [fromString(), "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, null)", "", "foobar"],
            [fromString(), "ProcessGroovyMethods.execute(command, null, file('$pwd'))", pwd, ""],
            // Spread calls
            [fromString(), "ProcessGroovyMethods.execute(*[command, new String[] {'FOOBAR=foobar'}, file('$pwd')])", pwd, "foobar"],
            [fromString(), "ProcessGroovyMethods.execute(*[command, ['FOOBAR=foobar'], file('$pwd')])", pwd, "foobar"],
            // Typed nulls
            [fromString(), "ProcessGroovyMethods.execute(command, (String[]) null, null)", "", ""],
            [fromString(), "ProcessGroovyMethods.execute(command, null, (File) null)", "", ""],
            [fromString(), "ProcessGroovyMethods.execute(command, (String[]) null, (File) null)", "", ""],
            // type-wrapped arguments
            [fromGroovyString(), "ProcessGroovyMethods.execute(command as String, null, null)", "", ""],
            [fromString(), "ProcessGroovyMethods.execute(command, (String[]) ['FOOBAR=foobar'], null)", "", "foobar"],
            [fromString(), "ProcessGroovyMethods.execute(command, (List) ['FOOBAR=foobar'], null)", "", "foobar"],
            [fromString(), "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'] as String[], null)", "", "foobar"],
            [fromString(), "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'] as List, null)", "", "foobar"],
        ]
    }
}
