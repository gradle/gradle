/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.internal.tooling.eclipse;

import java.io.File;
import java.util.List;

public class DefaultEclipseSourceDirectory extends DefaultEclipseClasspathEntry {
    private final String path;
    private final File directory;
    private final List<String> excludes;
    private final List<String> includes;
    private final String output;

    public DefaultEclipseSourceDirectory(String path, File directory, List<String> excludes, List<String> includes, String output, List<DefaultClasspathAttribute> classpathAttributes, List<DefaultAccessRule> accessRules) {
        super(classpathAttributes, accessRules);
        this.path = path;
        this.directory = directory;
        this.excludes = excludes;
        this.includes = includes;
        this.output = output;
    }

    @Override
    public String toString() {
        return "source directory '" + path + "'";
    }

    public File getDirectory() {
        return directory;
    }

    public String getPath() {
        return path;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public String getOutput() {
        return output;
    }
}
