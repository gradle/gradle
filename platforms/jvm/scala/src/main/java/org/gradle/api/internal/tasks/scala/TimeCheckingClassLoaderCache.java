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

package org.gradle.api.internal.tasks.scala;

import sbt.internal.inc.classpath.AbstractClassLoaderCache;
import sbt.io.IO;
import scala.Function0;
import scala.jdk.javaapi.CollectionConverters;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class implements AbstractClassLoaderCache in a way that allows safe
 * resource release when entries are evicted.
 *
 * Cache is based on file timestamps, because this method is used in sbt implementation.
 */
class TimeCheckingClassLoaderCache implements AbstractClassLoaderCache {
    private final URLClassLoader commonParent;
    private final GuavaBackedClassLoaderCache<Set<TimestampedFile>> cache;

    static class TimestampedFile {
        private final File file;
        private final long timestamp;

        public TimestampedFile(File file) {
            this.file = file;
            this.timestamp = IO.getModifiedTimeOrZero(file);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TimestampedFile that = (TimestampedFile) o;
            return timestamp == that.timestamp &&
                Objects.equals(file, that.file);
        }

        @Override
        public int hashCode() {
            return Objects.hash(file, timestamp);
        }
    }

    public TimeCheckingClassLoaderCache(int maxSize) {
        commonParent = new URLClassLoader(new URL[0]);
        cache = new GuavaBackedClassLoaderCache<>(maxSize);
    }

    @Override
    public ClassLoader commonParent() {
        return commonParent;
    }

    @Override
    public ClassLoader apply(scala.collection.immutable.List<File> files) {
        try {
            List<File> jFiles = CollectionConverters.asJava(files);
            return cache.get(getTimestampedFiles(jFiles), () -> {
                ArrayList<URL> urls = new ArrayList<>(jFiles.size());
                for (File f : jFiles) {
                    urls.add(f.toURI().toURL());
                }
                return new URLClassLoader(urls.toArray(new URL[0]), commonParent);
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ClassLoader cachedCustomClassloader(scala.collection.immutable.List<File> files, Function0<ClassLoader> mkLoader) {
        try {
            return cache.get(getTimestampedFiles(CollectionConverters.asJava(files)), () -> mkLoader.apply());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Set<TimestampedFile> getTimestampedFiles(List<File> fs) {
        return fs.stream().map(TimestampedFile::new).collect(Collectors.toSet());
    }

    @Override
    public void close() throws IOException {
        cache.clear();
        commonParent.close();
    }
}
