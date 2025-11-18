/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.internal.concurrent;

import org.gradle.internal.exceptions.DefaultMultiCauseException;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

/**
 * A {@link org.gradle.internal.concurrent.Stoppable} that stops a collection of things. If an element implements
 * {@link java.io.Closeable} or {@link org.gradle.internal.concurrent.Stoppable} then the appropriate close/stop
 * method is called on that object; otherwise the element is ignored. Elements may be {@code null}, in which case they
 * are ignored.
 *
 * <p>Attempts to stop as many elements as possible in the presence of failures.</p>
 */
public class CompositeStoppable implements Stoppable {

    public static void stopAll(Object service) {
        try {
            stopOne(service);
        } catch (IOException e) {
            throw throwAsUncheckedException(e);
        }
    }

    public static void stopAll(Object... services) {
        stopAll(Arrays.asList(services));
    }

    public static void stopAll(Iterable<?> services) {
        List<Throwable> failures = null;
        for (Object service : services) {
            try {
                stopOne(service);
            } catch (Throwable throwable) {
                if (failures == null) {
                    failures = new ArrayList<>();
                }
                failures.add(throwable);
            }
        }
        if (failures != null) {
            if (failures.size() == 1) {
                throw throwAsUncheckedException(failures.get(0));
            } else {
                throw new DefaultMultiCauseException("Could not stop all services.", failures);
            }
        }
    }

    private final List<Object> elements = new ArrayList<>();

    public CompositeStoppable() {
    }

    public CompositeStoppable addFailure(final Throwable failure) {
        add((Stoppable) () -> {
            throw throwAsUncheckedException(failure);
        });
        return this;
    }

    public CompositeStoppable add(Iterable<?> elements) {
        for (Object closeable : elements) {
            add(closeable);
        }
        return this;
    }

    public CompositeStoppable add(Object... elements) {
        for (Object closeable : elements) {
            add(closeable);
        }
        return this;
    }

    public synchronized CompositeStoppable add(Object stoppable) {
        if (stoppable instanceof Stoppable || stoppable instanceof Closeable) {
            elements.add(stoppable);
        }
        return this;
    }

    @Override
    public synchronized void stop() {
        try {
            stopAll(elements);
        } finally {
            elements.clear();
        }
    }

    private static void stopOne(Object service) throws IOException {
        if (service instanceof Stoppable) {
            ((Stoppable) service).stop();
        } else if (service instanceof Closeable) {
            ((Closeable) service).close();
        }
    }

}
