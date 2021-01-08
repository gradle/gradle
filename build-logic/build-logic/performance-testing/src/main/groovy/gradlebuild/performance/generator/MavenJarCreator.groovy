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

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.Deflater

class MavenJarCreator {
    int minimumSizeKB = 0
    int maximumSizeKB = 0
    Random random = new Random(1L)
    byte[] charsToUse = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes()

    void createJar(MavenModule mavenModule, File artifactFile) {
        try {
            artifactFile.withOutputStream { stream ->
                JarOutputStream out = new JarOutputStream(stream, new Manifest());
                out.setLevel(Deflater.NO_COMPRESSION)
                try {
                    addJarEntry(out, artifactFile.name + ".properties", "testcontent")
                    if (minimumSizeKB > 0) {
                        int sizeInBytes
                        if(maximumSizeKB > minimumSizeKB) {
                            sizeInBytes = (minimumSizeKB + random.nextInt(maximumSizeKB - minimumSizeKB)) * 1024
                        } else {
                            sizeInBytes = minimumSizeKB * 1024
                        }
                        addGeneratedUncompressedJarEntry(out, "generated.txt", sizeInBytes)
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

    private void addJarEntry(JarOutputStream out, String name, String content) {
        // Add archive entry
        JarEntry entry = new JarEntry(name)
        entry.setTime(System.currentTimeMillis())
        out.putNextEntry(entry)

        // Write file to archive
        def contentBytes = content.getBytes("utf-8")
        out.write(contentBytes, 0, contentBytes.length)
    }

    private void addGeneratedUncompressedJarEntry(JarOutputStream out, String name, int sizeInBytes) {
        JarEntry entry = new JarEntry(name)
        entry.setTime(System.currentTimeMillis())
        out.putNextEntry(entry)

        for (int i = 0; i < sizeInBytes; i++) {
            out.write(charsToUse, i % charsToUse.length, 1)
        }
    }
}
