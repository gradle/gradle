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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyModifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.Cast;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.inject.Inject;


/**
 * Dependency APIs for using <a href="https://docs.gradle.org/current/userguide/java_platform_plugin.html#java_platform_plugin">Platforms</a> in {@code dependencies} blocks.
 *
 * <p>
 * NOTE: This API is <strong>incubating</strong> and is likely to change until it's made stable.
 * </p>
 *
 * @since 7.6
 */
@Incubating
public interface PlatformDependencyModifiers {

    @Nested
    PlatformDependencyModifier getPlatform();

    abstract class PlatformDependencyModifier implements DependencyModifier {
        @Inject
        protected abstract ObjectFactory getObjectFactory();

        @Override
        public <D extends Dependency > D modify(D d) {
            if (d instanceof ModuleDependency) {
                ModuleDependency dependency = Cast.uncheckedCast(d);
                dependency.endorseStrictVersions();
                dependency.attributes(attributeContainer -> attributeContainer.attribute(Category.CATEGORY_ATTRIBUTE, getObjectFactory().named(Category.class, Category.REGULAR_PLATFORM)));
            }
            return d;
        }
    }

    @Nested
    EnforcedPlatformDependencyModifier getEnforcedPlatform();
    abstract class EnforcedPlatformDependencyModifier implements DependencyModifier {
        @Inject
        protected abstract ObjectFactory getObjectFactory();

        @Override
        public <D extends Dependency > D modify(D d) {
            if (d instanceof ExternalDependency) {
                DeprecationLogger.whileDisabled(() -> ((ExternalDependency)d).setForce(true));
            }
            if (d instanceof ModuleDependency) {
                ModuleDependency dependency = Cast.uncheckedCast(d);
                dependency.attributes(attributeContainer -> attributeContainer.attribute(Category.CATEGORY_ATTRIBUTE, getObjectFactory().named(Category.class, Category.ENFORCED_PLATFORM)));
            }
            return d;
        }
    }
}
