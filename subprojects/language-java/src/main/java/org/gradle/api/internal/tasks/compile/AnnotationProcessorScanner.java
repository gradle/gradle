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
import org.gradle.internal.FileUtils;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileType;
import org.gradle.util.DeprecationLogger;

/**
 * Discovers relevant properties of annotation processors.
 */
class AnnotationProcessorScanner implements FileContentCacheFactory.Calculator<Map<String, String>> {

    private static final Pattern CLASSNAME = Pattern.compile("(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\‌​.)+\\p{javaJavaIdentifierPart}\\p{javaJavaIdentifierPart}*");

    private static final String INCAP_DECLARATION = "INCAP";

    private final Map<String, String> result = Maps.newHashMap(ImmutableMap.<String, String>builder()
        .put(AnnotationProcessorInfo.PROCESSOR_KEY, "false")
        .put(AnnotationProcessorInfo.INCREMENTAL_KEY, "false")
        .put(AnnotationProcessorInfo.NAME_KEY, AnnotationProcessorInfo.UNKNOWN_NAME)
        .build());

    @Override
    public Map<String, String> calculate(File file, FileType fileType) {
        if (fileType == FileType.Directory) {
            File spec = new File(file, "META-INF/services/javax.annotation.processing.Processor");
            if (spec.isFile()) {
                scanFile(spec);
            }
            return result;
        }

        if (fileType == FileType.RegularFile && FileUtils.hasExtension(file, "jar")) {
            try {
                ZipFile zipFile = new ZipFile(file);
                try {
                    ZipEntry entry = zipFile.getEntry("META-INF/services/javax.annotation.processing.Processor");
                    if (entry != null) {
                        scanZipEntry(zipFile, entry);
                    }
                } finally {
                    zipFile.close();
                }
            } catch (IOException e) {
                DeprecationLogger.nagUserWith("Malformed jar [" + file.getName() + "] found on compile classpath. Gradle 5.0 will no longer allow malformed jars on compile classpath.");
            }
        }

        return result;
    }

    private void scanFile(File spec) {
        try {
            Files.asCharSource(spec, Charsets.UTF_8)
                .readLines(new LineProcessor<Void>() {
                    @Override
                    public boolean processLine(String line) throws IOException {
                        processLine(line);
                        return true;
                    }

                    @Override
                    public Void getResult() {
                        return null;
                    }
                });
            if (!isNamed()) {
                result.put(AnnotationProcessorInfo.NAME_KEY, spec.getName());
            }
        } catch (IOException iox) {
            throw UncheckedException.throwAsUncheckedException(iox);
        }
    }

    private void scanZipEntry(ZipFile zipFile, ZipEntry entry) {
        try {
            for (String line : CharStreams.toString(new InputStreamReader(zipFile.getInputStream(entry))).split("\\r?\\n")) {
                processLine(line);
            }
        } catch (Exception x) {
            throw UncheckedException.throwAsUncheckedException(x);
        }
    }

    private void processLine(String line) {
        if (line.contains(INCAP_DECLARATION)) {
            setIncremental();
        } else if (CLASSNAME.matcher(line).matches()) {
            setName(line);
        }
    }

    private Boolean isNamed() {
        return !result.get(AnnotationProcessorInfo.NAME_KEY).equals(AnnotationProcessorInfo.UNKNOWN_NAME);
    }

    private void setIncremental() {
        result.put(AnnotationProcessorInfo.INCREMENTAL_KEY, "true");
    }

    private void setName(String name) {
        result.put(AnnotationProcessorInfo.NAME_KEY, name);
    }
}
