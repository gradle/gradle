/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.io;

import org.gradle.api.Transformer;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.Closeable;

public abstract class IoUtils {

    // TODO merge in IoActions

    public static <T, C extends Closeable> T get(C resource, Transformer<T, ? super C> transformer) {
        try {
            return transformer.transform(resource);
        } finally {
            CompositeStoppable.stoppable(resource).stop();
        }
    }
}
