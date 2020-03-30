/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.Iterables;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import sbt.internal.inc.AnalyzingCompiler;
import sbt.internal.inc.AnalyzingCompiler$;
import sbt.internal.inc.RawCompiler;
import sbt.internal.inc.ScalaInstance;
import sbt.internal.inc.ZincUtil;
import sbt.internal.inc.classpath.AbstractClassLoaderCache;
import sbt.internal.inc.classpath.ClassLoaderCache;
import sbt.io.IO;
import scala.Function0;
import scala.Option;
import scala.collection.JavaConverters;
import xsbti.ArtifactInfo;
import xsbti.compile.ClasspathOptionsUtil;
import xsbti.compile.ScalaCompiler;
import xsbti.compile.ZincCompilerUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class ZincScalaCompilerFactory {
    private static final Logger LOGGER = Logging.getLogger(ZincScalaCompilerFactory.class);
    private static final int CLASSLOADER_CACHE_SIZE = 4;
    private static final int COMPILER_CLASSLOADER_CACHE_SIZE = 4;
    private static final GuavaBackedClassLoaderCache<HashCode> CLASSLOADER_CACHE = new GuavaBackedClassLoaderCache<HashCode>(CLASSLOADER_CACHE_SIZE);
    private static final TimeCheckingClassLoaderCache COMPILER_CLASSLOADER_CACHE = new TimeCheckingClassLoaderCache(COMPILER_CLASSLOADER_CACHE_SIZE);

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

    /**
     * This class implements AbstractClassLoaderCache in a way that allows safe
     * resource release when entries are evicted.
     *
     * Cache is based on file timestamps, because this method is used in sbt implementation.
     */
    static class TimeCheckingClassLoaderCache implements AbstractClassLoaderCache {
        private final URLClassLoader commonParent;
        private final GuavaBackedClassLoaderCache<Set<TimestampedFile>> cache;

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
                List<File> jFiles = JavaConverters.seqAsJavaList(files);
                return cache.get(getTimestampedFiles(jFiles), () -> {
                    ArrayList<URL> urls = new ArrayList<>(jFiles.size());
                    for (File f: jFiles) {
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
                return cache.get(getTimestampedFiles(JavaConverters.seqAsJavaList(files)), () -> mkLoader.apply());
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

    static ZincScalaCompiler getCompiler(CacheRepository cacheRepository, HashedClasspath hashedScalaClasspath) {
        ScalaInstance scalaInstance = getScalaInstance(hashedScalaClasspath);
        String zincVersion = ZincCompilerUtil.class.getPackage().getImplementationVersion();
        String scalaVersion = scalaInstance.actualVersion();
        String javaVersion = Jvm.current().getJavaVersion().getMajorVersion();
        String zincCacheKey = String.format("zinc-%s_%s_%s", zincVersion, scalaVersion, javaVersion);
        String zincCacheName = String.format("%s compiler cache", zincCacheKey);
        final PersistentCache zincCache = cacheRepository.cache(zincCacheKey)
                .withDisplayName(zincCacheName)
                .withLockOptions(mode(FileLockManager.LockMode.OnDemand))
                .open();

        File compilerBridgeSourceJar = findFile("compiler-bridge", hashedScalaClasspath.getClasspath());
        File bridgeJar = getBridgeJar(zincCache, scalaInstance, compilerBridgeSourceJar, sbt.util.Logger.xlog2Log(new SbtLoggerAdapter()));
        ScalaCompiler scalaCompiler = new AnalyzingCompiler(
            scalaInstance,
            ZincUtil.constantBridgeProvider(scalaInstance, bridgeJar),
            ClasspathOptionsUtil.auto(),
            k -> scala.runtime.BoxedUnit.UNIT,
            Option.apply(new ClassLoaderCache(COMPILER_CLASSLOADER_CACHE))
        );

        return new ZincScalaCompiler(scalaInstance, scalaCompiler, new AnalysisStoreProvider());
    }

    private static ClassLoader getClassLoader(ClassPath classpath) {
        try {
            List<URL> urls = new ArrayList<URL>();
            for (File file : classpath.getAsFiles()) {
                urls.add(file.toURI().toURL());
            }
            return new URLClassLoader(urls.toArray(new URL[0]));
        } catch (Exception ee) {
            throw new RuntimeException(ee);
        }
    }

    private static ClassLoader getCachedClassLoader(HashedClasspath classpath) {
        try {
            return CLASSLOADER_CACHE.get(classpath.getHash(), new Callable<ClassLoader>() {
                @Override
                public ClassLoader call() throws Exception {
                    return getClassLoader(classpath.getClasspath());
                }
            });
        } catch (Exception ee) {
            throw new RuntimeException(ee);
        }
    }

    private static ScalaInstance getScalaInstance(HashedClasspath hashedScalaClasspath) {
        ClassLoader scalaClassLoader = getCachedClassLoader(hashedScalaClasspath);
        String scalaVersion = getScalaVersion(scalaClassLoader);
        ClassPath scalaClasspath = hashedScalaClasspath.getClasspath();

        File libraryJar = findFile(ArtifactInfo.ScalaLibraryID, scalaClasspath);
        File compilerJar = findFile(ArtifactInfo.ScalaCompilerID, scalaClasspath);

        return new ScalaInstance(
                scalaVersion,
                scalaClassLoader,
                getClassLoader(DefaultClassPath.of(libraryJar)),
                libraryJar,
                compilerJar,
                Iterables.toArray(scalaClasspath.getAsFiles(), File.class),
                Option.empty()
        );
    }

    private static File getBridgeJar(PersistentCache zincCache, ScalaInstance scalaInstance, File compilerBridgeSourceJar, sbt.util.Logger logger) {
        return zincCache.useCache(() -> {
            final File bridgeJar = new File(zincCache.getBaseDir(), "compiler-bridge.jar");
            if (bridgeJar.exists()) {
                // compiler interface exists, use it
                return bridgeJar;
            } else {
                // generate from sources jar
                final Timer timer = Time.startTimer();
                RawCompiler rawCompiler = new RawCompiler(scalaInstance, ClasspathOptionsUtil.manual(), logger);
                scala.collection.Iterable<File> sourceJars = JavaConverters.collectionAsScalaIterable(Collections.singletonList(compilerBridgeSourceJar));
                scala.collection.Iterable<File> xsbtiJars = JavaConverters.collectionAsScalaIterable(Arrays.asList(scalaInstance.allJars()));
                AnalyzingCompiler$.MODULE$.compileSources(sourceJars, bridgeJar, xsbtiJars, "compiler-bridge", rawCompiler, logger);

                final String interfaceCompletedMessage = String.format("Scala Compiler interface compilation took %s", timer.getElapsed());
                if (timer.getElapsedMillis() > 30000) {
                    LOGGER.warn(interfaceCompletedMessage);
                } else {
                    LOGGER.debug(interfaceCompletedMessage);
                }

                return bridgeJar;
            }
        });
    }

    private static File findFile(String prefix, ClassPath classpath) {
        for (File f : classpath.getAsFiles()) {
            if (f.getName().startsWith(prefix)) {
                return f;
            }
        }
        throw new IllegalStateException(String.format("Cannot find any files starting with %s in %s", prefix, classpath.getAsFiles()));
    }

    private static String getScalaVersion(ClassLoader scalaClassLoader) {
        try {
            Properties props = new Properties();
            props.load(scalaClassLoader.getResourceAsStream("library.properties"));
            return props.getProperty("version.number");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to determine scala version");
        }
    }


}
