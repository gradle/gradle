/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.test.fixtures.server.s3

import groovy.io.FileType
import org.apache.commons.io.IOUtils

import javax.servlet.http.HttpServletRequest

trait LocalStorage {
    private File baseDir;

    abstract File getStorageDirectory()

    private File baseDir() {
        baseDir = baseDir ?: getStorageDirectory()
        baseDir
    }

    public List<File> getContents() {
        def list = []
        def dir = baseDir()
        dir.eachFileRecurse(FileType.FILES) { file ->
            list << file
        }
        return list
    }

    File writeFileFromRequest(HttpServletRequest request) {
        File f = new File("${baseDir().getAbsolutePath()}${request.pathInfo}")
        def parent = f.getParentFile()
        if (!parent.exists()) {
            parent.mkdirs()
        }
        f.createNewFile()
        def inStream = request.getInputStream()
        def out = f.newOutputStream()
        try {
            IOUtils.copy(inStream, out)
            println "   Wrote file ${f.getAbsolutePath()} to disk"
        } catch (IOException e) {
            throw e
        } finally {
            out.close()
        }
        f
    }

    File getFile(HttpServletRequest request) {
        def file = new File("${baseDir().getAbsolutePath()}${request.pathInfo}")
        file
    }
}