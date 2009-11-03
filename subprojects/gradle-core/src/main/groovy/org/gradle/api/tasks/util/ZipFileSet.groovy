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

import org.gradle.api.internal.file.FileResolver

/**
 * @author Hans Dockter
 */
class ZipFileSet extends FileSet {
    Object prefix
    Object fullPath
    Object fileMode
    Object dirMode

    ZipFileSet(File dir, FileResolver resolver) {
        super(dir, resolver)
    }

    ZipFileSet(Map<String, ?> args, FileResolver resolver) {
        super(args, resolver)
    }

    protected doAddFileSet(Object builder, File dir, String nodeName) {
        Map args = [:]
        addToArgsIfNotNull(args, 'prefix', prefix)
        addToArgsIfNotNull(args, 'fullpath', fullPath)
        addToArgsIfNotNull(args, 'filemode', fileMode)
        addToArgsIfNotNull(args, 'dirmode', dirMode)
        if (dir.isDirectory()) {
            args.dir = dir.absolutePath
        } else {
            args.src = dir.absolutePath
        }
        removeEmptyArgs(args)
        builder.zipfileset(args) {
            patternSet.addToAntBuilder(builder)
        }
    }

    def removeEmptyArgs(Map args) {
        Set emptyKeys = args.keySet().findAll { !args[it] }
        emptyKeys.each { args.remove(it) }
    }


    void addToArgsIfNotNull(Map args, String key, Object value) {
        if (value) args[key] = value.toString()
    }
}