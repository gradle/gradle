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

    private static final Pattern CLASSNAME = Pattern.compile("([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*");

    public static final String META_INF_INCAP = "META-INF/incap";

    // Per the JSR-269 spec, you can have multiple Annotation Processor classes declared in
    // META-INF/services/javax.annotation.processing.Processor
    //
    //   my.annotation.processor.Processor1
    //   my.annotation.processor.Processor2
    //
    // As a policy decision (for now), we require all processors in the file to be incremental,
    // if any are.  If we find a "META-INF/incap" file, we consider them all to be incremental.
    // We only record one processor class name from each classpath artifact.  It's only used
    // for logging, so it doesn't really matter which one we record.

    @Override
    public Map<String, String> calculate(File dirOrJar, FileType fileType) {
        Map<String, String> result = Maps.newHashMap();
        result.put(AnnotationProcessorInfo.PROCESSOR_KEY, "false");
        result.put(AnnotationProcessorInfo.INCREMENTAL_KEY, "false");
        // Set the cache entry's default name to the dir/jar path, for debugging.
        // If we discover that this is an annotation processor, we will set it to the class name.
        result.put(AnnotationProcessorInfo.NAME_KEY, dirOrJar.getPath());

        if (fileType == FileType.Directory) {
            File spec = new File(dirOrJar, "META-INF/services/javax.annotation.processing.Processor");
            if (spec.isFile()) {
                markAsProcessor(result);
                scanFile(spec, result);
                if (new File(dirOrJar, META_INF_INCAP).isFile()) {
                    setIncremental(result);
                }
            }
            return result;
        }

        if (fileType == FileType.RegularFile && FileUtils.hasExtension(dirOrJar, "jar")) {
            try {
                ZipFile zipFile = new ZipFile(dirOrJar);
                try {
                    ZipEntry entry = zipFile.getEntry("META-INF/services/javax.annotation.processing.Processor");
                    if (entry != null) {
                        markAsProcessor(result);
                        scanZipEntry(zipFile, entry, result);
                        if (zipFile.getEntry(META_INF_INCAP) != null) {
                            setIncremental(result);
                        }
                    }
                } finally {
                    zipFile.close();
                }
            } catch (IOException e) {
                DeprecationLogger.nagUserWith("Malformed jar [" + dirOrJar.getName() + "] found on compile classpath. Gradle 5.0 will no longer allow malformed jars on compile classpath.");
            }
        }

        return result;
    }

    private void scanFile(File spec, final Map<String, String> result) {
        try {
            Files.asCharSource(spec, Charsets.UTF_8)
                .readLines(new LineProcessor<Void>() {
                    @Override
                    public boolean processLine(String line) throws IOException {
                        scanLineForClassName(line, result);
                        return true;
                    }

                    @Override
                    public Void getResult() {
                        return null;
                    }
                });
            if (!isNamed(result)) {
                result.put(AnnotationProcessorInfo.NAME_KEY, spec.getName());
            }
        } catch (IOException iox) {
            throw UncheckedException.throwAsUncheckedException(iox);
        }
    }

    private void scanZipEntry(ZipFile zipFile, ZipEntry entry, Map<String, String> result) {
        try {
            for (String line : CharStreams.toString(new InputStreamReader(zipFile.getInputStream(entry))).split("\\r?\\n")) {
                scanLineForClassName(line, result);
            }
        } catch (Exception x) {
            throw UncheckedException.throwAsUncheckedException(x);
        }
    }

    private void scanLineForClassName(String line, Map<String, String> result) {
        if (CLASSNAME.matcher(line).matches()) {
            result.put(AnnotationProcessorInfo.NAME_KEY, line);
        }
    }

    private Boolean isNamed(Map<String, String> result) {
        return !result.get(AnnotationProcessorInfo.NAME_KEY).equals(AnnotationProcessorInfo.UNKNOWN_NAME);
    }

    private Boolean isProcessor(Map<String, String> result) {
        return !"true".equals(result.get(AnnotationProcessorInfo.PROCESSOR_KEY));
    }

    private void setIncremental(Map<String, String> result) {
        result.put(AnnotationProcessorInfo.INCREMENTAL_KEY, "true");
    }

    private void markAsProcessor(Map<String, String> result) {
        result.put(AnnotationProcessorInfo.PROCESSOR_KEY, "true");
    }

    private void setName(Map<String, String> result, String name) {
        result.put(AnnotationProcessorInfo.NAME_KEY, name);
    }
}
