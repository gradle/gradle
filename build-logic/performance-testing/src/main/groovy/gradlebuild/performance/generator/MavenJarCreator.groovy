/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.performance.generator

import java.nio.file.attribute.FileTime
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MavenJarCreator {
    int minimumSizeKB = 0
    int maximumSizeKB = 0
    Random random = new Random(1L)
    byte[] charsToUse = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes()

    void createJar(MavenModule mavenModule, File artifactFile) {
        try {
            artifactFile.withOutputStream { stream ->
                ZipOutputStream out = new ZipOutputStream(stream)
                out.setLevel(Deflater.NO_COMPRESSION)
                try {
                    addZipEntry(out, artifactFile.name + ".properties", "testcontent")
                    if (minimumSizeKB > 0) {
                        int sizeInBytes
                        if(maximumSizeKB > minimumSizeKB) {
                            sizeInBytes = (minimumSizeKB + random.nextInt(maximumSizeKB - minimumSizeKB)) * 1024
                        } else {
                            sizeInBytes = minimumSizeKB * 1024
                        }
                        addGeneratedUncompressedZipEntry(out, "generated.txt", sizeInBytes)
                    }
                } finally {
                    out.close()
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace()
            System.out.println("Error: " + ex.getMessage())
        }
    }

    private void addZipEntry(ZipOutputStream out, String name, String content) {
        // Add archive entry
        ZipEntry entry = normalizedZipEntry(name)
        out.putNextEntry(entry)

        // Write file to archive
        def contentBytes = content.getBytes("utf-8")
        out.write(contentBytes, 0, contentBytes.length)
    }

    private void addGeneratedUncompressedZipEntry(ZipOutputStream out, String name, int sizeInBytes) {
        ZipEntry entry = normalizedZipEntry(name)
        out.putNextEntry(entry)

        for (int i = 0; i < sizeInBytes; i++) {
            out.write(charsToUse, i % charsToUse.length, 1)
        }
    }

    private static ZipEntry normalizedZipEntry(String name) {
        new ZipEntry(name).tap {
            creationTime = FileTime.fromMillis(0)
            lastAccessTime = FileTime.fromMillis(0)
            lastModifiedTime = FileTime.fromMillis(0)
        }
    }
}

