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
 *     execute("echo 123")
 *     execute(["echo", "123"])
 * </pre>
 */
class Execute1StaticInstrumentationInDynamicGroovyWithIndyIntegrationTest extends AbstractProcessInstrumentationInDynamicGroovyIntegrationTest {
    @Override
    def indyModes() {
        return [true]
    }

    @Override
    def testCases() {
        // static import calls (are handled differently by the dynamic Groovy's codegen)
        return [
            // varInitializer | processCreator | expectedPwdSuffix | expectedEnvVar
            [fromString(), "execute(command)", "", ""],
            [fromGroovyString(), "execute(command)", "", ""],
            [fromStringArray(), "execute(command)", "", ""],
            [fromStringList(), "execute(command)", "", ""],
            [fromObjectList(), "execute(command)", "", ""],
            // Spread calls
            [fromString(), "execute(*[command])", "", ""],
            // type-wrapped arguments
            [fromGroovyString(), "execute(command as String)", "", ""],
        ]
    }
}
