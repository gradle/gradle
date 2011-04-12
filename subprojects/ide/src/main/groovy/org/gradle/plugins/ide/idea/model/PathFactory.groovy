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
package org.gradle.plugins.ide.idea.model

import org.gradle.api.invocation.Gradle
import org.gradle.api.artifacts.ResolverContainer

class PathFactory {
    private final List<Map> variables = []
    private final Map<String, File> varsByName = [:]

    // TODO: Should the cache dir be enforced as a CACHE_DIR variable
    private File cacheDir

    PathFactory addPathVariable(String name, File dir) {
        variables << [name: "\$${name}\$", prefix: dir.absolutePath + File.separator, dir: dir]
        varsByName[name] = dir
        return this
    }

    PathFactory setCacheDir(Gradle gradle) {
        // TODO: Actually fetch the value from the Resolver service
        cacheDir = new File(gradle.gradleUserHomeDir, ResolverContainer.DEFAULT_CACHE_DIR_NAME)
        return this
    }

    private File canonicalFile(File file) {
        // When file in cache, use absolute path
        String absPath = file.absolutePath
        if (cacheDir != null && absPath.startsWith(cacheDir.absolutePath))
            return file.absoluteFile
        return file.canonicalFile
    }

    /**
     * Creates a path for the given file.
     */
    Path path(File file) {
        createFile(canonicalFile(file))
    }

    private Path createFile(File file) {
        Map match = null
        for (variable in variables) {
            if (file.absolutePath == variable.dir.absolutePath) {
                match = variable
                break
            }
            if (file.absolutePath.startsWith(variable.prefix)) {
                if (!match || variable.prefix.startsWith(match.prefix)) {
                    match = variable
                }
            }
        }

        if (match) {
            return new Path(match.dir, match.name, file)
        }

        return new Path(file)
    }

    /**
     * Creates a path relative to the given path variable.
     */
    Path relativePath(String pathVar, File file) {
        return new Path(varsByName[pathVar], "\$${pathVar}\$", file)
    }

    /**
     * Creates a path for the given URL.
     */
    Path path(String url) {
        String expandedUrl = url
        for (variable in variables) {
            expandedUrl = expandedUrl.replace(variable.name, variable.prefix)
        }
        if (expandedUrl.toLowerCase().startsWith('file://')) {
            expandedUrl = toUrl('file', canonicalFile(new File(expandedUrl.substring(7))))
        } else if (expandedUrl.toLowerCase().startsWith('jar://')) {
            def parts = expandedUrl.substring(6).split('!')
            if (parts.length == 2) {
                expandedUrl = toUrl('jar', canonicalFile(new File(parts[0]))) + '!' + parts[1]
            }
        }
        return new Path(url, expandedUrl)
    }

    def toUrl(String scheme, File file) {
        return scheme + '://' + file.absolutePath.replace(File.separator, '/')
    }
}
