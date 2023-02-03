/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.file

import spock.lang.Specification

import static org.gradle.internal.file.FilePathUtil.sizeOfCommonPrefix

class FilePathUtilTest extends Specification {

    def "common prefix for #commonPrefix/{#firstPostFix,#secondPostFix}"() {
        expect:
        sizeOfCommonPrefix(commonPrefix + "/" + firstPostFix, commonPrefix + "/" + secondPostFix, 0, '/' as char) == commonPrefix.length()
        sizeOfCommonPrefix(commonPrefix + "/" + secondPostFix, commonPrefix + "/" + firstPostFix, 0, '/' as char) == commonPrefix.length()

        where:
        commonPrefix | firstPostFix | secondPostFix
        "some"      | "path"            | "other/some"
        "some"      | "pather/one"      | "path/two/three"
        "some/path" | "one"             | "two/three"
        ""          | "one"             | "two/three"
        ""          | "oner"            | "one"
        "some/path" | "with/one/ending" | "without/two/different"
    }
}
