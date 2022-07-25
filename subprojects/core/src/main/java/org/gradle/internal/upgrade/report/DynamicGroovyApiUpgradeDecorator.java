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
import groovy.lang.DelegatingMetaClass;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.gradle.internal.lazy.Lazy;

import java.util.LinkedHashSet;
import java.util.List;
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
        if (registry.get() == null) {
            return;
        }
        for (CallSite callSite : callSites.array) {
            for (DynamicGroovyUpgradeDecoration change : registry.get().decorations.get()) {
                change.decorateCallSite(callSite).ifPresent(decorated -> callSites.array[callSite.getIndex()] = decorated);
            }
        }
    }

    @SuppressWarnings("unused")
    public static void decorateMetaClass(Class<?> type) {
        Set<String> typeHierarchy = getTypeHierarchy(type);
        ApiUpgradeProblemCollector problemCollector = registry.get().collector;
        problemCollector.getApiUpgrades().stream()
            .filter(upgrade -> upgrade.getId().getName().startsWith("set") && typeHierarchy.contains(upgrade.getId().getOwner()))
            .forEach(upgrade -> registerMetaClass(type, upgrade, problemCollector));
    }

    private static void registerMetaClass(Class<?> type, ReportableApiChange apiUpgrade, ApiUpgradeProblemCollector problemCollector) {
        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(type);
        String propertyName = apiUpgrade.getId().getName().replace("set", "");
        propertyName = propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
        GroovySystem.getMetaClassRegistry().setMetaClass(type, new PropertySetterMetaClass(propertyName, apiUpgrade, problemCollector, metaClass));
    }

    private static Set<String> getTypeHierarchy(Class<?> clazz) {
        Set<String> classes = new LinkedHashSet<>();
        while (clazz != null) {
            classes.add(clazz.getName());
            for (Class<?> anInterface : clazz.getInterfaces()) {
                classes.add(anInterface.getName());
            }
            clazz = clazz.getSuperclass();
        }
        return classes;
    }

    private static class PropertySetterMetaClass extends DelegatingMetaClass {
        private final String propertyName;
        private final ReportableApiChange apiUpgrade;
        private final ApiUpgradeProblemCollector problemCollector;

        public PropertySetterMetaClass(String propertyName, ReportableApiChange apiUpgrade, ApiUpgradeProblemCollector problemCollector, MetaClass delegate) {
            super(delegate);
            this.propertyName = propertyName;
            this.apiUpgrade = apiUpgrade;
            this.problemCollector = problemCollector;
        }

        @Override
        public void setProperty(Object object, String property, Object newValue) {
            if (property.equals(propertyName)) {
                Optional<StackTraceElement> element = getOwnerStackTraceElement();
                String sourceFile = element.map(StackTraceElement::getFileName).orElse("Unknown");
                int lineNumber = element.map(StackTraceElement::getLineNumber).orElse(-1);
                problemCollector.collectDynamicApiChangesReport(sourceFile, lineNumber, apiUpgrade.getApiChangeReport());
            }
            super.setProperty(object, property, newValue);
        }

        private Optional<StackTraceElement> getOwnerStackTraceElement() {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                if (element.getLineNumber() >= 0) {
                    return Optional.of(element);
                }
            }
            return Optional.empty();
        }
    }
}
