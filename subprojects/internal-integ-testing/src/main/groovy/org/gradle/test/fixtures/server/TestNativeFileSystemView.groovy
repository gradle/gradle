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

package org.gradle.test.fixtures.server

import org.apache.sshd.server.FileSystemView
import org.apache.sshd.server.SshFile
import org.apache.sshd.server.filesystem.NativeFileSystemFactory
import org.apache.sshd.server.filesystem.NativeSshFile

/**
 * This is a patched version of {@link org.apache.sshd.server.filesystem.NativeFileSystemView}
 * to allow the usage of a custom currentDir (rootpath) without manipulating System properties.
 * */
class TestNativeFileSystemView implements FileSystemView {
    // the first and the last character will always be '/'
    // It is always with respect to the root directory.
    private String currDir;

    private String userName;

    private boolean caseInsensitive = false;

    List<FileRequestLogger> logger

    /**
     * Constructor - internal do not use directly, use {@link NativeFileSystemFactory} instead
     */
    public TestNativeFileSystemView(String rootpath, String userName, List<FileRequestLogger> requestLoggerList, boolean caseInsensitive) {
        if (!rootpath) {
            throw new IllegalArgumentException("rootPath must be set");
        }

        if (!userName) {
            throw new IllegalArgumentException("user can not be null");
        }

        this.logger = requestLoggerList;
        this.caseInsensitive = caseInsensitive;

        currDir = rootpath;
        this.userName = userName;
    }

    /**
     * Get file object.
     */
    public SshFile getFile(String file) {
        return getFile(currDir, file);
    }

    public SshFile getFile(SshFile baseDir, String file) {
        return getFile(baseDir.getAbsolutePath(), file);
    }

    protected SshFile getFile(String dir, String file) {
        // get actual file object

        String physicalName = NativeSshFile.getPhysicalName("/", dir, file, caseInsensitive);
        File fileObj = new File(physicalName);
        logFileRequest(dir, fileObj.absolutePath);
        // strip the root directory and return
        String userFileName = physicalName.substring("/".length() - 1);
        return new NativeSshFile(userFileName, fileObj, userName);
    }

    void logFileRequest(String dir, String file) {
        //log xml and jar requests only
        if (file.endsWith("xml") || file.endsWith(".jar")) {
            String normalizedPath = (file - dir).replaceAll("\\\\", '/') - "/"
            logger.each {
                it.logRequest(normalizedPath)
            }
        }
    }
}

