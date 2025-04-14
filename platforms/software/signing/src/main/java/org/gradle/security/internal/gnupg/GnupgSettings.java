/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.security.internal.gnupg;

import org.gradle.internal.os.OperatingSystem;

import java.io.File;

/**
 * Stores the settings for invoking gnupg.
 *
 * @since 4.5
 */
public class GnupgSettings {

    private String executable;
    private File homeDir;
    private File optionsFile;
    private String keyName;
    private String passphrase;
    private boolean useLegacyGpg;

    public String getExecutable() {
        if (executable == null) {
            return defaultExecutable();
        }
        return executable;
    }

    public void setExecutable(String executable) {
        this.executable = executable;
    }

    private String defaultExecutable() {
        if (OperatingSystem.current().isWindows()) {
            return "gpg.exe";
        } else {
            return "gpg";
        }
    }

    public boolean getUseLegacyGpg() {
        return useLegacyGpg;
    }

    public void setUseLegacyGpg(boolean useLegacyGpg) {
        this.useLegacyGpg = useLegacyGpg;
    }

    public File getHomeDir() {
        return homeDir;
    }

    public void setHomeDir(File homeDir) {
        this.homeDir = homeDir;
    }

    public File getOptionsFile() {
        return optionsFile;
    }

    public void setOptionsFile(File optionsFile) {
        this.optionsFile = optionsFile;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }
}
