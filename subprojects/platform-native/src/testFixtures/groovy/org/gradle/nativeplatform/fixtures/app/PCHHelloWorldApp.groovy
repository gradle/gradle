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

package org.gradle.nativeplatform.fixtures.app

import org.gradle.integtests.fixtures.SourceFile

abstract class PCHHelloWorldApp extends IncrementalHelloWorldApp {
    abstract List<SourceFile> getLibrarySources(String path)
    abstract String getIOHeader()

    public SourceFile getBrokenFile() {
        return null
    }

    public SourceFile getPrefixHeaderFile(String path, String... headers) {
        return sourceFile("headers/${path}", "prefixHeader.h", """
            #ifndef PREFIX_HEADER_H
            ${includeAll(headers)}
            #endif
        """)
    }

    private static String includeAll(String... headers) {
        headers.collect { header ->
            if (header.startsWith("<")) {
                return "#include ${header}"
            } else {
                return "#include \"${header}\""
            }
        }.join("\n")
    }
}
