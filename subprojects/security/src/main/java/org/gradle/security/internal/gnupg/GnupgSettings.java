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

    public void setExecutable(String executable) {
        this.executable = executable;
    }

    public String getExecutable() {
        if (executable == null) {
            return defaultExecutable();
        }
        return executable;
    }

    private String defaultExecutable() {
        String defaultExecutable = useLegacyGpg ? "gpg" : "gpg2";
        if (OperatingSystem.current().isWindows()) {
            defaultExecutable += ".exe";
        }
        return defaultExecutable;
    }

    public void setUseLegacyGpg(boolean useLegacyGpg) {
        this.useLegacyGpg = useLegacyGpg;
    }

    public boolean getUseLegacyGpg() {
        return useLegacyGpg;
    }

    public void setHomeDir(File homeDir) {
        this.homeDir = homeDir;
    }

    public File getHomeDir() {
        return homeDir;
    }

    public void setOptionsFile(File optionsFile) {
        this.optionsFile = optionsFile;
    }

    public File getOptionsFile() {
        return optionsFile;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public String getPassphrase() {
        return passphrase;
    }
}
