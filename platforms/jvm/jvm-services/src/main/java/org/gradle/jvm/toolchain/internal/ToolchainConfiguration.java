/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * Service representing the build level configuration of the JVM toolchain subsystem.
 * <p>
 * Used also in the launcher for the daemon toolchain
 */
@ServiceScope({ Scope.Build.class, Scope.Global.class })
public interface ToolchainConfiguration {
    String AUTO_DETECT = "org.gradle.java.installations.auto-detect";

    Collection<String> getJavaInstallationsFromEnvironment();
    void setJavaInstallationsFromEnvironment(Collection<String> installations);

    Collection<String> getInstallationsFromPaths();
    void setInstallationsFromPaths(Collection<String> installations);

    boolean isAutoDetectEnabled();
    void setAutoDetectEnabled(boolean enabled);

    boolean isDownloadEnabled();
    void setDownloadEnabled(boolean enabled);

    File getAsdfDataDirectory();

    File getIntelliJdkDirectory();

    void setIntelliJdkDirectory(File intellijInstallationDirectory);

    @Nullable File getJabbaHomeDirectory();

    File getSdkmanCandidatesDirectory();
}
