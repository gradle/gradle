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

package org.gradle.problems.internal.services;

import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.DefaultProblemGroup;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.ProblemGroupInternal;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.problems.internal.rendering.ProblemGroupRenderer;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nags once per problem id when a reported problem's identity was created outside the predefined group hierarchy,
 * that is through the deprecated {@code ProblemGroup.create} factories.
 * <p>
 * Lives in this module because the factories themselves cannot reach {@link DeprecationLogger}: {@code problems-api}
 * is a dependency of {@code logging}. Every reported problem passes through the summarizer, so this is the single
 * point where the daemon sees the identity of problems reported locally, from workers, and through the Tooling API.
 */
public class LegacyProblemIdentityNagger {

    public static final String PROBLEM_ID = "legacy-problem-identity";
    public static final String PROBLEM_ID_DISPLAY_NAME = "Problem reported with a group created through ProblemGroup.create()";

    /**
     * The upgrading guide anchor every nag of this family links to; test harnesses recognize the family by it.
     */
    public static final String UPGRADE_GUIDE_SECTION = "problems_api_legacy_identity";

    private final Set<ProblemId> nagged = ConcurrentHashMap.newKeySet();

    /**
     * The nag reports a deprecation problem, which re-enters the summarizer on the same thread. That problem lives in
     * Gradle's own deprecation group and is exempt anyway; the guard keeps the recursion out regardless of the gate.
     */
    private final ThreadLocal<Boolean> nagging = ThreadLocal.withInitial(() -> false);

    public void nag(ProblemInternal problem) {
        if (nagging.get()) {
            return;
        }
        ProblemId id = problem.getDefinition().getId();
        DefaultProblemGroup legacy = legacyGroupToReplace(id);
        if (legacy == null || !nagged.add(id)) {
            return;
        }
        nagging.set(true);
        try {
            DeprecationLogger.deprecateBehaviour("Reporting problem '" + ProblemGroupRenderer.render(id)
                    + "' with a group created through ProblemGroup.create().")
                .withAdvice("Create the group from the predefined hierarchy instead, for example "
                    + "problems.getGroups().getOthers().group(\"" + legacy.getName() + "\").")
                // one problem id for the whole family, so that reports and tests can recognize it
                .withProblemId(PROBLEM_ID)
                .withProblemIdDisplayName(PROBLEM_ID_DISPLAY_NAME)
                .willBeRemovedInGradle10()
                .withUpgradeGuideSection(9, UPGRADE_GUIDE_SECTION)
                .nagUser();
        } finally {
            nagging.set(false);
        }
    }

    /**
     * The group the producer has to replace, or {@code null} when the id needs no nag: its groups all come from the
     * predefined hierarchy, or its chain belongs to one of Gradle's own producers that stage 2 has not migrated yet
     * (see {@code GradleCoreProblemGroup.LEGACY_ROOT_NAMES}).
     */
    @Nullable
    static DefaultProblemGroup legacyGroupToReplace(ProblemId id) {
        DefaultProblemGroup legacy = legacyRootOf(id.getGroup());
        if (legacy == null || GradleCoreProblemGroup.isGradleOwnedLegacyChain(id.getGroup())) {
            return null;
        }
        return legacy;
    }

    /**
     * The outermost legacy group in the chain, or {@code null} when every group comes from the predefined hierarchy.
     * Groups from the predefined hierarchy are never {@link DefaultProblemGroup}; only the legacy factories, and the
     * builder's copy of foreign {@link ProblemGroup} subclasses, produce that type.
     */
    @Nullable
    private static DefaultProblemGroup legacyRootOf(ProblemGroup group) {
        DefaultProblemGroup outermost = null;
        for (ProblemGroupInternal current = ProblemGroupInternal.of(group); current != null; current = current.getParentInternal()) {
            if (current instanceof DefaultProblemGroup) {
                outermost = (DefaultProblemGroup) current;
            }
        }
        return outermost;
    }
}
