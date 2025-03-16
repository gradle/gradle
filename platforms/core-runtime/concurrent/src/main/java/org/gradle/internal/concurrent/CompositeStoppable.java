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

import org.gradle.internal.UncheckedException;
import org.gradle.internal.exceptions.DefaultMultiCauseException;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link org.gradle.internal.concurrent.Stoppable} that stops a collection of things. If an element implements
 * {@link java.io.Closeable} or {@link org.gradle.internal.concurrent.Stoppable} then the appropriate close/stop
 * method is called on that object, otherwise the element is ignored. Elements may be {@code null}, in which case they
 * are ignored.
 *
 * <p>Attempts to stop as many elements as possible in the presence of failures.</p>
 */
public class CompositeStoppable implements Stoppable {
    public static final Stoppable NO_OP_STOPPABLE = new Stoppable() {
        @Override
        public void stop() {
        }
    };
    private final List<Stoppable> elements = new ArrayList<Stoppable>();

    public CompositeStoppable() {
    }

    public static CompositeStoppable stoppable(Object... elements) {
        return new CompositeStoppable().add(elements);
    }

    public static CompositeStoppable stoppable(Iterable<?> elements) {
        return new CompositeStoppable().add(elements);
    }

    public CompositeStoppable addFailure(final Throwable failure) {
        add(new Closeable() {
            @Override
            public void close() {
                throw UncheckedException.throwAsUncheckedException(failure);
            }
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

    public synchronized CompositeStoppable add(Object closeable) {
        this.elements.add(toStoppable(closeable));
        return this;
    }

    private static Stoppable toStoppable(final Object object) {
        if (object instanceof Stoppable) {
            return (Stoppable) object;
        }
        if (object instanceof Closeable) {
            final Closeable closeable = (Closeable) object;
            return new Stoppable() {
                @Override
                public String toString() {
                    return closeable.toString();
                }

                @Override
                public void stop() {
                    try {
                        closeable.close();
                    } catch (IOException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
            };
        }
        return NO_OP_STOPPABLE;
    }

    @Override
    public synchronized void stop() {
        List<Throwable> failures = null;
        try {
            for (Stoppable element : elements) {
                try {
                    element.stop();
                } catch (Throwable throwable) {
                    if (failures == null) {
                        failures = new ArrayList<Throwable>();
                    }
                    failures.add(throwable);
                }
            }
        } finally {
            elements.clear();
        }

        if (failures != null) {
            if (failures.size() == 1) {
                throw UncheckedException.throwAsUncheckedException(failures.get(0));
            } else {
                throw new DefaultMultiCauseException("Could not stop all services.", failures);
            }
        }
    }
}
