/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.build

import com.tonicsystems.jarjar.Main as JarJarMain
import com.tonicsystems.jarjar.ext_util.EntryStruct
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.jar.JarOutputStream
import java.util.jar.JarFile
import java.util.jar.JarEntry

class JarJar extends DefaultTask {
    @InputFiles FileCollection inputFiles
    @OutputFile File outputFile

    @Input def rules = [:]
    @Input def keeps = []

    @TaskAction
    void nativeJarJar() {
        try {
            TemporaryFileProvider tmpFileProvider = getServices().get(TemporaryFileProvider);
            File tempRuleFile = tmpFileProvider.createTemporaryFile("jarjar", "rule")
            writeRuleFile(tempRuleFile)

            File tmpMergedJarFile = tmpFileProvider.createTemporaryFile("jarjar", "jar")
            mergeJarFiles(inputFiles.files, tmpMergedJarFile);

            JarJarMain.main("process", tempRuleFile.absolutePath, tmpMergedJarFile.absolutePath, outputFile.absolutePath)
        } catch (IOException e) {
            throw new GradleException("Unable to execute JarJar task", e);
        }
    }

    void rule(String pattern, String result) {
        rules[pattern] = result
    }

    void keep(String pattern) {
        keeps << pattern
    }

    private void writeRuleFile(File ruleFile) {
        ruleFile.withPrintWriter { writer ->
            rules.each {pattern, result ->
                writer.println("rule ${pattern} ${result}")
            }
            keeps.each {pattern ->
                writer.println("keep ${pattern}")
            }
        }
    }

    private static void mergeJarFiles(Set<File> srcFiles, File destFile) throws IOException {
        byte[] buf = new byte[0x2000];
        JarOutputStream out = new JarOutputStream(new FileOutputStream(destFile));

        Set<String> entries = new HashSet<String>();
        for (File srcFile : srcFiles) {
            JarFile jarFile = new JarFile(srcFile);
            try {
                EntryStruct struct = new EntryStruct();
                Enumeration<JarEntry> e = jarFile.entries();
                while (e.hasMoreElements()) {
                    JarEntry entry = e.nextElement();
                    struct.name = entry.getName();
                    struct.time = entry.getTime();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    pipe(jarFile.getInputStream(entry), baos, buf);
                    struct.data = baos.toByteArray();
                    if (entries.add(struct.name)) {
                        entry = new JarEntry(struct.name);
                        entry.setTime(struct.time);
                        entry.setCompressedSize(-1);
                        out.putNextEntry(entry);
                        out.write(struct.data);
                    }
                }
            } finally {
                jarFile.close();
            }
        }
        out.close();
    }

    public static void pipe(InputStream is, OutputStream out, byte[] buf) throws IOException {
        for (; ;) {
            int amt = is.read(buf);
            if (amt < 0) {
                break;
            }
            out.write(buf, 0, amt);
        }
    }
}