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

package org.gradle.configuration

import org.gradle.StartParameter
import org.gradle.groovy.scripts.ScriptSource

/**
 * @author Hans Dockter
 */
class ImportsReader {
    File defaultImportsFile

    ImportsReader() {

    }

    ImportsReader(File defaultImportsFile) {
        this.defaultImportsFile = defaultImportsFile
    }

    String getImports(File rootDir) {
        File projectImportsFiles = rootDir ? new File(rootDir, StartParameter.IMPORTS_FILE_NAME) : null
        String importsText = (defaultImportsFile ? defaultImportsFile.text : '') +
        (projectImportsFiles && projectImportsFiles.isFile() ? projectImportsFiles.text : '')
        importsText
    }

    ScriptSource withImports(ScriptSource source, File rootDir) {
        new ImportsScriptSource(source, this, rootDir)
    }
}
