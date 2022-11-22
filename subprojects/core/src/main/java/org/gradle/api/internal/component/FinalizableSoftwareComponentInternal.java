/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.component;

import org.gradle.internal.deprecation.DeprecationLogger;

public abstract class FinalizableSoftwareComponentInternal implements SoftwareComponentInternal {

    private boolean finalized = false;

    protected void checkNotFinalized() {
        if (finalized) {
            DeprecationLogger.deprecateInvocation("")
                .willBecomeAnErrorInGradle9()
                .withUpgradeGuideSection(8, "org_gradle_util_reports_deprecations")
                .nagUser();
        }
    }

    @Override
    public void finalizeValue() {
        finalized = true;
    }
}
