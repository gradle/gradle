/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.resolve.result;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

public class DefaultChosenComponentResult implements ChosenComponentResult {
    private Reason reason = Reason.NO_MATCH;
    private ModuleComponentIdentifier moduleComponentIdentifier;

    public boolean hasMatch() {
        return reason == Reason.MATCH;
    }

    public boolean hasNoMatch() {
        return reason == Reason.NO_MATCH;
    }

    public void matches(ModuleComponentIdentifier moduleComponentIdentifier) {
        setChosenComponentWithReason(Reason.MATCH, moduleComponentIdentifier);
    }

    public void noMatch() {
        setChosenComponentWithReason(Reason.NO_MATCH, null);
    }

    public void cannotDetermine() {
        setChosenComponentWithReason(Reason.CANNOT_DETERMINE, null);
    }

    private void setChosenComponentWithReason(Reason reason, ModuleComponentIdentifier moduleComponentIdentifier) {
        this.reason = reason;
        this.moduleComponentIdentifier = moduleComponentIdentifier;
    }

    public Reason getReason() {
        return reason;
    }

    public ModuleComponentIdentifier getModuleComponentIdentifier() {
        return moduleComponentIdentifier;
    }
}
