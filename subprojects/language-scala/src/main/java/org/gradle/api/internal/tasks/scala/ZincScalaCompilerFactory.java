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
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CacheRepositoryServices;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.util.GFileUtils;
import sbt.ScalaInstance;
import sbt.compiler.AnalyzingCompiler;
import xsbti.compile.JavaCompiler;

import java.io.File;
import java.io.IOException;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class ZincScalaCompilerFactory {
    private static final Logger LOGGER = Logging.getLogger(ZincScalaCompilerFactory.class);

    static Compiler createParallelSafeCompiler(final Iterable<File> scalaClasspath, final Iterable<File> zincClasspath, final xsbti.Logger logger, File gradleUserHome) {
        File zincCacheHomeDir = new File(System.getProperty(ZincScalaCompilerUtil.ZINC_CACHE_HOME_DIR_SYSTEM_PROPERTY, gradleUserHome.getAbsolutePath()));
        CacheRepository cacheRepository = ZincCompilerServices.getInstance(zincCacheHomeDir).get(CacheRepository.class);

        String zincVersion = Setup.zincVersion().published();
        String zincCacheKey = String.format("zinc-%s", zincVersion);
        String zincCacheName = String.format("Zinc %s compiler cache", zincVersion);
        final PersistentCache zincCache = cacheRepository.cache(zincCacheKey)
                .withDisplayName(zincCacheName)
                .withLockOptions(mode(FileLockManager.LockMode.Exclusive))
                .open();

        Compiler compiler;
        try {
            final File cacheDir = zincCache.getBaseDir();

            final String userSuppliedZincDir = System.getProperty("zinc.dir");
            if (userSuppliedZincDir != null && !userSuppliedZincDir.equals(cacheDir.getAbsolutePath())) {
                LOGGER.warn(ZincScalaCompilerUtil.ZINC_DIR_IGNORED_MESSAGE);
            }

            compiler = SystemProperties.getInstance().withSystemProperty(ZincScalaCompilerUtil.ZINC_DIR_SYSTEM_PROPERTY, cacheDir.getAbsolutePath(), new Factory<Compiler>() {
                @Override
                public Compiler create() {
                    Setup zincSetup = createZincSetup(scalaClasspath, zincClasspath, logger);
                    return createCompiler(zincSetup, zincCache, logger);
                }
            });
        } finally {
            zincCache.close();
        }

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
        final String sbtInterfaceFileName = Compiler.interfaceId(instance.actualVersion()) + ".jar";
        final File compilerInterface = new File(setup.cacheDir(), sbtInterfaceFileName);
        if (compilerInterface.exists()) {
            return zincCache.useCache(new Factory<File>() {
                @Override
                public File create() {
                    return compilerInterface;
                }
            });
        }

        try {
            // Compile the interface to a temp file and then copy it to the cache folder.
            // This avoids sporadic cache lock timeouts when the compiler interface JAR takes
            // a long time to generate while avoiding starving multiple compiler daemons.
            final File tmpDir = new File(zincCache.getBaseDir(), "tmp");
            tmpDir.mkdirs();
            final File tempFile = File.createTempFile("zinc", ".jar", tmpDir);
            final Timer timer = Time.startTimer();
            sbt.compiler.IC.compileInterfaceJar(
                    sbtInterfaceFileName,
                    setup.compilerInterfaceSrc(),
                    tempFile,
                    setup.sbtInterface(),
                    instance,
                    logger);
            final String interfaceCompletedMessage = String.format("Zinc interface compilation took %s", timer.getElapsed());
            if (timer.getElapsedMillis() > 30000) {
                LOGGER.warn(interfaceCompletedMessage);
            } else {
                LOGGER.debug(interfaceCompletedMessage);
            }

            return zincCache.useCache(new Factory<File>() {
                public File create() {
                    // Another process may have already copied the compiler interface JAR
                    // Avoid copying over same existing file to avoid locking problems
                    if (!compilerInterface.exists()) {
                        GFileUtils.moveFile(tempFile, compilerInterface);
                    } else {
                        GFileUtils.deleteQuietly(tempFile);
                    }
                    return compilerInterface;
                }
            });
        } catch (IOException e) {
            // fall back to the default logic
            return zincCache.useCache(new Factory<File>() {
                public File create() {
                    return Compiler.compilerInterface(setup, instance, logger);
                }
            });
        }
    }

    private static Setup createZincSetup(Iterable<File> scalaClasspath, Iterable<File> zincClasspath, xsbti.Logger logger) {
        ScalaLocation scalaLocation = ScalaLocation.fromPath(Lists.newArrayList(scalaClasspath));
        SbtJars sbtJars = SbtJars.fromPath(Lists.newArrayList(zincClasspath));
        Setup setup = Setup.create(scalaLocation, sbtJars, Jvm.current().getJavaHome(), true);
        if (LOGGER.isDebugEnabled()) {
            Setup.debug(setup, logger);
        }
        return setup;
    }

    private static class ZincCompilerServices extends DefaultServiceRegistry {
        private static ZincCompilerServices instance;

        private ZincCompilerServices(File gradleUserHome) {
            super(NativeServices.getInstance());

            addProvider(new GlobalScopeServices(true));
            addProvider(new CacheRepositoryServices(gradleUserHome, null));
        }

        public static ZincCompilerServices getInstance(File gradleUserHome) {
            if (instance == null) {
                NativeServices.initialize(gradleUserHome);
                instance = new ZincCompilerServices(gradleUserHome);
            }
            return instance;
        }
    }
}
