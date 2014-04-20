/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.externalresource.transport.sftp;

import org.apache.sshd.ClientSession;
import org.apache.sshd.client.sftp.DefaultSftpClient;
import org.apache.sshd.common.util.Buffer;

import java.io.IOException;

public class NonExistingFileHandlingSftpClient extends DefaultSftpClient implements LockableSftpClient {
    private final ClientSession clientSession;
    private boolean locked;

    public NonExistingFileHandlingSftpClient(ClientSession clientSession) throws IOException {
        super(clientSession);
        this.clientSession = clientSession;
    }

    private boolean noSuchFileResponse(Buffer buffer) {
        buffer.getInt(); //length
        int type = buffer.getByte();
        buffer.getInt(); //id
        return type == SSH_FXP_STATUS && buffer.getInt() == DefaultSftpClient.SSH_FX_NO_SUCH_FILE;
    }

    @Override
    protected Attributes checkAttributes(Buffer buffer) throws IOException {
        int originalBufferPosition = buffer.rpos();
        if (noSuchFileResponse(buffer)) {
            return null;
        } else {
            buffer.rpos(originalBufferPosition);
            return super.checkAttributes(buffer);
        }
    }

    @Override
    protected Handle checkHandle(Buffer buffer) throws IOException {
        int originalBufferPosition = buffer.rpos();
        if (noSuchFileResponse(buffer)) {
            return null;
        } else {
            buffer.rpos(originalBufferPosition);
            return super.checkHandle(buffer);
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        clientSession.close(false).awaitUninterruptibly();
    }

    public boolean isLocked() {
        return locked;
    }

    public void lock() {
        locked = true;
    }

    public void unlock() {
        locked = false;
    }
}
