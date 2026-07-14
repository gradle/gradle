/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.internal.artifacts.mvnsettings;

import org.gradle.api.Describable;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.internal.hash.Hashing;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * A checksum of the local Maven settings.xml and settings-security.xml files, used as a
 * configuration cache input so that changes to the Maven settings invalidate cached
 * configuration when Maven mirror support is enabled.
 *
 * <p>Value sources cannot inject build-scoped services, so this locates the settings
 * files itself. It only hashes the file contents and does not parse them, keeping the
 * cache fingerprint check cheap.
 */
public abstract class MavenSettingsChecksumValueSource implements ValueSource<String, ValueSourceParameters.None>, Describable {

    @Override
    public String getDisplayName() {
        return "Maven settings.xml content";
    }

    @Override
    public @Nullable String obtain() {
        MavenFileLocations locations = new DefaultMavenFileLocations();
        String userChecksum = checksumOf(locations.getUserSettingsFile());
        String globalChecksum = checksumOf(locations.getGlobalSettingsFile());
        String securityChecksum = checksumOf(securitySettingsFile());
        if (userChecksum == null && globalChecksum == null && securityChecksum == null) {
            return null;
        }
        return userChecksum + "/" + globalChecksum + "/" + securityChecksum;
    }

    private static File securitySettingsFile() {
        String location = System.getProperty("settings.security");
        if (location != null) {
            return new File(location);
        }
        return DefaultMavenMirrorResolver.defaultSecuritySettingsFile();
    }

    private static @Nullable String checksumOf(@Nullable File settingsFile) {
        if (settingsFile == null || !settingsFile.isFile()) {
            return null;
        }
        try {
            return Hashing.hashFile(settingsFile).toString();
        } catch (IOException e) {
            // An unreadable file is fingerprinted by its path, so it still invalidates once readable again
            return "unreadable:" + settingsFile.getAbsolutePath();
        }
    }
}
