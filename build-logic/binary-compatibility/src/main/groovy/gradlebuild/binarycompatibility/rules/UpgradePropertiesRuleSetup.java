/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.binarycompatibility.rules;

import me.champeau.gradle.japicmp.report.SetupRule;
import me.champeau.gradle.japicmp.report.ViolationCheckContext;

import java.util.Map;

public class UpgradePropertiesRuleSetup implements SetupRule {

    private Map<String, String> params;

    public UpgradePropertiesRuleSetup(Map<String, String> params) {
        this.params = params;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(ViolationCheckContext context) {
        System.out.println("newArchives: " + params.get("newUpgradedProperties"));
        System.out.println("oldArchives: " + params.get("oldUpgradedProperties"));
    }

}
