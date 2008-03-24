/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.util

/**
 * @author Hans Dockter
 */
class ZipFileSet extends FileSet {
    String prefix
    String fullPath
    String fileMode
    String dirMode

    ZipFileSet() {
        super()
    }

    ZipFileSet(File dir) {
        super(dir)
    }

    ZipFileSet(Object contextObject) {
        super(contextObject)
    }

    ZipFileSet(File dir, Object contextObject) {
        super(dir, contextObject)
    }

    ZipFileSet(Map args) {
        super(args)
    }

    def addToAntBuilder(node, String childNodeName) {
        Map args = [dir: dir.absolutePath, prefix: prefix, fullpath: fullPath, filemode: fileMode, dirmode: dirMode]
        removeEmptyArgs(args)
        node.zipfileset(args) {
            addIncludesAndExcludesToBuilder(delegate)
        }
    }

    def removeEmptyArgs(Map args) {
        List emptyKeys = args.keySet().findAll { !args[it] }
        emptyKeys.each { args.remove(it) }
    }

}