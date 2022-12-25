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

package org.gradle.internal.classloader;

import com.google.common.base.Throwables;

import javax.annotation.Nullable;

/**
 * A helper to handle transform errors in {@link org.gradle.internal.agents.InstrumentingClassLoader}.
 */
public class TransformErrorHandler {
    private final ThreadLocal<ClassNotFoundException> lastError = new ThreadLocal<ClassNotFoundException>();
    private final String classLoaderName;

    public TransformErrorHandler(String classLoaderName) {
        this.classLoaderName = classLoaderName;
    }

    /**
     * Marks the beginning of code where a transformation exception may occur.
     *
     * @throws ClassNotFoundException if there is a pending exception from outside the scope
     */
    public void enterClassLoadingScope(String className) throws ClassNotFoundException {
        ClassNotFoundException lastError = getLastErrorAndClear();
        if (lastError != null) {
            throw new ClassNotFoundException("A pending instrumentation exception prevented loading a class " + className + " in " + classLoaderName, lastError);
        }
    }

    public void classLoadingError(@Nullable String className, Throwable cause) {
        ClassNotFoundException newError = new ClassNotFoundException("Failed to instrument class " + className + " in " + classLoaderName, cause);
        Throwable prevError = lastError.get();
        if (prevError == null) {
            lastError.set(newError);
        } else {
            // We've got a chain of exceptions while loading classes.
            addSuppressedIfAvailable(prevError, newError);
        }
    }

    /**
     * Marks the end of code where a transformation exception may occur.
     *
     * @throws ClassNotFoundException if the exception happened during transformation
     */
    public void exitClassLoadingScope() throws ClassNotFoundException {
        ClassNotFoundException lastError = getLastErrorAndClear();
        if (lastError != null) {
            throw lastError;
        }
    }
    /**
     * Marks the end of code where a transformation exception may occur, if this block completed exceptionally.
     * Rethrows the supplied throwable.
     *
     * @return this method never returns, but can be used in a throw expression
     */
    public ClassNotFoundException exitClassLoadingScopeWithException(Throwable th) throws ClassNotFoundException {
        ClassNotFoundException pendingException = getLastErrorAndClear();
        if (pendingException != null) {
            addSuppressedIfAvailable(th, pendingException);
        }
        Throwables.propagateIfPossible(th, ClassNotFoundException.class);
        throw new RuntimeException("Unexpected exception type", th);
    }

    @Nullable
    private ClassNotFoundException getLastErrorAndClear() {
        ClassNotFoundException th = lastError.get();
        lastError.remove();
        return th;
    }

    @SuppressWarnings("Since15")
    private static void addSuppressedIfAvailable(Throwable th, Throwable suppressed) {
        try {
            th.addSuppressed(suppressed);
        } catch (NoSuchMethodError ignored) {
            // addSuppressed is Java 7+
        }
    }
}
