/*
 * Copyright 2007-2008 the original author or authors.
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
import org.gradle.api.AntBuilder

/**
 * @author Hans Dockter
 */
class TarFileSet extends ZipFileSet {
    String userName

    String group

    String uid

    String gid

    TarFileSet(File dir, FileResolver resolver) {
        super(dir, resolver)
    }

    TarFileSet(Map args, FileResolver resolver) {
        super(args, resolver)
    }

    protected doAddFileSet(Object builder, File dir, String nodeName) {
        Map args = [prefix: prefix, fullpath: fullPath, filemode: fileMode, dirmode: dirMode, username: userName,
            group: group, uid: uid, gid: gid]
        if (dir.isDirectory()) {
            args.dir = dir.absolutePath
        } else {
            args.src = dir.absolutePath
        }
        removeEmptyArgs(args)
        builder.tarfileset(args) {
            patternSet.addToAntBuilder(builder)
        }
    }
}
