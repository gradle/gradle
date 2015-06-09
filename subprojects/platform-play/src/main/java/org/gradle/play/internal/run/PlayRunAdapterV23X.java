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

import org.gradle.api.Transformer;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.List;

public class PlayRunAdapterV23X extends DefaultVersionedPlayRunAdapter {
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
        Class<?> assetsClassLoaderClass = null;
        try {
            assetsClassLoaderClass = classLoader.loadClass("play.runsupport.AssetsClassLoader");
        } catch (ClassNotFoundException ignore) {
            // fallback to default implementation, play.runsupport.AssetsClassLoader requires Play >= 2.3.7
            return super.createAssetsClassLoader(assetsJar, assetsDirs, classLoader);
        }

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
}
