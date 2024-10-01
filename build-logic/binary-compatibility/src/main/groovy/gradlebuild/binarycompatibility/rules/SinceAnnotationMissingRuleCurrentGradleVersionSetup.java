/*
 * Copyright 2020 the original author or authors.
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

public class SinceAnnotationMissingRuleCurrentGradleVersionSetup implements SetupRule {

    private final String currentVersion;
    private final String currentMasterVersion;

    public SinceAnnotationMissingRuleCurrentGradleVersionSetup(Map<String, String> currentVersion) {
        this.currentVersion = currentVersion.get("currentVersion");
        // Version that is used as "current version" on master, required only for EAP branch
        this.currentMasterVersion = getCurrentMasterVersion(currentVersion.get("baselineVersion"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(ViolationCheckContext context) {
        Map<String, Object> userData = (Map<String, Object>) context.getUserData();
        userData.put("currentVersion", currentVersion);
        userData.put("currentMasterVersion", currentMasterVersion);
    }

    private static String getCurrentMasterVersion(String baselineVersion) {
        if (baselineVersion == null || baselineVersion.isEmpty() || baselineVersion.split("[.]").length < 2) {
            throw new IllegalArgumentException("Baseline version must be provided in x.y[.z] format");
        }
        String[] versions = baselineVersion.split("[.]");
        String majorVersion = versions[0];
        // Minor version can have a suffix, e.g. 8.11-20241004012543+0000
        String minorVersion = versions[1].split("-")[0];
        return majorVersion + "." + (Integer.parseInt(minorVersion) + 1);
    }
}
