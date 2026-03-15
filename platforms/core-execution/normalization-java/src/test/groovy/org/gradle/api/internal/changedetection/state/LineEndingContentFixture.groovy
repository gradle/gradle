/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

class LineEndingContentFixture {
    public static final byte[] PNG_CONTENT = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a] as byte[]
    public static final byte[] JPG_CONTENT = [0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0xff, 0xda] as byte[]
    public static final byte[] CLASS_FILE_CONTENT = [0xca, 0xfe, 0xba, 0xbe, 0x00, 0x00, 0x00, 0x37, 0x0a, 0x00] as byte[]

    static String textWithLineEndings(String eol) {
        return "${eol}This is a line${eol}Another line${eol}${eol}Yet another line\nAnd one more\n\nAnd yet one more${eol}${eol}"
    }
}
