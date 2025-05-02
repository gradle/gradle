/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.util.internal.GUtil;

import java.io.File;
import java.nio.file.Path;

public class BaseDirFileResolver extends AbstractFileResolver {
    private final File baseDir;

    /**
     * Do not create instances of this type. Use {@link FileLookup} instead.
     */
    public BaseDirFileResolver(File baseDir) {
        if (!GUtil.isTrue(baseDir)) {
            throw new IllegalArgumentException(String.format("baseDir may not be null or empty string. basedir='%s'", baseDir));
        }
        if (!baseDir.isAbsolute()) {
            throw new IllegalArgumentException(String.format("base dir '%s' is not an absolute file.", baseDir));
        }
        this.baseDir = baseDir;
    }

    @Override
    public String resolveAsRelativePath(Object path) {
        Path baseDir = this.baseDir.toPath();
        Path file = resolve(path).toPath();
        if (file.equals(baseDir)) {
            return ".";
        } else {
            return baseDir.relativize(file).toString();
        }
    }

    @Override
    public String resolveForDisplay(Object path) {
        Path file = resolve(path).toPath();
        Path baseDir = this.baseDir.toPath();
        if (file.equals(baseDir)) {
            return ".";
        }
        Path parent = baseDir.getParent();
        if (parent == null) {
            parent = baseDir;
        }
        if (file.startsWith(parent)) {
            return baseDir.relativize(file).toString();
        } else {
            return file.toString();
        }
    }

    @Override
    protected File doResolve(File file) {
        if (!file.isAbsolute()) {
            return new File(baseDir, file.getPath());
        }
        return file;
    }

    @Override
    public boolean canResolveRelativePath() {
        return true;
    }

    public File getBaseDir() {
        return baseDir;
    }
}
