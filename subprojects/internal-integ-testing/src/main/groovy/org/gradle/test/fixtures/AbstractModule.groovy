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

package org.gradle.test.fixtures

import org.gradle.test.fixtures.file.TestFile
import org.gradle.internal.hash.HashUtil

abstract class AbstractModule {
    /**
     * @param cl A closure that is passed a writer to use to generate the content.
     */
    protected void publish(TestFile file, Closure cl) {
        def hashBefore = file.exists() ? getHash(file, "sha1") : null
        def tmpFile = file.parentFile.file("${file.name}.tmp")

        tmpFile.withWriter("utf-8") {
            cl.call(it)
        }

        def hashAfter = getHash(tmpFile, "sha1")
        if (hashAfter == hashBefore) {
            // Already published
            return
        }

        assert !file.exists() || file.delete()
        assert tmpFile.renameTo(file)
        onPublish(file)
    }

    protected abstract onPublish(TestFile file)

    TestFile getSha1File(TestFile file) {
        getHashFile(file, "sha1")
    }

    TestFile sha1File(TestFile file) {
        hashFile(file, "sha1", 40)
    }

    TestFile getMd5File(TestFile file) {
        getHashFile(file, "md5")
    }

    TestFile md5File(TestFile file) {
        hashFile(file, "md5", 32)
    }

    private TestFile hashFile(TestFile file, String algorithm, int len) {
        def hashFile = getHashFile(file, algorithm)
        def hash = getHash(file, algorithm)
        hashFile.text = String.format("%0${len}x", hash)
        return hashFile
    }

    private TestFile getHashFile(TestFile file, String algorithm) {
        file.parentFile.file("${file.name}.${algorithm}")
    }

    protected BigInteger getHash(TestFile file, String algorithm) {
        HashUtil.createHash(file, algorithm.toUpperCase()).asBigInteger()
    }
}
