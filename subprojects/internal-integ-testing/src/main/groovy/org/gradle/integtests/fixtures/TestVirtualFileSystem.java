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

package org.gradle.integtests.fixtures;

import com.sshtools.daemon.vfs.VirtualFileSystem;

import java.io.IOException;
import java.util.List;
import java.util.Vector;

public class TestVirtualFileSystem extends VirtualFileSystem {

    List<String> fileRequests = new Vector<String>();

    public TestVirtualFileSystem() throws IOException {
        super();
    }

    public byte[] openFile(java.lang.String s, com.sshtools.j2ssh.io.UnsignedInteger32 unsignedInteger32, com.sshtools.j2ssh.sftp.FileAttributes fileAttributes) throws com.sshtools.daemon.platform.PermissionDeniedException, java.io.FileNotFoundException, java.io.IOException {
        logRequest(s);
        return super.openFile(s, unsignedInteger32, fileAttributes);
    }

    private void logRequest(String s) {
        String normalizedPath = s.replaceAll("/home/simple/", "");
        fileRequests.add(normalizedPath);
    }
}
