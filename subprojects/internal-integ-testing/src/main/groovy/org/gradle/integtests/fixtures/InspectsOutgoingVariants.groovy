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
 * Utility methods for tests which work with the outgoing variants task.
 */
@SelfType(AbstractIntegrationSpec)
trait InspectsOutgoingVariants {
    void hasSecondaryVariantsLegend() {
        outputContains("(*) Secondary variants are variants created via the Configuration#getOutgoing(): ConfigurationPublications API which also participate in selection, in addition to the configuration itself.")
    }

    void doesNotHaveSecondaryVariantsLegend() {
        outputDoesNotContain("(*) Secondary variants are variants created via the Configuration#getOutgoing(): ConfigurationPublications API which also participate in selection, in addition to the configuration itself.")
    }

    void hasLegacyVariantsLegend() {
        outputContains("(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.")
    }

    void doesNotHaveLegacyVariantsLegend() {
        outputDoesNotContain("(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.")
    }

    void hasIncubatingVariantsLegend() {
        outputContains("(i) Configuration uses incubating attributes such as Category.VERIFICATION.")
    }

    void doesNotHaveIncubatingVariantsLegend() {
        outputDoesNotContain("(i) Configuration uses incubating attributes such as Category.VERIFICATION.")
    }
}
