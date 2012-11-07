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

import org.apache.sshd.common.Session
import org.apache.sshd.server.FileSystemView
import org.apache.sshd.server.filesystem.NativeFileSystemFactory

class TestNativeFileSystemFactory extends NativeFileSystemFactory {

    String rootPath

    List<FileRequestLogger> logger

    public TestNativeFileSystemFactory(String rootPath, FileRequestLogger... logger) {
        this.rootPath = rootPath
        this.logger = Arrays.asList(logger)
    }

    /**
     * Create the appropriate user file system view.
     */
    public FileSystemView createFileSystemView(Session session) {
        String userName = session.getUsername();
        FileSystemView fsView = new TestNativeFileSystemView(rootPath, userName, logger, caseInsensitive);
        return fsView;
    }
}
