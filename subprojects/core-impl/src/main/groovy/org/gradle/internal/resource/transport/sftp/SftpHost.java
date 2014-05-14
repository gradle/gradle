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

package org.gradle.internal.resource.transport.sftp;

import org.gradle.internal.resource.PasswordCredentials;

import java.net.URI;

public class SftpHost {
    private final String hostname;
    private final int port;
    private final String username;
    private final String password;

    public SftpHost(URI uri, PasswordCredentials credentials) {
        hostname = uri.getHost();
        port = uri.getPort();
        username = credentials.getUsername();
        password = credentials.getPassword();
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SftpHost sftpHost = (SftpHost) o;

        if (port != sftpHost.port) {
            return false;
        }
        if (!hostname.equals(sftpHost.hostname)) {
            return false;
        }
        if (password != null ? !password.equals(sftpHost.password) : sftpHost.password != null) {
            return false;
        }
        if (username != null ? !username.equals(sftpHost.username) : sftpHost.username != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = hostname.hashCode();
        result = 31 * result + port;
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s:%d (Username: %s)", hostname, port, username);
    }
}