/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.cpp

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.junit.runner.RunWith

@RunWith(CppIntegrationTestRunner)
abstract class AbstractBinariesIntegrationSpec extends AbstractIntegrationSpec {
    def TestFile executable(Object path) {
        return file(OperatingSystem.current().getExecutableName(path.toString()))
    }

    def TestFile sharedLibrary(Object path) {
        return file(OperatingSystem.current().getSharedLibraryName(path.toString()))
    }

    def TestFile staticLibrary(Object path) {
        return file(OperatingSystem.current().getStaticLibraryName(path.toString()))
    }
}
