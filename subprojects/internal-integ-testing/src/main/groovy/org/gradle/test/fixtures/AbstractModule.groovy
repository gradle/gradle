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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.io.FilenameUtils
import org.gradle.internal.IoActions
import org.gradle.internal.hash.HashFunction
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

abstract class AbstractModule implements Module {
    /**
     Last modified date for writeZipped to be able to create zipFiles with identical hashes
     */
    private static Date lmd = new Date(0)

    private boolean hasModuleMetadata
    private Closure<?> onEveryFile

    Map<String, String> attributes = [:]

    /**
     * @param cl A closure that is passed a writer to use to generate the content.
     */
    protected void publish(TestFile file, @DelegatesTo(value=Writer, strategy=Closure.DELEGATE_FIRST) Closure cl, byte[] content = null) {
        file.parentFile.mkdirs()
        def hashBefore = file.exists() ? Hashing.sha1().hashFile(file) : null
        def tmpFile = file.parentFile.file("${file.name}.tmp")

        if (content) {
            tmpFile.bytes = content
        } else if (isJarFile(file)) {
            writeZipped(tmpFile, cl)
        } else {
            writeContents(tmpFile, cl)
            // normalize line endings
            tmpFile.setText(TextUtil.normaliseLineSeparators(tmpFile.getText("utf-8")), "utf-8")
        }

        def hashAfter = Hashing.sha1().hashFile(tmpFile)
        if (hashAfter == hashBefore) {
            // Already published
            return
        }

        assert !file.exists() || file.delete()
        assert tmpFile.renameTo(file)
        onPublish(file)
    }

    private void writeContents(output, Closure cl) {
        output.withWriter("utf-8", cl)
    }

    private void writeZipped(TestFile testFile, Closure cl) {
        def bos = new ByteArrayOutputStream()
        writeContents(bos, cl)

        ZipArchiveOutputStream zipStream = new ZipArchiveOutputStream(testFile)
        try {
            def entry = new ZipArchiveEntry(testFile.name)
            entry.setTime(lmd.getTime())
            zipStream.putArchiveEntry(entry)
            zipStream << bos.toByteArray()
            zipStream.closeArchiveEntry()
            zipStream.finish()
        } finally {
            IoActions.closeQuietly(zipStream)
        }
    }

    protected boolean isJarFile(TestFile testFile) {
        return FilenameUtils.getExtension(testFile.getName()) == 'jar'
    }

    protected abstract onPublish(TestFile file)

    protected void postPublish(TestFile file) {
        if (onEveryFile) {
            Closure cl = onEveryFile.clone()
            cl.delegate = file
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            cl(file)
        }
    }

    TestFile getSha1File(TestFile file) {
        getHashFile(file, Hashing.sha1())
    }

    TestFile sha1File(TestFile file) {
        hashFile(file, Hashing.sha1())
    }

    TestFile getSha256File(TestFile file) {
        getHashFile(file, Hashing.sha256())
    }

    TestFile sha256File(TestFile file) {
        hashFile(file, Hashing.sha256())
    }

    TestFile getSha512File(TestFile file) {
        getHashFile(file, Hashing.sha512())
    }

    TestFile sha512File(TestFile file) {
        hashFile(file, Hashing.sha512())
    }

    TestFile getMd5File(TestFile file) {
        getHashFile(file, Hashing.md5())
    }

    TestFile md5File(TestFile file) {
        hashFile(file, Hashing.md5())
    }

    private TestFile hashFile(TestFile file, HashFunction hashFunction) {
        def hashFile = getHashFile(file, hashFunction)
        def hash = hashFunction.hashFile(file)
        hashFile.text = hash.toZeroPaddedString(hashFunction.hexDigits)
        return hashFile
    }

    private TestFile getHashFile(TestFile file, HashFunction hashFunction) {
        def algorithm = hashFunction.algorithm.toLowerCase(Locale.ROOT).replaceAll('-', '')
        file.parentFile.file("${file.name}.${algorithm}")
    }

    Module withModuleMetadata() {
        hasModuleMetadata = true
        return this
    }

    boolean isHasModuleMetadata() {
        hasModuleMetadata
    }

    @Override
    Module withSignature(@DelegatesTo(value = File, strategy = Closure.DELEGATE_FIRST) Closure<?> signer) {
        onEveryFile = signer
        this
    }
}
