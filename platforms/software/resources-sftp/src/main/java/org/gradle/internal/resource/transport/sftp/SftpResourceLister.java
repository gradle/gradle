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

package org.gradle.internal.resource.transport.sftp;

import com.jcraft.jsch.ChannelSftp;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.transfer.ExternalResourceLister;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class SftpResourceLister implements ExternalResourceLister {
    private final SftpClientFactory sftpClientFactory;
    private final PasswordCredentials credentials;

    public SftpResourceLister(SftpClientFactory sftpClientFactory, PasswordCredentials credentials) {
        this.sftpClientFactory = sftpClientFactory;
        this.credentials = credentials;
    }

    @Override
    public List<String> list(ExternalResourceName directory) {
        LockableSftpClient client = sftpClientFactory.createSftpClient(directory.getUri(), credentials);

        try {
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = client.getSftpClient().ls(directory.getPath());
            List<String> list = new ArrayList<String>();
            for (ChannelSftp.LsEntry entry : entries) {
                list.add(entry.getFilename());
            }
            return list;
        } catch (com.jcraft.jsch.SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return null;
            }
            throw new ResourceException(directory.getUri(), String.format("Could not list children for resource '%s'.", directory.getUri()), e);
        } finally {
            sftpClientFactory.releaseSftpClient(client);
        }
    }
}
