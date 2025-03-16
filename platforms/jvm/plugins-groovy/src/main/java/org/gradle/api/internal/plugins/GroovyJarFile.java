/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.plugins;

import org.gradle.util.internal.VersionNumber;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.gradle.util.internal.GroovyDependencyUtil.groovyModuleDependency;

public class GroovyJarFile {
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("(groovy(?:-all)?)-(\\d.*?)(-indy)?.jar");

    private final File file;
    private final Matcher matcher;

    private GroovyJarFile(File file, Matcher matcher) {
        this.file = file;
        this.matcher = matcher;
    }

    public File getFile() {
        return file;
    }

    public String getBaseName() {
        return matcher.group(1);
    }

    public VersionNumber getVersion() {
        return VersionNumber.parse(matcher.group(2));
    }

    public boolean isGroovyAll() {
        return getBaseName().equals("groovy-all");
    }

    public boolean isIndy() {
        return matcher.group(3) != null;
    }

    public String getDependencyNotation() {
        return groovyModuleDependency(getBaseName(), getVersion(), isIndy() ? "indy" : null);
    }

    @Nullable
    public static GroovyJarFile parse(File file) {
        try {
            if (file.getName().contains("groovy")) {
                // Resolve a symlink file to the real location
                file = file.toPath().toRealPath().toFile();
            }
        } catch (IOException e) {
            // Let the code use the original File otherwise
        }
        Matcher matcher = FILE_NAME_PATTERN.matcher(file.getName());
        return matcher.matches() ? new GroovyJarFile(file, matcher) : null;
    }
}
