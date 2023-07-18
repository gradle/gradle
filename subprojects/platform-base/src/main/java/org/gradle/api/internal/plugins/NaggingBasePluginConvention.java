/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.plugins;

import org.gradle.api.NonNullApi;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.internal.deprecation.DeprecationLogger;

@SuppressWarnings("deprecation")
@NonNullApi
public class NaggingBasePluginConvention extends org.gradle.api.plugins.BasePluginConvention {

    private final org.gradle.api.plugins.BasePluginConvention delegate;

    public NaggingBasePluginConvention(org.gradle.api.plugins.BasePluginConvention delegate) {
        this.delegate = delegate;
    }

    @Override
    public DirectoryProperty getDistsDirectory() {
        logDeprecation();
        return delegate.getDistsDirectory();
    }

    @Override
    public DirectoryProperty getLibsDirectory() {
        logDeprecation();
        return delegate.getLibsDirectory();
    }

    @Override
    public String getDistsDirName() {
        logDeprecation();
        return delegate.getDistsDirName();
    }

    @Override
    public void setDistsDirName(String distsDirName) {
        logDeprecation();
        delegate.setDistsDirName(distsDirName);
    }

    @Override
    public String getLibsDirName() {
        logDeprecation();
        return delegate.getLibsDirName();
    }

    @Override
    public void setLibsDirName(String libsDirName) {
        logDeprecation();
        delegate.setLibsDirName(libsDirName);
    }

    @Override
    public String getArchivesBaseName() {
        logDeprecation();
        return delegate.getArchivesBaseName();
    }

    @Override
    public void setArchivesBaseName(String archivesBaseName) {
        logDeprecation();
        delegate.setArchivesBaseName(archivesBaseName);
    }

    private static void logDeprecation() {
        DeprecationLogger.deprecateType(org.gradle.api.plugins.BasePluginConvention.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "base_convention_deprecation")
            .nagUser();
    }
}
