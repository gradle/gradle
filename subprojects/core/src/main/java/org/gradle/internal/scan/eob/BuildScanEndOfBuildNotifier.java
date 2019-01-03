/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.scan.eob;

import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;

/**
 * Used by the scan plugin to register a listener to be notified about the build finishing.
 */
@UsedByScanPlugin
public interface BuildScanEndOfBuildNotifier {

    interface BuildResult {
        @Nullable
        Throwable getFailure();
    }

    interface BuildScanResult {

    }

    interface Listener {
        /**
         * Called after all user build logic has completed and the build outcome reported to the logging system. Also called prior to shutting down the build(-tree) scope services
         */
        BuildScanResult execute(BuildResult buildResult);
    }

    void notify(Listener listener);

}
