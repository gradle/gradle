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
import org.gradle.api.tasks.ScalaLibraryJar;
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
import sbt.internal.inc.classpath.ClassLoaderCache;
import scala.Option;
import scala.collection.JavaConverters;
import xsbti.compile.ClasspathOptionsUtil;
import xsbti.compile.ScalaCompiler;
import xsbti.compile.ZincCompilerUtil;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class ZincScalaCompilerFactory {
    private static final Logger LOGGER = Logging.getLogger(ZincScalaCompilerFactory.class);
    private static final int CLASSLOADER_CACHE_SIZE = 4;
    private static final int COMPILER_CLASSLOADER_CACHE_SIZE = 4;
    private static final GuavaBackedClassLoaderCache<HashCode> CLASSLOADER_CACHE = new GuavaBackedClassLoaderCache<>(CLASSLOADER_CACHE_SIZE);
    private static final ClassLoaderCache COMPILER_CLASSLOADER_CACHE;

    static {
        // Load TimeCheckingClassLoaderCache and use it to create cache via reflection
        // If we detect that we are using zinc 1.2.x, we fallback to default cache
        Class<?> abstractCacheClass;
        Class<?> checkingClass;
        try {
            abstractCacheClass = ZincScalaCompilerFactory.class.getClassLoader().loadClass("sbt.internal.inc.classpath.AbstractClassLoaderCache");
            checkingClass = ZincScalaCompilerFactory.class.getClassLoader().loadClass("org.gradle.api.internal.tasks.scala.TimeCheckingClassLoaderCache");
        } catch (ClassNotFoundException ex) {
            abstractCacheClass = null;
            checkingClass = null;
        }
        if (checkingClass != null) {
            try {
                Constructor<ClassLoaderCache> constructor = ClassLoaderCache.class.getConstructor(abstractCacheClass);
                Object cache = checkingClass.getConstructors()[0].newInstance(COMPILER_CLASSLOADER_CACHE_SIZE);
                COMPILER_CLASSLOADER_CACHE = constructor.newInstance(cache);
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate ClassLoaderCache", e);
            }
        } else {
            COMPILER_CLASSLOADER_CACHE = new ClassLoaderCache(new URLClassLoader(new URL[]{}));
        }
    }

    static ZincScalaCompiler getCompiler(CacheRepository cacheRepository, HashedClasspath hashedScalaClasspath) {
        ClassLoader scalaClassLoader = getCachedClassLoader(hashedScalaClasspath);
        ClassPath scalaClasspath = hashedScalaClasspath.getClasspath();

        ScalaLibraryJar scalaLibraryJar = ScalaLibraryJar.find(scalaClasspath.getAsFiles(), null);

        ScalaInstance scalaInstance = getScalaInstance(scalaClassLoader, scalaClasspath, scalaLibraryJar);
        String zincVersion = ZincCompilerUtil.class.getPackage().getImplementationVersion();
        String scalaVersion = scalaInstance.actualVersion();
        String javaVersion = Jvm.current().getJavaVersion().getMajorVersion();
        String zincCacheKey = String.format("zinc-%s_%s_%s", zincVersion, scalaVersion, javaVersion);
        String zincCacheName = String.format("%s compiler cache", zincCacheKey);
        final PersistentCache zincCache = cacheRepository.cache(zincCacheKey)
            .withDisplayName(zincCacheName)
            .withLockOptions(mode(FileLockManager.LockMode.OnDemand))
            .open();

        final File bridgeJar;
        if (scalaLibraryJar.isCompilerBridgeDistributedInSourceForm()) {
            File compilerBridgeSourceJar = scalaLibraryJar.findCompilerBridgeJar(scalaClasspath);
            bridgeJar = getBridgeJar(zincCache, scalaInstance, compilerBridgeSourceJar, sbt.util.Logger.xlog2Log(new SbtLoggerAdapter()));
        } else {
            bridgeJar = scalaLibraryJar.findCompilerBridgeJar(scalaClasspath);
        }

        ScalaCompiler scalaCompiler = new AnalyzingCompiler(
            scalaInstance,
            ZincUtil.constantBridgeProvider(scalaInstance, bridgeJar),
            ClasspathOptionsUtil.manual(),
            k -> scala.runtime.BoxedUnit.UNIT,
            Option.apply(COMPILER_CLASSLOADER_CACHE)
        );

        return new ZincScalaCompiler(scalaInstance, scalaCompiler, new AnalysisStoreProvider());
    }

    private static ClassLoader getClassLoader(ClassPath classpath) {
        try {
            List<URL> urls = new ArrayList<>();
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
            return CLASSLOADER_CACHE.get(classpath.getHash(), () -> getClassLoader(classpath.getClasspath()));
        } catch (Exception ee) {
            throw new RuntimeException(ee);
        }
    }

    private static ScalaInstance getScalaInstance(ClassLoader scalaClassLoader, ClassPath scalaClasspath, ScalaLibraryJar scalaLibraryJar) {
        final File[] libraryJars = Iterables.toArray(Collections.singletonList(scalaLibraryJar.file), File.class);
        final File[] allJars = Iterables.toArray(scalaClasspath.getAsFiles(), File.class);

        return new ScalaInstance(
            scalaLibraryJar.version,
            scalaClassLoader,
            scalaClassLoader,
            getClassLoader(DefaultClassPath.of(scalaLibraryJar.file)),
            libraryJars,
            allJars,
            allJars,
            Option.empty()
        );
    }

    private static File getBridgeJar(PersistentCache zincCache, ScalaInstance scalaInstance, File compilerBridgeSourceJar, sbt.util.Logger logger) {
        return zincCache.useCache(() -> {
            final File bridgeJar = new File(zincCache.getBaseDir(), "compiler-bridge.jar");

            if (!bridgeJar.exists()) {
                // generate from sources jar
                final Timer timer = Time.startTimer();
                RawCompiler rawCompiler = new RawCompiler(scalaInstance, ClasspathOptionsUtil.manual(), logger);
                scala.collection.Iterable<Path> sourceJars = JavaConverters.collectionAsScalaIterable(
                    Collections.singletonList(compilerBridgeSourceJar.toPath())
                );
                scala.collection.Iterable<Path> xsbtiJars = JavaConverters.collectionAsScalaIterable(
                    Arrays.stream(scalaInstance.allJars()).map(File::toPath).collect(Collectors.toList())
                );
                AnalyzingCompiler$.MODULE$.compileSources(sourceJars, bridgeJar.toPath(), xsbtiJars, "compiler-bridge", rawCompiler, logger);

                final String interfaceCompletedMessage = String.format("Scala Compiler interface compilation took %s", timer.getElapsed());
                if (timer.getElapsedMillis() > 30000) {
                    LOGGER.warn(interfaceCompletedMessage);
                } else {
                    LOGGER.debug(interfaceCompletedMessage);
                }
            }
            return bridgeJar;
        });
    }
}
