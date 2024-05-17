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
 * Test cases for triple-argument String and String collection {@code execute} extension:
 * <pre>
 *     "echo 123".execute(env, cwd)
 *     ["echo", "123"].execute(env, cwd)
 * </pre>
 */
class Execute3InstanceInstrumentationInDynamicGroovyWithIndyIntegrationTest extends AbstractProcessInstrumentationInDynamicGroovyIntegrationTest {
    @Override
    def indyModes() {
        return [true]
    }

    @Override
    def testCases() {
        return [
            // varInitializer | processCreator | expectedPwdSuffix | expectedEnvVar
            [fromString(), "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromGroovyString(), "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromStringArray(), "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromStringList(), "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromObjectList(), "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromString(), "command.execute(['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            [fromGroovyString(), "command.execute(['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            [fromStringArray(), "command.execute(['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            [fromStringList(), "command.execute(['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            [fromObjectList(), "command.execute(['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            // Null argument handling
            [fromString(), "command.execute(null, null)", "", ""],
            [fromString(), "command.execute(['FOOBAR=foobar'], null)", "", "foobar"],
            [fromString(), "command.execute(new String[] {'FOOBAR=foobar'}, null)", "", "foobar"],
            [fromString(), "command.execute(null, file('$pwd'))", pwd, ""],
            // Spread calls
            [fromString(), "command.execute(*[new String[] {'FOOBAR=foobar'}, file('$pwd')])", pwd, "foobar"],
            [fromString(), "command.execute(*[['FOOBAR=foobar'], file('$pwd')])", pwd, "foobar"],
            // Typed nulls
            [fromString(), "command.execute((String[]) null, null)", "", ""],
            [fromString(), "command.execute(null, (File) null)", "", ""],
            [fromString(), "command.execute((String[]) null, (File) null)", "", ""],
            // type-wrapped arguments
            [fromString(), "command.execute((String[]) ['FOOBAR=foobar'], null)", "", "foobar"],
            [fromString(), "command.execute((List) ['FOOBAR=foobar'], null)", "", "foobar"],
            [fromString(), "command.execute(['FOOBAR=foobar'] as String[], null)", "", "foobar"],
            [fromString(), "command.execute(['FOOBAR=foobar'] as List, null)", "", "foobar"],
            // null-safe call
            [fromGroovyString(), "command?.execute(null, null)", "", ""],
        ]
    }
}
