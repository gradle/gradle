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
package org.gradle.api.internal.file.ant;

import org.apache.tools.ant.types.selectors.FileSelector;

import java.io.File;

public class BaseDirSelector implements FileSelector {
    private File baseDir;

    public void setBaseDir(File baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public String toString() {
        return String.format("{basedir: %s}", baseDir);
    }

    @Override
    public boolean isSelected(File basedir, String filename, File file) {
        return basedir.equals(this.baseDir);
    }
}
