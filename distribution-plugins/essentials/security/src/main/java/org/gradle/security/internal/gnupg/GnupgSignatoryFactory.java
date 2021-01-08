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

import org.gradle.api.Project;

/**
 * Creates {@link GnupgSignatory} instances.
 * @since 4.5
 */
public class GnupgSignatoryFactory {

    public GnupgSignatory createSignatory(Project project) {
        return createSignatory(project, "default");
    }

    public GnupgSignatory createSignatory(Project project, String name) {
        return createSignatory(project, name, "");
    }

    public GnupgSignatory createSignatory(Project project, String name, String propertyPrefix) {
        return new GnupgSignatory(project, name, readSettings(project, propertyPrefix));
    }

    private GnupgSettings readSettings(Project project, String propertyPrefix) {
        Object executable = project.findProperty(buildQualifiedProperty(propertyPrefix, "executable"));
        Object useLegacyGpg = project.findProperty(buildQualifiedProperty(propertyPrefix, "useLegacyGpg"));
        Object homeDir = project.findProperty(buildQualifiedProperty(propertyPrefix, "homeDir"));
        Object optionsFile = project.findProperty(buildQualifiedProperty(propertyPrefix, "optionsFile"));
        Object keyName = project.findProperty(buildQualifiedProperty(propertyPrefix, "keyName"));
        Object passphrase = project.findProperty(buildQualifiedProperty(propertyPrefix, "passphrase"));
        GnupgSettings settings = new GnupgSettings();
        if (executable != null) {
            settings.setExecutable(executable.toString());
        }
        if (useLegacyGpg != null) {
            settings.setUseLegacyGpg(Boolean.parseBoolean(useLegacyGpg.toString()));
        }
        if (homeDir != null) {
            settings.setHomeDir(project.file(homeDir.toString()));
        }
        if (optionsFile != null) {
            settings.setOptionsFile(project.file(optionsFile.toString()));
        }
        if (keyName != null) {
            settings.setKeyName(keyName.toString());
        }
        if (passphrase != null) {
            settings.setPassphrase(passphrase.toString());
        }
        return settings;
    }

    private String buildQualifiedProperty(String propertyPrefix, String property) {
        return "signing.gnupg." + propertyPrefix + (propertyPrefix.isEmpty() ? "" : ".") + property;
    }

}
