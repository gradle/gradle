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
import com.google.common.collect.Sets;
import org.apache.tools.ant.AntClassLoader;
import org.gradle.cache.internal.Cache;
import org.gradle.cache.internal.MapBackedCache;
import org.gradle.internal.Factory;
import sbt.internal.inc.ScalaInstance;
import sbt.internal.inc.ZincUtil;
import scala.Option;
import xsbti.ArtifactInfo;
import xsbti.compile.ClasspathOptionsUtil;
import xsbti.compile.ScalaCompiler;
import xsbti.compile.ZincCompilerUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ZincScalaCompilerFactory {

    private static final Cache<Set<File>, ClassLoader> CLASS_LOADER_CACHE = new MapBackedCache<>(new ConcurrentHashMap<>());
    private static final Cache<Set<File>, ScalaInstance> SCALA_INSTANCE_CACHE = new MapBackedCache<>(new ConcurrentHashMap<>());
    private static final Cache<Set<File>, ZincScalaCompiler> COMPILER_CACHE = new MapBackedCache<>(new ConcurrentHashMap<>());
    private static final AnalysisStoreProvider ANALYSIS_STORE_PROVIDER = new AnalysisStoreProvider();

    private static ClassLoader getClassLoader(final Iterable<File> classpath) {
        return CLASS_LOADER_CACHE.get(Sets.newHashSet(classpath), new Factory<ClassLoader>() {
            @Nullable
            @Override
            public ClassLoader create() {
                AntClassLoader cl = new AntClassLoader(Thread.currentThread().getContextClassLoader(), false);
                for (File file : classpath) {
                    cl.addPathComponent(file);
                }
                return cl;
            }
        });
    }

    private static ScalaInstance getScalaInstance(final Iterable<File> scalaClasspath) {
        return SCALA_INSTANCE_CACHE.get(Sets.newHashSet(scalaClasspath), new Factory<ScalaInstance>() {
            @Nullable
            @Override
            public ScalaInstance create() {
                ClassLoader scalaClassLoader = getClassLoader(scalaClasspath);

                File libraryJar = findFile(ArtifactInfo.ScalaLibraryID, scalaClasspath);
                File compilerJar = findFile(ArtifactInfo.ScalaCompilerID, scalaClasspath);

                return new ScalaInstance(
                    getScalaVersion(scalaClassLoader),
                    scalaClassLoader,
                    getClassLoader(Arrays.asList(libraryJar)),
                    libraryJar,
                    compilerJar,
                    Iterables.toArray(scalaClasspath, File.class),
                    Option.empty()
                );
            }
        });

    }

    static ZincScalaCompiler getCompiler(final Iterable<File> scalaClasspath, final Iterable<File> zincClasspath) {
        return COMPILER_CACHE.get(Sets.newHashSet(Iterables.concat(scalaClasspath, zincClasspath)), new Factory<ZincScalaCompiler>() {
            @Nullable
            @Override
            public ZincScalaCompiler create() {
                ScalaInstance scalaInstance = getScalaInstance(scalaClasspath);
                File bridgeJar = findFile(ZincUtil.getDefaultBridgeModule(scalaInstance.version()).name(), scalaClasspath);
                ScalaCompiler scalaCompiler = ZincCompilerUtil.scalaCompiler(scalaInstance, bridgeJar, ClasspathOptionsUtil.auto());

                return new ZincScalaCompiler(scalaInstance, scalaCompiler, ANALYSIS_STORE_PROVIDER);
            }
        });
    }

    private static File findFile(String prefix, Iterable<File> files) {
        for (File f: files) {
            if (f.getName().startsWith(prefix)) {
                return f;
            }
        }
        throw new IllegalStateException(String.format("Cannot find any files starting with %s in %s", prefix, files));
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
