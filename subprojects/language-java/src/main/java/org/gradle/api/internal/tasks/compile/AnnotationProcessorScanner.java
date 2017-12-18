/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.incap.IncapBuildClientFactory;
import org.gradle.incap.IncapVersionDetector;
import org.gradle.incap.ProcessorType;
import org.gradle.internal.FileUtils;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileType;
import org.gradle.util.DeprecationLogger;

/**
 * Discovers relevant properties of annotation processors.
 */
class AnnotationProcessorScanner implements FileContentCacheFactory.Calculator<AnnotationProcessorInfo> {

    private static final Pattern CLASSNAME = Pattern.compile("([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*");

    @Override
    public AnnotationProcessorInfo calculate(File dirOrJar, FileType fileType) {
        AnnotationProcessorInfo result = new AnnotationProcessorInfo();
        // Set the cache entry's default name to the dir/jar path, for debugging.
        // If we discover that this is an annotation processor, we will set it to the class name.
        result.setName(dirOrJar.getPath());

        try {
            if (fileType == FileType.Directory) {
                File spec = new File(dirOrJar, "META-INF/services/javax.annotation.processing.Processor");
                if (spec.isFile()) {
                    result.setProcessor(true);
                    scanFileForClassname(spec, result);
                    scanDirOrJarForIncap(dirOrJar, result);
                }
                return result;
            }

            if (fileType == FileType.RegularFile && FileUtils.hasExtension(dirOrJar, "jar")) {
                ZipFile zipFile = new ZipFile(dirOrJar);
                try {
                    ZipEntry entry = zipFile.getEntry("META-INF/services/javax.annotation.processing.Processor");
                    if (entry != null) {
                        result.setProcessor(true);
                        scanZipEntryForClassname(zipFile, entry, result);
                        scanDirOrJarForIncap(dirOrJar, result);
                    }
                } finally {
                    zipFile.close();
                }
            }
        } catch (IOException e) {
            DeprecationLogger.nagUserWith("Malformed jar [" + dirOrJar.getName() + "] found on compile classpath. Gradle 5.0 will no longer allow malformed jars on compile classpath.");
        }

        return result;
    }

    private void scanFileForClassname(File spec, final AnnotationProcessorInfo result) {
        try {
            // TODO:  Clean this up.  It's ugly.
            Files.asCharSource(spec, Charsets.UTF_8)
                .readLines(new LineProcessor<Void>() {
                    @Override
                    public boolean processLine(String line) throws IOException {
                        if (CLASSNAME.matcher(line).matches()) {
                            result.setName(line);
                        }
                        return true;
                    }

                    @Override
                    public Void getResult() {
                        return null;
                    }
                });
            if (!result.isNamed()) {
                result.setName(spec.getPath());
            }
        } catch (IOException iox) {
            throw UncheckedException.throwAsUncheckedException(iox);
        }
    }

    private void scanDirOrJarForIncap(File dirOrJar, AnnotationProcessorInfo result) {
        try {
            ProcessorType type = new IncapVersionDetector().detectProcessorType(dirOrJar);
            if (type == ProcessorType.SIMPLE || type == ProcessorType.AGGREGATING) {
                result.setIncapSupportType(type);
            }
        } catch (IOException iox) {
            result.setIncapSupportType(ProcessorType.UNSPECIFIED);
        }
    }

    private void scanZipEntryForClassname(ZipFile zipFile, ZipEntry entry, AnnotationProcessorInfo result) throws IOException {
        for (String line : CharStreams.toString(new InputStreamReader(zipFile.getInputStream(entry))).split("\\r?\\n")) {
            if (CLASSNAME.matcher(line).matches()) {
                result.setName(line);
            }         
        }
    }
}
