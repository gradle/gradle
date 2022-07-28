/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.upgrade.report.groovy;

import com.google.common.collect.ImmutableList;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.upgrade.report.config.ApiUpgradeConfig;
import org.gradle.internal.upgrade.report.groovy.decorations.CallsiteApiUpgradeDecoration;
import org.gradle.internal.upgrade.report.problems.ApiUpgradeProblemCollector;

import java.util.List;
import java.util.Optional;

public class DefaultCallsiteApiUpgradeDecorator implements CallsiteApiUpgradeDecorator {

    private final Lazy<List<CallsiteApiUpgradeDecoration>> decorations;

    public DefaultCallsiteApiUpgradeDecorator(ApiUpgradeProblemCollector collector, ApiUpgradeConfig config) {
        this.decorations = Lazy.locking().of(() -> initDecorations(collector, config));
    }

    private List<CallsiteApiUpgradeDecoration> initDecorations(ApiUpgradeProblemCollector collector, ApiUpgradeConfig config) {
        return config.getApiUpgrades().stream()
            .map(change -> change.mapToDynamicGroovyDecoration(collector))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public boolean shouldDecorateCallsiteArray() {
        return !decorations.get().isEmpty();
    }

    @Override
    public void decorateCallSiteArray(CallSiteArray callSites) {
        for (CallSite callSite : callSites.array) {
            for (CallsiteApiUpgradeDecoration change : decorations.get()) {
                change.decorateCallSite(callSite).ifPresent(decorated -> callSites.array[callSite.getIndex()] = decorated);
            }
        }
    }
}
