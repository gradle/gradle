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

package org.gradle.internal.enterprise.core;

import com.google.common.annotations.VisibleForTesting;

public class GradleEnterprisePluginPresence {

    @VisibleForTesting
    public static final String NO_SCAN_PLUGIN_MSG = "An internal error occurred that prevented a build scan from being created.\n" +
        "Please report this via https://github.com/gradle/gradle/issues";

    private boolean present;

    public void markPresent() {
        this.present = true;
    }

    public boolean isPresent() {
        return present;
    }

}
