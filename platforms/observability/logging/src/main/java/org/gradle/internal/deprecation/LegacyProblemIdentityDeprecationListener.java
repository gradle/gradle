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

package org.gradle.internal.deprecation;

import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.LegacyProblemIdentityUsage;

/**
 * Turns a use of the deprecated problem identity API, reported by the problems-api module, into a deprecation warning.
 */
final class LegacyProblemIdentityDeprecationListener implements LegacyProblemIdentityUsage.Listener {

    private static final String UPGRADE_GUIDE_SECTION = "problems_api_legacy_identity";
    private static final String PREDEFINED_GROUPS_ADVICE = "Use the predefined groups from Problems.getGroups() instead.";

    @Override
    public void used(LegacyProblemIdentityUsage.Usage usage) {
        switch (usage) {
            case GROUP_DISPLAY_NAME:
                displayName(ProblemGroup.class);
                break;
            case ID_DISPLAY_NAME:
                displayName(ProblemId.class);
                break;
            case GROUP_PARENT:
                DeprecationLogger.deprecateMethod(ProblemGroup.class, "getParent()")
                    .withAdvice("The predefined groups from Problems.getGroups() have a fixed place in the hierarchy.")
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, UPGRADE_GUIDE_SECTION)
                    .nagUser();
                break;
            case GROUP_SUBCLASS:
                subclass(ProblemGroup.class);
                break;
            case ID_SUBCLASS:
                subclass(ProblemId.class);
                break;
            default:
                throw new IllegalArgumentException("Unknown usage " + usage);
        }
    }

    private static void displayName(Class<?> type) {
        DeprecationLogger.deprecateMethod(type, "getDisplayName()")
            .replaceWith("getName()")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, UPGRADE_GUIDE_SECTION)
            .nagUser();
    }

    private static void subclass(Class<?> type) {
        DeprecationLogger.deprecate("Subclassing " + type.getSimpleName())
            .withAdvice(PREDEFINED_GROUPS_ADVICE)
            .willBecomeAnErrorInGradle10()
            .withUpgradeGuideSection(9, UPGRADE_GUIDE_SECTION)
            .nagUser();
    }
}
