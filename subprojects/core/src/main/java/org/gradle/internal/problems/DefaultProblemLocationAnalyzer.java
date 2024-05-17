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

package org.gradle.internal.problems;

import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.initialization.ClassLoaderScopeId;
import org.gradle.initialization.ClassLoaderScopeOrigin;
import org.gradle.initialization.ClassLoaderScopeRegistryListener;
import org.gradle.initialization.ClassLoaderScopeRegistryListenerManager;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;
import org.gradle.problems.Location;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultProblemLocationAnalyzer implements ProblemLocationAnalyzer, ClassLoaderScopeRegistryListener, Closeable {
    private final Lock lock = new ReentrantLock();
    private final Map<String, ClassLoaderScopeOrigin.Script> scripts = new HashMap<>();
    private final ClassLoaderScopeRegistryListenerManager listenerManager;

    public DefaultProblemLocationAnalyzer(ClassLoaderScopeRegistryListenerManager listenerManager) {
        this.listenerManager = listenerManager;
        listenerManager.add(this);
    }

    @Override
    public void close() throws IOException {
        listenerManager.remove(this);
        lock.lock();
        try {
            scripts.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void childScopeCreated(ClassLoaderScopeId parentId, ClassLoaderScopeId childId, @javax.annotation.Nullable ClassLoaderScopeOrigin origin) {
        if (origin instanceof ClassLoaderScopeOrigin.Script) {
            ClassLoaderScopeOrigin.Script scriptOrigin = (ClassLoaderScopeOrigin.Script) origin;
            lock.lock();
            try {
                scripts.put(scriptOrigin.getFileName(), scriptOrigin);
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void classloaderCreated(ClassLoaderScopeId scopeId, ClassLoaderId classLoaderId, ClassLoader classLoader, ClassPath classPath, @javax.annotation.Nullable HashCode implementationHash) {
    }

    @Override
    public Location locationForUsage(List<StackTraceElement> stack, boolean fromException) {
        int startPos;
        int endPos;
        if (fromException) {
            // When analysing an exception stack trace, consider all the user code in the stack.
            // This is because we cannot tell the difference between:
            // - a validation exception thrown in user code that is called from other user code, where the caller should be blamed
            // - an unexpected exception thrown in user code that is called from other user code, where the called code should be blamed
            // So, for now, just blame the first user code that can be identified. This gives the user some clues for where to start
            startPos = 0;
            endPos = stack.size();
        } else {
            // When analysing a problem stack trace, consider only the deepest user code in the stack.
            startPos = findFirstUserCode(stack);
            if (startPos == stack.size()) {
                // No user code in the stack
                return null;
            }
            endPos = findNextGradleType(startPos, stack);
        }

        lock.lock();
        try {
            for (int i = startPos; i < endPos; i++) {
                StackTraceElement element = stack.get(i);
                if (element.getLineNumber() >= 0 && scripts.containsKey(element.getFileName())) {
                    ClassLoaderScopeOrigin.Script source = scripts.get(element.getFileName());
                    int lineNumber = element.getLineNumber();
                    return new Location(source.getLongDisplayName(), source.getShortDisplayName(), lineNumber);
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    private int findNextGradleType(int startPos, List<StackTraceElement> stack) {
        for (int i = startPos; i < stack.size(); i++) {
            StackTraceElement element = stack.get(i);
            if (element.getClassName().startsWith("org.gradle.")) {
                return i;
            }
        }
        return stack.size();
    }

    private int findFirstUserCode(List<StackTraceElement> stack) {
        int pos = 0;
        while (pos < stack.size() && isNonUserCode(stack.get(pos))) {
            pos++;
        }
        return pos;
    }

    private static boolean isNonUserCode(StackTraceElement element) {
        String className = element.getClassName();
        return className.startsWith("org.gradle.") ||
            className.startsWith("jdk.") ||
            className.startsWith("java.lang.reflect.") ||
            className.startsWith("java.util.concurrent.") ||
            className.equals("java.lang.Thread") ||
            className.startsWith("sun.") ||
            className.startsWith("com.sun.") ||
            className.startsWith("groovy.lang.") ||
            className.startsWith("org.codehaus.groovy.");
    }
}
