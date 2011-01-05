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

package org.gradle.api.plugins.jetty;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Patterns for scanning for file changes.
 */
public class ScanTargetPattern {
    private File directory;
    private List includes = Collections.EMPTY_LIST;
    private List excludes = Collections.EMPTY_LIST;

    public File getDirectory() {
        return directory;
    }

    public void setDirectory(File directory) {
        this.directory = directory;
    }

    public void setIncludes(List includes) {
        this.includes = includes;
    }

    public void setExcludes(List excludes) {
        this.excludes = excludes;
    }

    public List getIncludes() {
        return includes;
    }

    public List getExcludes() {
        return excludes;
    }
}
