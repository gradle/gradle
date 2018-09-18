/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.plugins.announce.internal;

import org.gradle.internal.installation.GradleInstallation;

import java.io.File;

public class DefaultIconProvider implements IconProvider {
    private final GradleInstallation gradleInstallation;

    public DefaultIconProvider(GradleInstallation gradleInstallation) {
        this.gradleInstallation = gradleInstallation;
    }

    @Override
    public File getIcon(final int width, final int height) {
        if (gradleInstallation == null) {
            return null;
        }
        File candidate = new File(gradleInstallation.getGradleHome(), "media/gradle-icon-" + width + "x" + height + ".png");
        return candidate.isFile() ? candidate : null;
    }
}
