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
package org.gradle.plugins.idea.model

class PathFactory {
    private final List<Map> variables = []

    void addPathVariable(String name, File dir) {
        variables << [name: "\$${name}\$", prefix: dir.absolutePath + File.separator, dir: dir]
    }

    Path path(File file) {
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

    Path path(String url) {
        return new Path(url)
    }
}
