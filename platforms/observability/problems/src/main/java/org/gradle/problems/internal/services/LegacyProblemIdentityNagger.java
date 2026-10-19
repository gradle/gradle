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

import com.google.common.collect.ImmutableList;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.DefaultProblemGroup;
import org.gradle.api.problems.internal.DefaultProblemGroups;
import org.gradle.api.problems.internal.DefaultProblemId;
import org.gradle.api.problems.internal.ProblemGroupInternal;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.problems.internal.rendering.ProblemGroupRenderer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nags once per problem id when a reported problem's id was not created through {@code Problems.getGroups()}: through
 * the deprecated {@code ProblemId.create} or {@code ProblemGroup.create} factories, or from a custom
 * {@code ProblemGroup} subclass, which the problem builder copies into a factory group.
 * <p>
 * Lives in this module because the factories themselves cannot reach {@link DeprecationLogger}: {@code problems-api}
 * is a dependency of {@code logging}. Every reported problem passes through the summarizer, so this is the single
 * point where the daemon sees the identity of problems reported locally, from workers, and through the Tooling API.
 * <p>
 * The reporter replaces factory groups that match a predefined group with the predefined one before the problem
 * reaches the summarizer, in a worker before the problem is sent to the daemon. The nag therefore looks at the
 * {@linkplain ProblemInternal#getReportedId() id the producer reported}, and bases its advice on the id after the
 * replacement.
 */
public class LegacyProblemIdentityNagger {

    /**
     * The name of the deprecation problem the nag reports, one for the whole family so that reports and tests can
     * recognize it.
     */
    public static final String PROBLEM_NAME = "Problem reported outside the predefined problem groups";

    /**
     * The upgrading guide anchor every nag of this family links to; test harnesses recognize the family by it.
     */
    public static final String UPGRADE_GUIDE_SECTION = "problems_api_legacy_identity";

    private final Set<ProblemId> nagged = ConcurrentHashMap.newKeySet();

    /**
     * The nag reports a deprecation problem, which re-enters the summarizer on the same thread. That problem lives in
     * the predefined Gradle > Deprecation group and never nags; the guard keeps the recursion out regardless.
     */
    private final ThreadLocal<Boolean> nagging = ThreadLocal.withInitial(() -> false);

    public void nag(ProblemInternal problem) {
        if (nagging.get()) {
            return;
        }
        ProblemId reported = problem.getReportedId();
        if (!isLegacy(reported) || !nagged.add(reported)) {
            return;
        }
        nagging.set(true);
        try {
            DeprecationLogger.deprecateBehaviour("Reporting problem '" + ProblemGroupRenderer.render(reported)
                    + "' with an id not created through problems.getGroups().")
                .withAdvice(advice(problem.getDefinition().getId()))
                .withProblemName(PROBLEM_NAME)
                .willBeRemovedInGradle10()
                .withUpgradeGuideSection(9, UPGRADE_GUIDE_SECTION)
                .nagUser();
        } finally {
            nagging.set(false);
        }
    }

    /**
     * Whether the id was created through {@code ProblemId.create}, or a group in its chain is not from the predefined
     * hierarchy.
     */
    static boolean isLegacy(ProblemId id) {
        return (id instanceof DefaultProblemId && ((DefaultProblemId) id).isCreatedThroughFactory()) || outermostLegacyGroup(id.getGroup()) != null;
    }

    /**
     * What the producer should use instead, based on the id after the reporter replaced factory groups that match
     * predefined ones. When no factory group is left, the producer only has to create the id from its predefined group.
     * Otherwise it creates the outermost factory group below the closest predefined group, or below {@code Others}.
     * The advice names the group after its display name, as the upgrading guide does, but only when the predefined
     * hierarchy accepts that name there; factory names never had to follow the naming rules.
     */
    static String advice(ProblemId id) {
        DefaultProblemGroup legacy = outermostLegacyGroup(id.getGroup());
        if (legacy == null) {
            return "Create the id with problemId() on the predefined group '" + ProblemGroupRenderer.render(id.getGroup()) + "' from problems.getGroups() instead.";
        }
        ProblemGroup predefinedParent = legacy.getParent();
        String name = legacy.getDisplayName();
        if (predefinedParent == null) {
            String call = accepts(ImmutableList.of(DefaultProblemGroups.INSTANCE.getOthers().getName(), name)) ? "group(\"" + name + "\")" : "group(name)";
            return "Create the group from the predefined hierarchy instead, for example problems.getGroups().getOthers()." + call + ".";
        }
        if (accepts(ImmutableList.<String>builder().addAll(pathOf(predefinedParent)).add(name).build())) {
            return "Create the group below the predefined group '" + ProblemGroupRenderer.render(predefinedParent)
                + "' from problems.getGroups() instead, with group(\"" + name + "\").";
        }
        return "Create the group from the predefined hierarchy instead, for example problems.getGroups().getOthers().group(name).";
    }

    /**
     * Whether the predefined hierarchy can hold a group at this path: every name valid, no closed group, no group too deep.
     */
    private static boolean accepts(List<String> groupPath) {
        try {
            DefaultProblemGroups.INSTANCE.problemId(groupPath, "probe");
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static List<String> pathOf(ProblemGroup group) {
        ProblemGroup parent = group.getParent();
        return parent == null
            ? ImmutableList.of(group.getName())
            : ImmutableList.<String>builder().addAll(pathOf(parent)).add(group.getName()).build();
    }

    /**
     * The outermost legacy group in the chain, or {@code null} when every group comes from the predefined hierarchy.
     * Groups from the predefined hierarchy are never {@link DefaultProblemGroup}; only the legacy factories, and the
     * builder's copy of foreign {@link ProblemGroup} subclasses, produce that type.
     */
    @Nullable
    private static DefaultProblemGroup outermostLegacyGroup(ProblemGroup group) {
        DefaultProblemGroup outermost = null;
        for (ProblemGroupInternal current = ProblemGroupInternal.of(group); current != null; current = current.getParentInternal()) {
            if (current instanceof DefaultProblemGroup) {
                outermost = (DefaultProblemGroup) current;
            }
        }
        return outermost;
    }
}
