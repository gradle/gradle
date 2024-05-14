/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.Describable;

import java.io.File;

public class InstallationLocation implements Describable {
    public static InstallationLocation userDefined(File location, String source) {
        return new InstallationLocation(location, source, false, false);
    }

    public static InstallationLocation autoDetected(File location, String source) {
        return new InstallationLocation(location, source, true, false);
    }

    public static InstallationLocation autoProvisioned(File location, String source) {
        return new InstallationLocation(location, source, true, true);
    }

    private final File location;

    private final String source;

    private final boolean autoDetected;

    private final boolean autoProvisioned;

    private InstallationLocation(File location, String source, boolean autoDetected, boolean autoProvisioned) {
        this.location = location;
        this.source = source;
        this.autoDetected = autoDetected;
        this.autoProvisioned = autoProvisioned;
    }

    public File getLocation() {
        return location;
    }

    @Override
    public String getDisplayName() {
        return "'" + location.getAbsolutePath() + "' (" + source + ")" + (autoDetected? " auto-detected" : "") + (autoProvisioned? " auto-provisioned" : "");
    }

    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    /**
     * Flag for if this location was auto-detected, i.e. not explicitly defined by the user.
     *
     * This is used to lower the severity of issues related to this location.
     */
    public boolean isAutoDetected() {
        return autoDetected;
    }

    public boolean isAutoProvisioned() {
        return autoProvisioned;
    }

    public InstallationLocation withLocation(File location) {
        return new InstallationLocation(location, source, autoDetected, autoProvisioned);
    }
}
