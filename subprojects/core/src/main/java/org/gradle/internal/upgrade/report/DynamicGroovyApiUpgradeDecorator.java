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
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.BeanDynamicObject.BeanDynamicObjectCallListenerRegistry;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class DynamicGroovyApiUpgradeDecorator {

    private static final AtomicReference<DynamicGroovyApiUpgradeDecorator> registry = new AtomicReference<>();
    private final ApiUpgradeProblemCollector collector;
    private final Lazy<List<DynamicGroovyUpgradeDecoration>> decorations;

    private DynamicGroovyApiUpgradeDecorator(ApiUpgradeProblemCollector collector) {
        this.collector = collector;
        this.decorations = Lazy.locking().of(() -> initDecorations(collector.getApiUpgrades()));
        BeanDynamicObjectCallListenerRegistry.setBeanDynamicObjectCallListener(new SetterBeanDynamicObjectCallListener(collector));
    }

    private List<DynamicGroovyUpgradeDecoration> initDecorations(List<ReportableApiChange> changes) {
        return changes.stream()
            .map(change -> change.mapToDynamicGroovyDecoration(collector))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(ImmutableList.toImmutableList());
    }

    public static void init(ApiUpgradeProblemCollector reporter) {
        registry.set(new DynamicGroovyApiUpgradeDecorator(reporter));
    }

    public static boolean shouldDecorateCallsiteArray() {
        return registry.get() != null;
    }

    /**
     * This method is called via reflection
     */
    @SuppressWarnings("unused")
    public static void decorateCallSiteArray(CallSiteArray callSites) {
        // TODO: It seems like for worker actions the instance may be null (different classloader)
        //       Though we should detect the situation and not silently ignore it.
        if (registry.get() == null || registry.get().collector.getApiUpgrades().isEmpty()) {
            return;
        }
        for (CallSite callSite : callSites.array) {
            for (DynamicGroovyUpgradeDecoration change : registry.get().decorations.get()) {
                change.decorateCallSite(callSite).ifPresent(decorated -> callSites.array[callSite.getIndex()] = decorated);
            }
        }
    }

    private static boolean isInstanceOfType(String typeName, Object bean) {
        try {
            if (bean == null) {
                return false;
            }
            ClassLoader classLoader = bean.getClass().getClassLoader();
            if (classLoader == null) {
                return Class.forName(typeName).isInstance(bean);
            } else {
                return classLoader.loadClass(typeName).isInstance(bean);
            }
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static class SetterBeanDynamicObjectCallListener implements BeanDynamicObject.BeanDynamicObjectCallListener {

        private final ApiUpgradeProblemCollector collector;
        private final Lazy<Map<String, Set<ReportableApiChange>>> setters;

        public SetterBeanDynamicObjectCallListener(ApiUpgradeProblemCollector collector) {
            this.collector = collector;
            this.setters = Lazy.locking().of(() -> initSetterToReportableChange(collector.getApiUpgrades()));
        }

        private Map<String, Set<ReportableApiChange>> initSetterToReportableChange(List<ReportableApiChange> changes) {
            Map<String, Set<ReportableApiChange>> map = new LinkedHashMap<>();
            changes.stream()
                .filter(change -> change.getId().getName().startsWith("set"))
                .forEach(change -> {
                    String propertyName = change.getId().getName().replace("set", "");
                    propertyName = propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
                    map.computeIfAbsent(propertyName, k -> new LinkedHashSet<>()).add(change);
                });
            return map;
        }

        @Override
        public void onSetProperty(Object bean, String propertyName, Object value) {
            Set<ReportableApiChange> upgrades = setters.get().getOrDefault(propertyName, Collections.emptySet());
            if (upgrades.isEmpty()) {
                return;
            }
            Optional<ReportableApiChange> matchingChange = upgrades.stream()
                .filter(change -> isInstanceOfType(change.getId().getOwner(), bean))
                .findFirst();
            if (matchingChange.isPresent()) {
                Optional<StackTraceElement> ownerStacktrace = getOwnerStackTraceElement();
                String file = ownerStacktrace.map(StackTraceElement::getFileName).orElse("<Unknown file>");
                int lineNumber = ownerStacktrace.map(StackTraceElement::getLineNumber).orElse(-1);
                collector.collectDynamicApiChangesReport(file, lineNumber, matchingChange.get().getApiChangeReport());
            }
        }

        private Optional<StackTraceElement> getOwnerStackTraceElement() {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                if (element.getLineNumber() >= 0 && element.getFileName() != null && element.getFileName().contains(File.separator)) {
                    return Optional.of(element);
                }
            }
            return Optional.empty();
        }
    }
}
