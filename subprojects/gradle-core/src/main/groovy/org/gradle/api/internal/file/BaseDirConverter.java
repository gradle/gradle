/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.util.GUtil;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class BaseDirConverter extends AbstractFileResolver {
    private final File baseDir;

    public BaseDirConverter(File baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    protected File doResolve(Object path) {
        if (!GUtil.isTrue(path) || !GUtil.isTrue(baseDir)) {
            throw new IllegalArgumentException(String.format(
                    "Neither path nor baseDir may be null or empty string. path='%s' basedir='%s'", path, baseDir));
        }

        File file = convertObjectToFile(path);
        if (!file.isAbsolute()) {
            file = new File(baseDir, path.toString());
        }

        return file;
    }
}
