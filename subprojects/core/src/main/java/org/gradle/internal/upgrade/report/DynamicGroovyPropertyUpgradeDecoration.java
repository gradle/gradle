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

import groovy.lang.Closure;
import groovy.lang.DelegatingMetaClass;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import org.codehaus.groovy.runtime.callsite.AbstractCallSite;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.gradle.internal.lazy.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Supplier;

public class DynamicGroovyPropertyUpgradeDecoration implements DynamicGroovyUpgradeDecoration {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicGroovyPropertyUpgradeDecoration.class);
    private final Class<?> type;
    private final String propertyName;
    private final Lazy<String> changeReport;

    public DynamicGroovyPropertyUpgradeDecoration(Class<?> type, String propertyName, Supplier<String> changeReport) {
        this.type = type;
        this.propertyName = propertyName;
        this.changeReport = Lazy.locking().of(changeReport);
        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(type);
        GroovySystem.getMetaClassRegistry().setMetaClass(type, new PropertySetterMetaClass(type, propertyName, metaClass));
    }

    @Override
    public Optional<CallSite> decorateCallSite(CallSite callSite) {
        String name = callSite.getName();
        if (name.equals(propertyName)) {
            return Optional.of(new AbstractCallSite(callSite) {
                @Override
                public Object callGroovyObjectGetProperty(Object receiver) throws Throwable {
                    Object typeToCheck = inferReceiverFromCallSiteReceiver(receiver);
                    if (type.isInstance(typeToCheck)) {
                        Optional<StackTraceElement> ownerStacktrace = getOwnerStackTraceElement();
                        String file = ownerStacktrace.map(StackTraceElement::getFileName).orElse("<Unknown file>");
                        int lineNumber = ownerStacktrace.map(StackTraceElement::getLineNumber).orElse(-1);
                        LOGGER.info("{}: line {}: {}", file, lineNumber, changeReport.get());
                    }
                    return super.callGroovyObjectGetProperty(receiver);
                }

                @Override
                public Object callGetProperty(Object receiver) throws Throwable {
                    if (type.isInstance(receiver)) {
                        Optional<StackTraceElement> ownerStacktrace = getOwnerStackTraceElement();
                        String file = ownerStacktrace.map(StackTraceElement::getFileName).orElse("<Unknown file>");
                        int lineNumber = ownerStacktrace.map(StackTraceElement::getLineNumber).orElse(-1);
                        LOGGER.info("{}: line {}: {}", file, lineNumber, changeReport.get());
                    }
                    return super.callGetProperty(receiver);
                }

                private Optional<StackTraceElement> getOwnerStackTraceElement() {
                    for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                        if (element.getLineNumber() >= 0 && element.getClassName().equals(callSite.getArray().owner.getName())) {
                            return Optional.of(element);
                        }
                    }
                    return Optional.empty();
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

    private static class PropertySetterMetaClass extends DelegatingMetaClass {
        private final Class<?> type;
        private final String propertyName;

        public PropertySetterMetaClass(Class<?> type, String propertyName, MetaClass delegate) {
            super(delegate);
            this.type = type;
            this.propertyName = propertyName;
        }

        @Override
        public void setProperty(Object object, String property, Object newValue) {
            if (property.equals(propertyName)) {
                LOGGER.info("Calling setter replacement for property {}.{}", type.getName(), propertyName);
            }
            super.setProperty(object, property, newValue);
        }
    }
}
