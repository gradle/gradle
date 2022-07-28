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

package org.gradle.internal.upgrade.report.groovy.decorations;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.callsite.AbstractCallSite;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.upgrade.report.problems.ApiUpgradeProblemCollector;

import java.util.Optional;
import java.util.function.Supplier;

import static org.gradle.internal.upgrade.report.ApiUpgradeUtils.getStackTraceElementForCallSiteOwner;
import static org.gradle.internal.upgrade.report.ApiUpgradeUtils.isInstanceOfType;

public class PropertyCallsiteApiUpgradeDecoration implements CallsiteApiUpgradeDecoration {
    private final ApiUpgradeProblemCollector collector;
    private final String propertyName;
    private final Lazy<String> changeReport;
    private final String typeName;

    public PropertyCallsiteApiUpgradeDecoration(ApiUpgradeProblemCollector problemCollector, String typeName, String propertyName, Supplier<String> changeReport) {
        this.collector = problemCollector;
        this.typeName = typeName;
        this.propertyName = propertyName;
        this.changeReport = Lazy.locking().of(changeReport);
    }

    @Override
    public Optional<CallSite> decorateCallSite(CallSite callSite) {
        String name = callSite.getName();
        if (name.equals(propertyName)) {
            return Optional.of(new AbstractCallSite(callSite) {
                @Override
                public Object callGroovyObjectGetProperty(Object receiver) throws Throwable {
                    Object typeToCheck = inferReceiverFromCallSiteReceiver(receiver);
                    if (isInstanceOfType(typeName, typeToCheck)) {
                        Optional<StackTraceElement> ownerStacktrace = getStackTraceElementForCallSiteOwner(callSite);
                        String file = ownerStacktrace.map(StackTraceElement::getFileName).orElse("<Unknown file>");
                        int lineNumber = ownerStacktrace.map(StackTraceElement::getLineNumber).orElse(-1);
                        collector.collectDynamicApiChangesReport(file, lineNumber, changeReport.get());
                    }
                    return super.callGroovyObjectGetProperty(receiver);
                }

                @Override
                public Object callGetProperty(Object receiver) throws Throwable {
                    if (isInstanceOfType(typeName, receiver)) {
                        Optional<StackTraceElement> ownerStacktrace = getStackTraceElementForCallSiteOwner(callSite);
                        String file = ownerStacktrace.map(StackTraceElement::getFileName).orElse("<Unknown file>");
                        int lineNumber = ownerStacktrace.map(StackTraceElement::getLineNumber).orElse(-1);
                        collector.collectDynamicApiChangesReport(file, lineNumber, changeReport.get());
                    }
                    return super.callGetProperty(receiver);
                }
            });
        }
        return Optional.empty();
    }

    private Object inferReceiverFromCallSiteReceiver(Object callSiteReceiver) {
        return callSiteReceiver instanceof Closure
            ? ((Closure<?>) callSiteReceiver).getDelegate()
            : callSiteReceiver;
    }
}
