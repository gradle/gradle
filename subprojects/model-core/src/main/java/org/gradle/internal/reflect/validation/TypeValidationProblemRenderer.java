/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.reflect.validation;

import org.gradle.problems.Solution;
import org.gradle.model.internal.type.ModelType;

import java.util.List;
import java.util.stream.Collectors;

public class TypeValidationProblemRenderer {

    // We should get rid of this method quickly, as the validation message
    // system was designed to display the same message unconditionally
    // whatever asks for it, when it should be the responisiblity of the
    // consumer to render it as they need. For example, an HTML renderer
    // may use the same information differently
    public static String renderMinimalInformationAbout(TypeValidationProblem problem) {
        String context = problem.getWhere()
            .getType()
            .map(c -> "Type '" + ModelType.of(c).getDisplayName() + "': ")
            .orElse("");
        StringBuilder details = new StringBuilder(maybeAppendDot(problem.getShortDescription()));
        problem.getWhy().ifPresent(reason -> details.append(" ").append(maybeAppendDot(reason)));
        List<Solution> possibleSolutions = problem.getPossibleSolutions();
        int solutionCount = possibleSolutions.size();
        if (solutionCount > 0) {
            details.append(" Possible solution").append(solutionCount > 1 ? "s" : "").append(": ");
            details.append(maybeAppendDot(possibleSolutions.stream()
                .map(Solution::getShortDescription)
                .collect(Collectors.joining(" or "))));
        }
        return context + details;
    }

    private static String maybeAppendDot(String txt) {
        if (txt.endsWith(".")) {
            return txt;
        }
        return txt + ".";
    }

}
