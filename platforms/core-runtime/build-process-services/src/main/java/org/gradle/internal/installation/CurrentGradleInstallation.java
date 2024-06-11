/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.installation;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;

/**
 * Provides access to the current Gradle installation associated with the runtime.
 */
@ServiceScope({Scope.Global.class, Scope.Build.class})
public class CurrentGradleInstallation {

    private static CurrentGradleInstallation instance;

    private final GradleInstallation gradleInstallation;

    public CurrentGradleInstallation(@Nullable GradleInstallation gradleInstallation) {
        this.gradleInstallation = gradleInstallation;
    }

    @Nullable // if no installation can be located
    public GradleInstallation getInstallation() {
        return gradleInstallation;
    }

    @Nullable // if no installation can be located
    public static GradleInstallation get() {
        return locate().getInstallation();
    }

    public synchronized static CurrentGradleInstallation locate() {
        if (instance == null) {
            instance = CurrentGradleInstallationLocator.locate();
        }
        return instance;
    }

}
