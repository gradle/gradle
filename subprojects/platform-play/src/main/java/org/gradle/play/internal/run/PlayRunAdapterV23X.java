/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.run;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;
import org.gradle.util.CollectionUtils;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.List;

public class PlayRunAdapterV23X extends DefaultVersionedPlayRunAdapter {
    protected static final String RUN_SUPPORT_PLAY_MODULE = "run-support";
    private static final VersionNumber MINIMUM_PLAY_VERSION_WITH_RUN_SUPPORT = VersionNumber.parse("2.3.7");

    @Override
    protected Class<?> getBuildLinkClass(ClassLoader classLoader) throws ClassNotFoundException {
        return classLoader.loadClass("play.core.BuildLink");
    }

    @Override
    protected Class<?> getBuildDocHandlerClass(ClassLoader classLoader) throws ClassNotFoundException {
        return classLoader.loadClass("play.core.BuildDocHandler");
    }

    @Override
    protected Class<?> getDocHandlerFactoryClass(ClassLoader docsClassLoader) throws ClassNotFoundException {
        return docsClassLoader.loadClass("play.docs.BuildDocHandlerFactory");
    }

    @Override
    protected ClassLoader createAssetsClassLoader(File assetsJar, Iterable<File> assetsDirs, ClassLoader classLoader) {
        Class<?> assetsClassLoaderClass;

        assetsClassLoaderClass = loadClass(classLoader, "play.runsupport.AssetsClassLoader");

        final Class<?> tuple2Class = loadClass(classLoader, "scala.Tuple2");

        List<?> tuples = CollectionUtils.collect(assetsDirs, new Transformer<Object, File>() {
            @Override
            public Object transform(File file) {
                return DirectInstantiator.instantiate(tuple2Class, "public", file);
            }
        });

        ScalaMethod listToScalaSeqMethod = ScalaReflectionUtil.scalaMethod(classLoader, "scala.collection.convert.WrapAsScala", "asScalaBuffer", List.class);
        Object scalaTuples = listToScalaSeqMethod.invoke(tuples);

        return Cast.uncheckedCast(DirectInstantiator.instantiate(assetsClassLoaderClass, classLoader, scalaTuples));
    }

    private Class<?> loadClass(ClassLoader classLoader, String className) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public Iterable<Dependency> getRunsupportClasspathDependencies(String playVersion, String scalaCompatibilityVersion) {
        ImmutableList.Builder<Dependency> listBuilder = ImmutableList.builder();

        String runsupportPlayVersion = playVersion;
        boolean transitive = true;
        // use run-support from 2.3.7 for versions before Play 2.3.7
        if (VersionNumber.parse(playVersion).compareTo(MINIMUM_PLAY_VERSION_WITH_RUN_SUPPORT) < 0) {
            runsupportPlayVersion = "2.3.7";
            transitive = false;
        }
        DefaultExternalModuleDependency runSupportDependency = new DefaultExternalModuleDependency("com.typesafe.play", RUN_SUPPORT_PLAY_MODULE + "_" + scalaCompatibilityVersion, runsupportPlayVersion);
        runSupportDependency.setTransitive(transitive);
        listBuilder.add(runSupportDependency);

        String name = scalaCompatibilityVersion.equals("2.10") ? "io" : ("io_" + scalaCompatibilityVersion);
        DefaultExternalModuleDependency dependency = new DefaultExternalModuleDependency("org.scala-sbt", name, getIOSupportDependencyVersion(), "runtime");
        dependency.setTransitive(false);
        listBuilder.add(dependency);

        return listBuilder.build();
    }

    protected String getIOSupportDependencyVersion() {
        return "0.13.6";
    }
}
