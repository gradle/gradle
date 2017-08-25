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

import org.apache.commons.io.FilenameUtils
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.internal.IoActions
import org.gradle.internal.hash.HashFunction
import org.gradle.internal.hash.HashUtil
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.internal.hash.Hashing.md5
import static org.gradle.internal.hash.Hashing.sha1

abstract class AbstractModule {
    /**
     Last modified date for writeZipped to be able to create zipFiles with identical hashes
     */
    private static Date lmd = new Date()

    /**
     * @param cl A closure that is passed a writer to use to generate the content.
     */
    protected void publish(TestFile file, Closure cl) {
        file.parentFile.mkdirs()
        def hashBefore = file.exists() ? getHash(file, sha1()) : null
        def tmpFile = file.parentFile.file("${file.name}.tmp")

        if(isJarFile(file)) {
            writeZipped(tmpFile, cl)
        } else {
            writeContents(tmpFile, cl)
        }

        def hashAfter = getHash(tmpFile, sha1())
        if (hashAfter == hashBefore) {
            // Already published
            return
        }

        assert !file.exists() || file.delete()
        assert tmpFile.renameTo(file)
        onPublish(file)
    }

    private static void writeContents(output, Closure cl) {
        output.withWriter("utf-8", cl)
    }

    private static void writeZipped(TestFile testFile, Closure cl) {
        def bos = new ByteArrayOutputStream()
        writeContents(bos, cl)

        ZipOutputStream zipStream = new ZipOutputStream(testFile)
        try {
            def entry = new ZipEntry(testFile.name)
            entry.setTime(lmd.getTime())
            zipStream.putNextEntry(entry)
            zipStream << bos.toByteArray()
            zipStream.closeEntry()
            zipStream.finish()
        } finally {
            IoActions.closeQuietly(zipStream)
        }
    }

    private static boolean isJarFile(TestFile testFile) {
        return FilenameUtils.getExtension(testFile.getName()) == 'jar'
    }

    protected abstract onPublish(TestFile file)

    static TestFile getSha1File(TestFile file) {
        getHashFile(file, sha1())
    }

    static TestFile sha1File(TestFile file) {
        hashFile(file, sha1(), 40)
    }

    static TestFile getMd5File(TestFile file) {
        getHashFile(file, md5())
    }

    static TestFile md5File(TestFile file) {
        hashFile(file, md5(), 32)
    }

    private static TestFile hashFile(TestFile file, HashFunction hashFunction, int len) {
        def hashFile = getHashFile(file, hashFunction)
        def hash = getHash(file, hashFunction)
        hashFile.text = String.format("%0${len}x", new BigInteger(1, hash.toByteArray()))
        return hashFile
    }

    private static TestFile getHashFile(TestFile file, HashFunction hashFunction) {
        file.parentFile.file("${file.name}.${hashFunction.toString().toLowerCase().replaceAll(/-/, "")}")
    }

    protected static BigInteger getHash(TestFile file, HashFunction hashFunction) {
        new BigInteger(1, HashUtil.createHash(file, hashFunction).toByteArray())
    }
}
