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

package org.gradle.testing.base.spi.internal;

import org.gradle.testing.base.spi.AssertionDetailsProvider;
import org.gradle.testing.base.spi.AssertionFailureDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

public class AssertionDetailsManager {

    private final List<AssertionDetailsProvider> providers;

    public AssertionDetailsManager(AssertionDetailsProvider defaultProvider, ClassLoader classLoader) {
        ServiceLoader<AssertionDetailsProvider> loader = ServiceLoader.load(AssertionDetailsProvider.class, classLoader); // TODO it seems like the service definition is ignored
        Iterator<AssertionDetailsProvider> iterator = loader.iterator();
        List<AssertionDetailsProvider> providers = new ArrayList<AssertionDetailsProvider>();
        while (iterator.hasNext()) {
            providers.add(iterator.next());
        }
        providers.add(defaultProvider);
        this.providers = Collections.unmodifiableList(providers);
    }

    public AssertionFailureDetails extractDetails(Throwable t) {
        return extractDetails(providers, t, t.getClass());
    }

    private static AssertionFailureDetails extractDetails(List<AssertionDetailsProvider> providers, Throwable t, Class<?> cls) {
        if (Object.class.equals(cls)) {
            return null;
        }
        String className = cls.getName();
        for (AssertionDetailsProvider extractor : providers) {
            if (extractor.isAssertionType(className)) {
                return extractor.extractAssertionDetails(t);
            }
        }
        return extractDetails(providers, t, cls.getSuperclass());
    }
}
