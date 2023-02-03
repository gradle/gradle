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

package org.gradle.integtests.fixtures

import groovy.transform.SelfType

/**
 * Utility methods for tests which work with any configuration reporting tasks (sub-types of {@code AbstractConfigurationReportTask}).
 */
@SelfType(AbstractIntegrationSpec)
trait InspectsConfigurationReport {
    void hasSecondaryVariantsLegend() {
        outputContains("(*) Secondary variants are variants created via the Configuration#getOutgoing(): ConfigurationPublications API which also participate in selection, in addition to the configuration itself.")
    }

    void doesNotHaveSecondaryVariantsLegend() {
        outputDoesNotContain("(*) Secondary variants are variants created via the Configuration#getOutgoing(): ConfigurationPublications API which also participate in selection, in addition to the configuration itself.")
    }

    void hasLegacyLegend() {
        outputContains("(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.")
    }

    void doesNotHaveLegacyLegend() {
        outputDoesNotContain("(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.")
    }

    void hasIncubatingLegend() {
        outputContains("(i) Configuration uses incubating attributes such as Category.VERIFICATION.")
    }

    void doesNotHaveIncubatingLegend() {
        outputDoesNotContain("(i) Configuration uses incubating attributes such as Category.VERIFICATION.")
    }

    void hasTransitiveLegend() {
        outputContains("(t) Configuration extended transitively.")
    }

    void doesNotHaveTransitiveLegend() {
        outputDoesNotContain("(t) Configuration extended transitively.")
    }

    void reportsCompleteAbsenceOfResolvableConfigurations() {
        outputContains("There are no resolvable configurations (including legacy configurations) present in project")
    }

    void reportsCompleteAbsenceOfResolvableVariants() {
        outputContains("There are no outgoing variants (including legacy variants) present in project")
    }

    void reportsNoProperConfigurations() {
        outputContains("There are no purely resolvable configurations present in project")
    }

    void reportsNoProperVariants() {
        outputContains("There are no purely consumable variants present in project")
    }

    void promptsForRerunToFindMoreConfigurations() {
        outputContains("Re-run this report with the '--all' flag to include legacy configurations (legacy = consumable and resolvable).")
    }

    void promptsForRerunToFindMoreVariants() {
        outputContains("Re-run this report with the '--all' flag to include legacy variants (legacy = consumable and resolvable).")
    }

    void doesNotPromptForRerunToFindMoreConfigurations() {
        outputDoesNotContain("Re-run this report with the '--all' flag to include legacy configurations (legacy = consumable and resolvable).")
    }

    void doesNotPromptForRerunToFindMoreVariants() {
        outputDoesNotContain("Re-run this report with the '--all' flag to include legacy variants (legacy = consumable and resolvable).")
    }
}
