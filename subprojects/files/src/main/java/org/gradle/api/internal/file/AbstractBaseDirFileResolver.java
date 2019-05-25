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

package org.gradle.api.internal.file;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractBaseDirFileResolver extends AbstractFileResolver {
    public AbstractBaseDirFileResolver(Factory<PatternSet> patternSetFactory) {
        super(patternSetFactory);
    }

    protected abstract File getBaseDir();

    @Override
    public String resolveAsRelativePath(Object path) {
        List<String> basePath = Arrays.asList(StringUtils.split(getBaseDir().getAbsolutePath(), "/" + File.separator));
        File targetFile = resolve(path);
        List<String> targetPath = new ArrayList<String>(Arrays.asList(StringUtils.split(targetFile.getAbsolutePath(),
                "/" + File.separator)));

        // Find and remove common prefix
        int maxDepth = Math.min(basePath.size(), targetPath.size());
        int prefixLen = 0;
        while (prefixLen < maxDepth && basePath.get(prefixLen).equals(targetPath.get(prefixLen))) {
            prefixLen++;
        }
        basePath = basePath.subList(prefixLen, basePath.size());
        targetPath = targetPath.subList(prefixLen, targetPath.size());

        for (int i = 0; i < basePath.size(); i++) {
            targetPath.add(0, "..");
        }
        if (targetPath.isEmpty()) {
            return ".";
        }
        return CollectionUtils.join(File.separator, targetPath);
    }

    @Override
    protected File doResolve(Object path) {
        if (!GUtil.isTrue(path)) {
            throw new IllegalArgumentException(String.format(
                "path may not be null or empty string. path='%s'", path));
        }

        File file = convertObjectToFile(path);

        if (file == null) {
            throw new IllegalArgumentException(String.format("Cannot convert path to File. path='%s'", path));
        }

        if (!file.isAbsolute()) {
            File baseDir = getBaseDir();
            if (!GUtil.isTrue(baseDir)) {
                throw new IllegalArgumentException(String.format("baseDir may not be null or empty string. basedir='%s'", baseDir));
            }

            file = new File(baseDir, file.getPath());
        }

        return file;
    }

    @Override
    public boolean canResolveRelativePath() {
        return true;
    }
}
