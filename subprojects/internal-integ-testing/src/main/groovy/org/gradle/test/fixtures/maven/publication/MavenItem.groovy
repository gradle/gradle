/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.test.fixtures.maven.publication

import org.gradle.internal.hash.HashUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.ChecksumVerifiable

class MavenItem implements ChecksumVerifiable {
    File file;
    File md5
    File sha1

    MavenItem(TestFile file, String fileName) {
        this.file = file.file(fileName)
        this.md5 = file.file(fileName + '.md5')
        this.sha1 = file.file(fileName + '.sha1')
    }

    void verify() {
        assert file.exists()
        assert md5.exists()
        assert sha1.exists()
        assert md5.text == HashUtil.createHash(file, 'MD5').asZeroPaddedHexString(32)
        assert sha1.text == HashUtil.createHash(file, 'SHA1').asZeroPaddedHexString(40)
    }
}
