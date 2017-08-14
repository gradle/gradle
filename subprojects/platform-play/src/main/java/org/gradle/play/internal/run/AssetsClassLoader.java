/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.specs.Spec;
import org.gradle.internal.UncheckedException;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

class AssetsClassLoader extends ClassLoader {
    private final List<AssetDir> assetDirs;

    AssetsClassLoader(ClassLoader parent, List<AssetDir> assetDirs) {
        super(parent);
        this.assetDirs = assetDirs;
    }

    @Override
    protected URL findResource(String name) {
        AssetDir assetDir = findResourceInAssetDir(name);
        if (assetDir != null) {
            return assetDir.toURL(name);
        }
        return null;
    }

    private AssetDir findResourceInAssetDir(final String name) {
        return CollectionUtils.findFirst(assetDirs, new Spec<AssetDir>() {
            @Override
            public boolean isSatisfiedBy(AssetDir assetDir) {
                return assetDir.exists(name);
            }
        });
    }

    public static class AssetDir {
        private final String prefix;
        private final File dir;

        public AssetDir(String prefix, File dir) {
            this.prefix = prefix;
            this.dir = dir;
        }

        boolean exists(String name) {
            return name.startsWith(prefix) && resolve(name).isFile();
        }

        File resolve(String name) {
            return new File(dir, name.substring(prefix.length()));
        }

        URL toURL(String name) {
            try {
                return resolve(name).toURI().toURL();
            } catch (MalformedURLException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
