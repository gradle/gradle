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
 * Test cases for single-argument {@code ProcessGroovyMethods.execute} with static import (Groovy produces a special byte code in this case):
 * <pre>
 *     import static org.codehaus.groovy.runtime.ProcessGroovyMethods.execute
 *     execute("echo 123", env, cwd)
 *     execute(["echo", "123"], env, cwd)
 * </pre>
 */
class Execute3StaticInstrumentationInDynamicGroovyWithIndyIntegrationTest extends AbstractProcessInstrumentationInDynamicGroovyIntegrationTest {
    @Override
    def indyModes() {
        return [true]
    }

    @Override
    def testCases() {
        // static import calls (are handled differently by the dynamic Groovy's codegen)
        return [
            // varInitializer | processCreator | expectedPwdSuffix | expectedEnvVar
            [fromString(), "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromGroovyString(), "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromStringArray(), "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromStringList(), "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromObjectList(), "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))", pwd, "foobar"],
            [fromString(), "execute(command, ['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            [fromGroovyString(), "execute(command, ['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            [fromStringArray(), "execute(command, ['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            [fromStringList(), "execute(command, ['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            [fromObjectList(), "execute(command, ['FOOBAR=foobar'], file('$pwd'))", pwd, "foobar"],
            // Null argument handling
            [fromString(), "execute(command, null, null)", "", ""],
            [fromString(), "execute(command, ['FOOBAR=foobar'], null)", "", "foobar"],
            [fromString(), "execute(command, new String[] {'FOOBAR=foobar'}, null)", "", "foobar"],
            [fromString(), "execute(command, null, file('$pwd'))", pwd, ""],
            // Spread calls
            [fromString(), "execute(*[command, new String[] {'FOOBAR=foobar'}, file('$pwd')])", pwd, "foobar"],
            [fromString(), "execute(*[command, ['FOOBAR=foobar'], file('$pwd')])", pwd, "foobar"],
            // Typed nulls
            [fromString(), "execute(command, (String[]) null, null)", "", ""],
            [fromString(), "execute(command, null, (File) null)", "", ""],
            [fromString(), "execute(command, (String[]) null, (File) null)", "", ""],
            // type-wrapped arguments
            [fromGroovyString(), "execute(command as String, null, null)", "", ""],
            [fromString(), "execute(command, (String[]) ['FOOBAR=foobar'], null)", "", "foobar"],
            [fromString(), "execute(command, (List) ['FOOBAR=foobar'], null)", "", "foobar"],
            [fromString(), "execute(command, ['FOOBAR=foobar'] as String[], null)", "", "foobar"],
            [fromString(), "execute(command, ['FOOBAR=foobar'] as List, null)", "", "foobar"],
        ]
    }
}
