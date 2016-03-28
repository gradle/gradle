/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.typesafe.zinc.*;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.*;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.jvm.Jvm;
import org.gradle.util.GFileUtils;
import scala.Option;
import xsbti.F0;

import java.io.File;
import java.io.Serializable;
import java.util.List;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class ZincScalaCompiler implements Compiler<ScalaJavaJointCompileSpec>, Serializable {
    private static final Logger LOGGER = Logging.getLogger(ZincScalaCompiler.class);
    private final Iterable<File> scalaClasspath;
    private Iterable<File> zincClasspath;
    private final File gradleUserHome;

    public static final String ZINC_CACHE_HOME_DIR_SYSTEM_PROPERTY = "org.gradle.zinc.home.dir";
    private static final String ZINC_DIR_SYSTEM_PROPERTY = "zinc.dir";
    public static final String ZINC_DIR_IGNORED_MESSAGE = "In order to guarantee parallel safe Scala compilation, Gradle does not support the '" + ZINC_DIR_SYSTEM_PROPERTY + "' system property and ignores any value provided.";

    public ZincScalaCompiler(Iterable<File> scalaClasspath, Iterable<File> zincClasspath, File gradleUserHome) {
        this.scalaClasspath = scalaClasspath;
        this.zincClasspath = zincClasspath;
        this.gradleUserHome = gradleUserHome;
    }

    @Override
    public WorkResult execute(ScalaJavaJointCompileSpec spec) {
        return Compiler.execute(scalaClasspath, zincClasspath, gradleUserHome, spec);
    }

    // need to defer loading of Zinc/sbt/Scala classes until we are
    // running in the compiler daemon and have them on the class path
    private static class Compiler {
        static WorkResult execute(final Iterable<File> scalaClasspath, final Iterable<File> zincClasspath, File gradleUserHome, final ScalaJavaJointCompileSpec spec) {
            LOGGER.info("Compiling with Zinc Scala compiler.");

            final xsbti.Logger logger = new SbtLoggerAdapter();

            com.typesafe.zinc.Compiler compiler = createParallelSafeCompiler(scalaClasspath, zincClasspath, logger, gradleUserHome);

            List<String> scalacOptions = new ZincScalaCompilerArgumentsGenerator().generate(spec);
            List<String> javacOptions = new JavaCompilerArgumentsBuilder(spec).includeClasspath(false).build();
            Inputs inputs = Inputs.create(ImmutableList.copyOf(spec.getClasspath()), ImmutableList.copyOf(spec.getSource()), spec.getDestinationDir(),
                    scalacOptions, javacOptions, spec.getScalaCompileOptions().getIncrementalOptions().getAnalysisFile(), spec.getAnalysisMap(), "mixed", getIncOptions(), true);
            if (LOGGER.isDebugEnabled()) {
                Inputs.debug(inputs, logger);
            }

            if (spec.getScalaCompileOptions().isForce()) {
                GFileUtils.deleteDirectory(spec.getDestinationDir());
            }

            try {
                compiler.compile(inputs, logger);
            } catch (xsbti.CompileFailed e) {
                throw new CompilationFailedException(e);
            }

            return new SimpleWorkResult(true);
        }

        private static IncOptions getIncOptions() {
            //The values are based on what I have found in sbt-compiler-maven-plugin.googlecode.com and zinc documentation
            //Hard to say what effect they have on the incremental build
            int transitiveStep = 3;
            double recompileAllFraction = 0.5d;
            boolean relationsDebug = false;
            boolean apiDebug = false;
            int apiDiffContextSize = 5;
            Option<File> apiDumpDirectory = Option.empty();
            boolean transactional = false;
            Option<File> backup = Option.empty();

            // We need to use the deprecated constructor as it is compatible with certain previous versions of the Zinc compiler
            @SuppressWarnings("deprecation")
            IncOptions options = new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, transactional, backup);
            return options;
        }

        static com.typesafe.zinc.Compiler createCompiler(Iterable<File> scalaClasspath, Iterable<File> zincClasspath, xsbti.Logger logger) {
            ScalaLocation scalaLocation = ScalaLocation.fromPath(Lists.newArrayList(scalaClasspath));
            SbtJars sbtJars = SbtJars.fromPath(Lists.newArrayList(zincClasspath));
            Setup setup = Setup.create(scalaLocation, sbtJars, Jvm.current().getJavaHome(), true);
            if (LOGGER.isDebugEnabled()) {
                Setup.debug(setup, logger);
            }
            com.typesafe.zinc.Compiler compiler = com.typesafe.zinc.Compiler.getOrCreate(setup, logger);
            return compiler;
        }

        static com.typesafe.zinc.Compiler createParallelSafeCompiler(final Iterable<File> scalaClasspath, final Iterable<File> zincClasspath, final xsbti.Logger logger, File gradleUserHome) {
            File zincCacheHomeDir = new File(System.getProperty(ZINC_CACHE_HOME_DIR_SYSTEM_PROPERTY, gradleUserHome.getAbsolutePath()));
            CacheRepository cacheRepository = ZincCompilerServices.getInstance(zincCacheHomeDir).get(CacheRepository.class);
            final PersistentCache zincCache = cacheRepository.cache("zinc")
                                                            .withDisplayName("Zinc compiler cache")
                                                            .withLockOptions(mode(FileLockManager.LockMode.Exclusive))
                                                            .open();
            final File cacheDir = zincCache.getBaseDir();

            final String userSuppliedZincDir = System.getProperty("zinc.dir");
            if (userSuppliedZincDir != null && !userSuppliedZincDir.equals(cacheDir.getAbsolutePath())) {
                LOGGER.warn(ZINC_DIR_IGNORED_MESSAGE);
            }

            com.typesafe.zinc.Compiler compiler = SystemProperties.getInstance().withSystemProperty(ZINC_DIR_SYSTEM_PROPERTY, cacheDir.getAbsolutePath(), new Factory<com.typesafe.zinc.Compiler>() {
                @Override
                public com.typesafe.zinc.Compiler create() {
                    return zincCache.useCache("initialize", new Factory<com.typesafe.zinc.Compiler>() {
                        @Override
                        public com.typesafe.zinc.Compiler create() {
                            return createCompiler(scalaClasspath, zincClasspath, logger);
                        }
                    });
                }
            });
            zincCache.close();

            return compiler;
        }
    }

    private static class SbtLoggerAdapter implements xsbti.Logger {
        @Override
        public void error(F0<String> msg) {
            LOGGER.error(msg.apply());
        }

        @Override
        public void warn(F0<String> msg) {
            LOGGER.warn(msg.apply());
        }

        @Override
        public void info(F0<String> msg) {
            LOGGER.info(msg.apply());
        }

        @Override
        public void debug(F0<String> msg) {
            LOGGER.debug(msg.apply());
        }

        @Override
        public void trace(F0<Throwable> exception) {
            LOGGER.trace(exception.apply().toString());
        }
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
