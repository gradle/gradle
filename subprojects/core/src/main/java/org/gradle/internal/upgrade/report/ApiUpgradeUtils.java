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

import org.codehaus.groovy.runtime.callsite.CallSite;

import java.io.File;
import java.util.Optional;
import java.util.function.Predicate;

public class ApiUpgradeUtils {

    public static boolean isInstanceOfType(String typeName, Object bean) {
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

    public static Optional<StackTraceElement> getStackTraceElementForCallSiteOwner(CallSite callSite) {
        return getStackTraceElement(element -> element.getClassName().equals(callSite.getArray().owner.getName()));
    }

    public static Optional<StackTraceElement> getStackTraceElementForAnyFile() {
        return getStackTraceElement(element -> element.getFileName() != null && element.getFileName().contains(File.separator));
    }

    private static Optional<StackTraceElement> getStackTraceElement(Predicate<StackTraceElement> predicate) {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getLineNumber() >= 0 && predicate.test(element)) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }
}
