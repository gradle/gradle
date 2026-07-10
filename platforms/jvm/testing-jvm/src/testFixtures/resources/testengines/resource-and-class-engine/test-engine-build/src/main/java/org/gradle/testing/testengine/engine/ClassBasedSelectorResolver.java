/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.testengine.engine;

import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.discovery.SelectorResolver;
import org.junit.platform.engine.support.discovery.SelectorResolver.Context;
import org.junit.platform.engine.support.discovery.SelectorResolver.Match;
import org.junit.platform.engine.support.discovery.SelectorResolver.Resolution;
import org.gradle.testing.testengine.descriptor.ClassBasedTestDescriptor;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ClassBasedSelectorResolver implements SelectorResolver {
    public static final Logger LOGGER = LoggerFactory.getLogger(ClassBasedSelectorResolver.class);

    public SelectorResolver.Resolution resolve(ClassSelector selector, SelectorResolver.Context context) {
        try {
            Class<?> testClass = Class.forName(selector.getClassName());
            LOGGER.info(() -> "Test class: " + testClass.getName());
            UniqueId id = UniqueId.forEngine(ClassAndResourceBasedTestEngine.ENGINE_ID).append("class", testClass.getName());
            return Resolution.match(Match.exact(context.addToParent(parent -> Optional.of(new ClassBasedTestDescriptor(id, testClass))).get()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve ClassSelector for: " + selector.getClassName(), e);
        }
    }
}
