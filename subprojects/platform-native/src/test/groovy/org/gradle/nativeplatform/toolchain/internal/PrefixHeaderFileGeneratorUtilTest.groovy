/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal

import com.google.common.collect.Sets
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Specification

class PrefixHeaderFileGeneratorUtilTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()

    def "generates a prefix header file" () {
        def headers = Sets.newLinkedHashSet()
        headers.add "header.h"
        headers.add "<stdio.h>"
        headers.add "some/path/to/another.h"
        def tempDir = tmpDirProvider.createDir("temp")
        def prefixHeaderFile = new File(tempDir, "prefix-headers.h")

        when:
        PrefixHeaderFileGeneratorUtil.generatePCHFile(headers, prefixHeaderFile)

        then:
        prefixHeaderFile.text == TextUtil.toPlatformLineSeparators(
"""#include "header.h"
#include <stdio.h>
#include "some/path/to/another.h"
""")
    }
}
