/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.language

import org.gradle.nativeplatform.fixtures.AvailableToolChains
import spock.lang.Specification

class LanguageTaskNamesTest extends Specification implements LanguageTaskNames {

    @Override
    AvailableToolChains.InstalledToolChain getToolchainUnderTest() {
        return null
    }

    @Override
    String getLanguageTaskSuffix() {
        return "Cpp"
    }

    def "variant task names read the state of the enclosing ProjectTasks"() {
        expect:
        tasks(":lib").debug.allToLink == [":lib:compileDebugCpp", ":lib:linkDebug"]
        tasks("").withOperatingSystemFamily("windows").release.assemble == ":assembleReleaseWindows"
        tasks("").withBuildType("Profile").compile == ":compileProfileCpp"
    }
}
