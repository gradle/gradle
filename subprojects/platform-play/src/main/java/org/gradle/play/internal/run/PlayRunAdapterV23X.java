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
        List<AssetsClassLoader.AssetDir> assetDirs = CollectionUtils.collect(assetsDirs, new Transformer<AssetsClassLoader.AssetDir, File>() {
            @Override
            public AssetsClassLoader.AssetDir transform(File file) {
                // TODO: This prefix shouldn't be hardcoded
                return new AssetsClassLoader.AssetDir("public", file);
            }
        });
        return new AssetsClassLoader(classLoader, assetDirs);
    }
}
