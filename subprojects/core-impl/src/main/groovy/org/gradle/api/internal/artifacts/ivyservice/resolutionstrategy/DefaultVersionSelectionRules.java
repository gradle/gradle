/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy;

import org.gradle.api.Action;
import org.gradle.api.artifacts.VersionSelection;
import org.gradle.api.artifacts.VersionSelectionRules;
import org.gradle.api.internal.UserCodeAction;
import org.gradle.api.internal.artifacts.VersionSelectionRulesInternal;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultVersionSelectionRules implements VersionSelectionRulesInternal {
    final Set<Action<? super VersionSelection>> versionSelectionActions = new LinkedHashSet<Action<? super VersionSelection>>();

    public void apply(VersionSelection selection) {
        for (Action<? super VersionSelection> action : versionSelectionActions) {
            action.execute(selection);
        }
    }

    public boolean hasRules() {
        return versionSelectionActions.size() > 0;
    }

    public VersionSelectionRules anyVersion(Action<? super VersionSelection> selectionAction) {
        versionSelectionActions.add(new UserCodeAction<VersionSelection>("Could not apply version selection rule with any()", selectionAction));
        return this;
    }
}
