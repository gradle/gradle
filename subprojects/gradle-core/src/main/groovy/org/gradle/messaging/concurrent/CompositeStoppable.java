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

package org.gradle.messaging.concurrent;

import org.gradle.api.UncheckedIOException;
import org.gradle.util.GUtil;
import org.gradle.util.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CompositeStoppable implements Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompositeStoppable.class);
    private final List<Stoppable> elements = new CopyOnWriteArrayList<Stoppable>();

    public CompositeStoppable() {
    }

    public CompositeStoppable(Stoppable... elements) {
        this(Arrays.asList(elements));
    }

    public CompositeStoppable(Iterable<? extends Stoppable> elements) {
        GUtil.addToCollection(this.elements, elements);
    }

    public CompositeStoppable(Closeable... elements) {
        for (Closeable element : elements) {
            add(element);
        }
    }

    public CompositeStoppable add(Iterable<? extends Stoppable> elements) {
        GUtil.addToCollection(this.elements, elements);
        return this;
    }

    public CompositeStoppable add(Stoppable stoppable) {
        elements.add(stoppable);
        return this;
    }

    public CompositeStoppable add(Closeable closeable) {
        elements.add(toStoppable(closeable));
        return this;
    }

    public CompositeStoppable addCloseables(Iterable<? extends Closeable> closeables) {
        for (Closeable closeable : closeables) {
            add(closeable);
        }
        return this;
    }

    private Stoppable toStoppable(final Closeable closeable) {
        return new Stoppable() {
            public void stop() {
                try {
                    closeable.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }

    public void stop() {
        Throwable failure = null;
        try {
            for (Stoppable element : elements) {
                try {
                    element.stop();
                } catch (Throwable throwable) {
                    if (failure == null) {
                        failure = throwable;
                    } else {
                        LOGGER.error(String.format("Could not stop %s.", element), throwable);
                    }
                }
            }
        } finally {
            elements.clear();
        }

        if (failure != null) {
            throw UncheckedException.asUncheckedException(failure);
        }
    }
}
