/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.tooling.events.problems.Problem;
import org.gradle.tooling.events.problems.ProblemId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProblemRenderer {

    private static String getFqn(ProblemId id) {
        var components = new ArrayList<String>();
        components.add(id.getName());

        var parentGroup = id.getGroup();
        while (parentGroup != null) {
            components.addFirst(parentGroup.getName());
            parentGroup = parentGroup.getParent();
        }

        return String.join(".", components) + " (" + id.getDisplayName() + ")";
    }

    private static void renderProblem(Problem problem, Map<ProblemId, Integer> summaryCounts) {
        System.out.println("Problem: " + getFqn(problem.getDefinition().getId()));
        System.out.println("  Contextual label: " + problem.getContextualLabel().getContextualLabel());
        System.out.println("  Details: " + problem.getDetails());

        if (!problem.getSolutions().isEmpty()) {
            System.out.println("  Solutions:");
            for (var solution : problem.getSolutions()) {
                System.out.println("    - " + solution.getSolution());
            }
        }

        if (!problem.getOriginLocations().isEmpty()) {
            System.out.println("  Origin locations:");
            for (var originLocation : problem.getOriginLocations()) {
                System.out.println("    - " + originLocation.getClass().getName());
            }
        }
        if (!problem.getContextualLocations().isEmpty()) {
            System.out.println("  Contextual locations:");
            for (var contextualLocation : problem.getContextualLocations()) {
                System.out.println("    - " + contextualLocation.getClass().getName());
            }
        }

        var additionalDataMap = problem.getAdditionalData().getAsMap();
        if (!additionalDataMap.isEmpty()) {
            System.out.println("  Additional data:");
            for (var entry : additionalDataMap.entrySet()) {
                System.out.println("    - " + entry.getKey() + ": " + entry.getValue());
            }
        }

        var summaryCount = summaryCounts.get(problem.getDefinition().getId());
        if (summaryCount != null) {
            System.out.println("  Also happened " + summaryCount + " times");
        }
    }

    public static void render(List<Problem> problemEvents, Map<ProblemId, Integer> summaryCounts) {
        for (var problem : problemEvents) {
            renderProblem(problem, summaryCounts);
        }
    }

}
