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
import org.gradle.cache.UnscopedCacheBuilderFactory;
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
import xsbti.ArtifactInfo;
import xsbti.compile.ClasspathOptionsUtil;
import xsbti.compile.ScalaCompiler;
import xsbti.compile.ZincCompilerUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

@SuppressWarnings("deprecation")
public class ZincScalaCompilerFactory {
    private static final Logger LOGGER = Logging.getLogger(ZincScalaCompilerFactory.class);
    private static final int CLASSLOADER_CACHE_SIZE = 4;
    private static final int COMPILER_CLASSLOADER_CACHE_SIZE = 4;
    private static final String SCALA_3_COMPILER_ID = "scala3-compiler_3";
    private static final String SCALA_3_LIBRARY_ID = "scala3-library_3";
    private static final GuavaBackedClassLoaderCache<HashCode> CLASSLOADER_CACHE = new GuavaBackedClassLoaderCache<HashCode>(CLASSLOADER_CACHE_SIZE);
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

    static ZincScalaCompiler getCompiler(UnscopedCacheBuilderFactory unscopedCacheBuilderFactory, HashedClasspath hashedScalaClasspath) {
        ScalaInstance scalaInstance;
        try {
            scalaInstance = getScalaInstance(hashedScalaClasspath);
        } catch (Exception e) {
            throw new RuntimeException("Failed create instance of the scala compiler", e);
        }

        String zincVersion = ZincCompilerUtil.class.getPackage().getImplementationVersion();
        String scalaVersion = scalaInstance.actualVersion();

        String javaVersion = Jvm.current().getJavaVersion().getMajorVersion();
        String zincCacheKey = String.format("zinc-%s_%s_%s", zincVersion, scalaVersion, javaVersion);
        String zincCacheName = String.format("%s compiler cache", zincCacheKey);
        final PersistentCache zincCache = unscopedCacheBuilderFactory.cache(zincCacheKey)
            .withDisplayName(zincCacheName)
            .withLockOptions(mode(FileLockManager.LockMode.OnDemandExclusive))
            .open();


        File compilerBridgeJar;
        if (isScala3(scalaVersion)) {
            compilerBridgeJar = findFile("scala3-sbt-bridge", hashedScalaClasspath.getClasspath());
        } else {
            File compilerBridgeSourceJar = findFile("compiler-bridge", hashedScalaClasspath.getClasspath());
            compilerBridgeJar = getBridgeJar(zincCache, scalaInstance, compilerBridgeSourceJar, sbt.util.Logger.xlog2Log(new SbtLoggerAdapter()));
        }

        ScalaCompiler scalaCompiler = new AnalyzingCompiler(
            scalaInstance,
            ZincUtil.constantBridgeProvider(scalaInstance, compilerBridgeJar),
            ClasspathOptionsUtil.manual(),
            k -> scala.runtime.BoxedUnit.UNIT,
            Option.apply(COMPILER_CLASSLOADER_CACHE)
        );

        return new ZincScalaCompiler(scalaInstance, scalaCompiler, new AnalysisStoreProvider());
    }

    private static ClassLoader getClassLoader(ClassPath classpath, ClassLoader parent) {
        try {
            List<URL> urls = new ArrayList<URL>();
            for (File file : classpath.getAsFiles()) {
                // Having the bridge in the classloader breaks zinc
                if (!file.toString().contains("scala3-sbt-bridge")) {
                    urls.add(file.toURI().toURL());
                }
            }
            if (parent != null) {
                return new URLClassLoader(urls.toArray(new URL[0]), parent);
            } else {
                return new URLClassLoader(urls.toArray(new URL[0]));
            }
        } catch (Exception ee) {
            throw new RuntimeException(ee);
        }
    }

    private static boolean isScala3(String version) {
        return version.startsWith("3.");
    }

    private static ClassLoader getCachedClassLoader(HashedClasspath classpath, ClassLoader parent) {
        try {
            return CLASSLOADER_CACHE.get(classpath.getHash(), new Callable<ClassLoader>() {
                @Override
                public ClassLoader call() throws Exception {
                    return getClassLoader(classpath.getClasspath(), parent);
                }
            });
        } catch (Exception ee) {
            throw new RuntimeException(ee);
        }
    }

    private static ScalaInstance getScalaInstance(HashedClasspath hashedScalaClasspath) throws MalformedURLException {
        ClassPath scalaClasspath = hashedScalaClasspath.getClasspath();
        File libraryJar = findFile(ArtifactInfo.ScalaLibraryID, scalaClasspath);
        URL[] libraryUrls;
        boolean isScala3 = false;
        try {
            File library3Jar = findFile(SCALA_3_LIBRARY_ID, scalaClasspath);
            isScala3 = true;
            libraryUrls = new URL[]{library3Jar.toURI().toURL(), libraryJar.toURI().toURL()};
        } catch (IllegalStateException e) {
            libraryUrls = new URL[]{libraryJar.toURI().toURL()};
        }
        ClassLoader scalaLibraryClassLoader;
        ClassLoader scalaClassLoader;
        if (isScala3) {
            scalaLibraryClassLoader = new ScalaCompilerLoader(libraryUrls, xsbti.Reporter.class.getClassLoader());
            scalaClassLoader = getCachedClassLoader(hashedScalaClasspath, scalaLibraryClassLoader);
        } else {
            scalaLibraryClassLoader = getClassLoader(DefaultClassPath.of(libraryJar), null);
            scalaClassLoader = getCachedClassLoader(hashedScalaClasspath, null);
        }
        String scalaVersion = getScalaVersion(scalaClassLoader);

        File compilerJar;
        if (isScala3) {
            compilerJar = findFile(SCALA_3_COMPILER_ID, scalaClasspath);
        } else {
            compilerJar = findFile(ArtifactInfo.ScalaCompilerID, scalaClasspath);
        }

        return new ScalaInstance(
            scalaVersion,
            scalaClassLoader,
            scalaLibraryClassLoader,
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
                scala.collection.Iterable<Path> sourceJars = JavaConverters.collectionAsScalaIterable(Collections.singletonList(compilerBridgeSourceJar.toPath()));
                List<Path> xsbtiJarsAsPath = Arrays.stream(scalaInstance.allJars()).map(File::toPath).collect(Collectors.toList());
                scala.collection.Iterable<Path> xsbtiJars = JavaConverters.collectionAsScalaIterable(xsbtiJarsAsPath);
                AnalyzingCompiler$.MODULE$.compileSources(sourceJars, bridgeJar.toPath(), xsbtiJars, "compiler-bridge", rawCompiler, logger);

                final String interfaceCompletedMessage = String.format("Scala Compiler interface compilation took %s", timer.getElapsed());
                if (timer.getElapsedMillis() > 30000) {
                    LOGGER.info(interfaceCompletedMessage);
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
            props.load(scalaClassLoader.getResourceAsStream("compiler.properties"));
            return props.getProperty("version.number");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to determine scala version");
        }
    }


}
