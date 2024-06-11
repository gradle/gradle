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
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.InternalStackTraceClassifier;
import org.gradle.internal.problems.failure.StackFramePredicate;
import org.gradle.problems.Location;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultProblemLocationAnalyzer implements ProblemLocationAnalyzer, ClassLoaderScopeRegistryListener, Closeable {

    private static final StackFramePredicate GRADLE_CODE = (frame, relevance) -> InternalStackTraceClassifier.isGradleCall(frame.getClassName());

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
    public Location locationForUsage(Failure failure, boolean fromException) {
        List<StackTraceElement> stack = failure.getStackTrace();
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
            startPos = failure.indexOfStackFrame(0, StackFramePredicate.USER_CODE);
            if (startPos == -1) {
                // No user code in the stack
                return null;
            }
            // Treat Gradle code as the boundary to allow stepping over JDK and Groovy calls
            endPos = failure.indexOfStackFrame(startPos + 1, GRADLE_CODE);
            if (endPos == -1) {
                endPos = stack.size();
            }
        }

        lock.lock();
        try {
            return locationFromStackRange(startPos, endPos, stack);
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    private Location locationFromStackRange(int startPos, int endPos, List<StackTraceElement> stack) {
        for (int i = startPos; i < endPos; i++) {
            Location location = tryGetLocation(stack.get(i));
            if (location != null) {
                return location;
            }
        }
        return null;
    }

    @Nullable
    private Location tryGetLocation(StackTraceElement frame) {
        int lineNumber = frame.getLineNumber();
        if (lineNumber < 0) {
            return null;
        }

        ClassLoaderScopeOrigin.Script source = scripts.get(frame.getFileName());
        if (source == null) {
            return null;
        }

        return new Location(source.getLongDisplayName(), source.getShortDisplayName(), lineNumber);
    }
}
