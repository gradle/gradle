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

import com.sshtools.daemon.configuration.PlatformConfiguration;
import com.sshtools.daemon.platform.NativeAuthenticationProvider;
import com.sshtools.daemon.platform.PasswordChangeException;
import com.sshtools.j2ssh.configuration.ConfigurationLoader;

import java.io.File;
import java.io.IOException;

public class SshDummyAuthenticationProvider extends NativeAuthenticationProvider {

    @Override
    public String getHomeDirectory(String username) throws IOException {
        PlatformConfiguration platform = (PlatformConfiguration) ConfigurationLoader.getConfiguration(PlatformConfiguration.class);
        String base = platform.getVFSRoot().getPath();
        File homeDir = new File(base + File.separator + "home" + File.separator + username);
        return homeDir.getAbsolutePath().replace('\\', '/');
    }

    @Override
    public boolean logonUser(String user, String password) throws PasswordChangeException, IOException {
        return user != null && user.equals(password);
    }

    @Override
    public boolean logonUser(String s) throws IOException {
        return s != null;
    }

    @Override
    public void logoffUser() throws IOException {

    }

    @Override
    public boolean changePassword(String s, String s1, String s2) {
        return false;
    }
}
