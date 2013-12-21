/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.publication.maven.internal.ant;

import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenFileLocations;
import org.gradle.api.internal.artifacts.mvnsettings.MavenFileLocations;

import java.io.File;

public class MaybeUserMavenSettingsSupplier implements MavenSettingsSupplier {

    MavenSettingsSupplier emptySettingsSupplier = new EmptyMavenSettingsSupplier();
    MavenFileLocations mavenFileLocations = new DefaultMavenFileLocations();

    public void supply(InstallDeployTaskSupport installDeployTaskSupport) {
        File userSettings = mavenFileLocations.getUserSettingsFile();
        if (userSettings.exists()) {
            installDeployTaskSupport.setSettingsFile(userSettings);
            return;
        }

        emptySettingsSupplier.supply(installDeployTaskSupport);
    }

    public void done() {
        emptySettingsSupplier.done();
    }

}
