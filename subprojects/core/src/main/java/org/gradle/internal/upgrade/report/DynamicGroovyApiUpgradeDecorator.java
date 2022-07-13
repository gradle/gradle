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

package org.gradle.internal.upgrade.report;

import com.google.common.collect.ImmutableList;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.gradle.internal.lazy.Lazy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class DynamicGroovyApiUpgradeDecorator {

    private static final AtomicReference<DynamicGroovyApiUpgradeDecorator> registry = new AtomicReference<>();

    private final Lazy<List<DynamicGroovyUpgradeDecoration>> decorations;

    private DynamicGroovyApiUpgradeDecorator(List<ReportableApiChange> changes) {
        this.decorations = Lazy.locking().of(() -> initChanges(changes));
    }

    public static void init(List<ReportableApiChange> changes) {
        registry.compareAndSet(null, new DynamicGroovyApiUpgradeDecorator(changes));
    }

    private static List<DynamicGroovyUpgradeDecoration> initChanges(List<ReportableApiChange> changes) {
        return changes.stream()
            .map(ReportableApiChange::mapToDynamicGroovyDecoration)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(ImmutableList.toImmutableList());
    }

    /**
     * This method is called via reflection
     */
    @SuppressWarnings("unused")
    public static void decorateCallSiteArray(CallSiteArray callSites) {
        // TODO: It seems like for worker actions the instance may be null (different classloader)
        //       Though we should detect the situation and not silently ignore it.
        if (registry.get() == null) {
            return;
        }
        for (CallSite callSite : callSites.array) {
            for (DynamicGroovyUpgradeDecoration change : registry.get().decorations.get()) {
                change.decorateCallSite(callSite).ifPresent(decorated -> callSites.array[callSite.getIndex()] = decorated);
            }
        }
    }
}
