/*
 * Copyright 2018 the original author or authors.
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

import org.apache.tools.zip.ZipFile;
import org.gradle.cache.internal.FileContentCache;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.file.FileType;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.io.IOException;

public class AnnotationProcessorDetector {
    private final FileContentCache<Boolean> cache;

    public AnnotationProcessorDetector(FileContentCacheFactory cacheFactory) {
        cache = cacheFactory.newCache("annotation-processors", 20000, new AnnotationServiceLocator(), BaseSerializerFactory.BOOLEAN_SERIALIZER);
    }

    public boolean containsProcessors(File jarOrClassesDir) {
        return cache.get(jarOrClassesDir);
    }

    private static class AnnotationServiceLocator implements FileContentCacheFactory.Calculator<Boolean> {
        @Override
        public Boolean calculate(File file, FileType fileType) {
            if (fileType == FileType.Directory) {
                return new File(file, "META-INF/services/javax.annotation.processing.Processor").isFile();
            }

            if (fileType == FileType.RegularFile && FileUtils.hasExtensionIgnoresCase(file.getName(), ".jar")) {
                try {
                    ZipFile zipFile = new ZipFile(file);
                    try {
                        return zipFile.getEntry("META-INF/services/javax.annotation.processing.Processor") != null;
                    } finally {
                        zipFile.close();
                    }
                } catch (IOException e) {
                    DeprecationLogger.nagUserWith("Malformed jar [" + file.getName() + "] found on compile classpath. Gradle 5.0 will no longer allow malformed jars on the compile classpath.");
                }
            }

            return false;
        }
    }
}
