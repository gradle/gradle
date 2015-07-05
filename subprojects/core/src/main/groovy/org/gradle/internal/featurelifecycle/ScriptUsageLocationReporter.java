/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.featurelifecycle;

import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang.StringUtils;
import org.gradle.groovy.scripts.Script;
import org.gradle.groovy.scripts.ScriptExecutionListener;
import org.gradle.groovy.scripts.ScriptSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class ScriptUsageLocationReporter implements ScriptExecutionListener, UsageLocationReporter {
    private final Lock lock = new ReentrantLock();
    private final Map<String, ScriptSource> scripts = new HashMap<String, ScriptSource>();

    @Override
    public void scriptClassLoaded(ScriptSource scriptSource, Class<? extends Script> scriptClass) {
        lock.lock();
        try {
            scripts.put(scriptSource.getFileName(), scriptSource);
        } finally {
            lock.unlock();
        }
    }

    public void reportLocation(DeprecatedFeatureUsage usage, StringBuilder target) {
        lock.lock();
        try {
            doReportLocation(usage, target);
        } finally {
            lock.unlock();
        }
    }

    private void doReportLocation(DeprecatedFeatureUsage usage, StringBuilder target) {
        List<StackTraceElement> stack = usage.getStack();
        if (stack.isEmpty()) {
            return;
        }

        StackTraceElement directCaller = stack.get(0);
        if (scripts.containsKey(directCaller.getFileName())) {
            reportStackTraceElement(directCaller, target);
            return;
        }

        int caller = 1;
        while (caller < stack.size() && stack.get(caller).getClassName().equals(directCaller.getClassName())) {
            caller++;
        }
        if (caller == stack.size()) {
            return;
        }
        StackTraceElement indirectCaller = stack.get(caller);
        if (scripts.containsKey(indirectCaller.getFileName())) {
            reportStackTraceElement(indirectCaller, target);
        }
    }

    private void reportStackTraceElement(StackTraceElement stackTraceElement, StringBuilder target) {
        ScriptSource scriptSource = scripts.get(stackTraceElement.getFileName());
        target.append(StringUtils.capitalize(scriptSource.getDisplayName()));
        if (stackTraceElement.getLineNumber() > 0) {
            target.append(": line ");
            target.append(stackTraceElement.getLineNumber());
        }
    }
}
