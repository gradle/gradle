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

import com.google.common.collect.Lists;
import com.typesafe.zinc.Compiler;
import com.typesafe.zinc.SbtJars;
import com.typesafe.zinc.ScalaLocation;
import com.typesafe.zinc.Setup;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.util.GFileUtils;
import sbt.ScalaInstance;
import sbt.compiler.AnalyzingCompiler;
import xsbti.compile.JavaCompiler;

import java.io.File;
import java.io.IOException;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

class ZincScalaCompilerFactory {
    private static final Logger LOGGER = Logging.getLogger(ZincScalaCompilerFactory.class);

    private static final String ZINC_CACHE_HOME_DIR_SYSTEM_PROPERTY = "org.gradle.zinc.home.dir";
    private static final String ZINC_DIR_SYSTEM_PROPERTY = "zinc.dir";
    private static final String ZINC_DIR_IGNORED_MESSAGE = "In order to guarantee parallel safe Scala compilation, Gradle does not support the '" + ZINC_DIR_SYSTEM_PROPERTY + "' system property and ignores any value provided.";

    static Compiler createParallelSafeCompiler(final Iterable<File> scalaClasspath, final Iterable<File> zincClasspath, final xsbti.Logger logger, File gradleUserHome) {
        File zincCacheHomeDir = new File(System.getProperty(ZINC_CACHE_HOME_DIR_SYSTEM_PROPERTY, gradleUserHome.getAbsolutePath()));
        CacheRepository cacheRepository = ZincCompilerServices.getInstance(zincCacheHomeDir).get(CacheRepository.class);

        String zincVersion = Setup.zincVersion().published();
        String zincCacheKey = String.format("zinc-%s", zincVersion);
        String zincCacheName = String.format("Zinc %s compiler cache", zincVersion);
        final PersistentCache zincCache = cacheRepository.cache(zincCacheKey)
                .withDisplayName(zincCacheName)
                .withLockOptions(mode(FileLockManager.LockMode.Exclusive))
                .open();
        final File cacheDir = zincCache.getBaseDir();

        final String userSuppliedZincDir = System.getProperty("zinc.dir");
        if (userSuppliedZincDir != null && !userSuppliedZincDir.equals(cacheDir.getAbsolutePath())) {
            LOGGER.warn(ZINC_DIR_IGNORED_MESSAGE);
        }

        Compiler compiler = SystemProperties.getInstance().withSystemProperty(ZINC_DIR_SYSTEM_PROPERTY, cacheDir.getAbsolutePath(), new Factory<Compiler>() {
            @Override
            public Compiler create() {
                Setup zincSetup = createZincSetup(scalaClasspath, zincClasspath, logger);
                return createCompiler(zincSetup, zincCache, logger);
            }
        });
        zincCache.close();

        return compiler;
    }

    private static Compiler createCompiler(final Setup setup, final PersistentCache zincCache, final xsbti.Logger logger) {
        return Compiler.compilerCache().get(setup, new scala.runtime.AbstractFunction0<Compiler>() {
            public Compiler apply() {
                ScalaInstance instance = Compiler.scalaInstance(setup);
                File interfaceJar = getCompilerInterface(setup, instance, zincCache, logger);
                AnalyzingCompiler scalac = Compiler.newScalaCompiler(instance, interfaceJar, logger);
                JavaCompiler javac = Compiler.newJavaCompiler(instance, setup.javaHome(), setup.forkJava());
                return new Compiler(scalac, javac);
            }
        });
    }

    // parallel safe version of Compiler.compilerInterface()
    private static File getCompilerInterface(final Setup setup, final ScalaInstance instance, PersistentCache zincCache, final xsbti.Logger logger) {
        String sbtInterfaceFileName =
            String.format("compiler-interface-%s.jar", Compiler.interfaceId(instance.actualVersion()));
        final File compilerInterface = new File(setup.cacheDir(), sbtInterfaceFileName);
        if (compilerInterface.exists()) {
            return compilerInterface;
        }

        try {
            // Let's try to compile the interface to a temp file and then copy it to the cache folder.
            // Compiling an interface is an expensive operation which affects performance on machines
            // with many CPUs and we don't want to block while compiling.
            final File tempFile = File.createTempFile("zinc", ".jar");
            sbt.compiler.IC.compileInterfaceJar(
                    sbtInterfaceFileName,
                    setup.compilerInterfaceSrc(),
                    tempFile,
                    setup.sbtInterface(),
                    instance,
                    logger);
            return zincCache.useCache("coping sbt interface", new Factory<File>() {
                public File create() {
                    GFileUtils.copyFile(tempFile, compilerInterface);
                    return compilerInterface;
                }
            });
        } catch (IOException e) {
            // fall back to the default logic
            return zincCache.useCache("compiling sbt interface", new Factory<File>() {
                public File create() {
                    return Compiler.compilerInterface(setup, instance, logger);
                }
            });
        }
    }

    private static Setup createZincSetup(Iterable<File> scalaClasspath, Iterable<File> zincClasspath, xsbti.Logger logger) {
        ScalaLocation scalaLocation = ScalaLocation.fromPath(Lists.newArrayList(scalaClasspath));
        SbtJars sbtJars = SbtJars.fromPath(Lists.newArrayList(zincClasspath));
        Setup setup = Setup.create(scalaLocation, sbtJars, null, false);
        if (LOGGER.isDebugEnabled()) {
            Setup.debug(setup, logger);
        }
        return setup;
    }
}
