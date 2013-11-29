/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.internal

import org.gradle.language.base.internal.DefaultBinaryNamingScheme
import org.gradle.nativebinaries.BuildType
import org.gradle.nativebinaries.Executable
import org.gradle.nativebinaries.Platform
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver
import spock.lang.Specification

class DefaultExecutableBinaryTest extends Specification {
    def namingScheme = new DefaultBinaryNamingScheme("bigOne")

    def "has useful string representation"() {
        given:
        def executable = Stub(Executable)

        when:
        def binary = new DefaultExecutableBinary(executable, new DefaultFlavor("flavorOne"), Stub(ToolChainInternal), Stub(Platform), Stub(BuildType), namingScheme, Mock(NativeDependencyResolver))

        then:
        binary.toString() == "executable 'bigOne:executable'"
    }
}
