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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import com.google.common.collect.ImmutableMap;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.internal.reflect.JavaReflectionUtil;

import java.util.Collection;

import static org.gradle.internal.component.model.ComponentResolveMetaData.MetaDataOrigin;

public class DefaultModuleResolutionFilterFactory implements ModuleResolutionFilterFactory {
    public static final ImmutableMap<MetaDataOrigin, Class<? extends DefaultModuleResolutionFilter>> SUPPORTED_ORIGINS;
    private final Class<? extends DefaultModuleResolutionFilter> filterClass;

    static {
        SUPPORTED_ORIGINS = ImmutableMap.<MetaDataOrigin, Class<? extends DefaultModuleResolutionFilter>>builder()
                                .put(MetaDataOrigin.Ivy, IvyModuleResolutionFilter.class)
                                .put(MetaDataOrigin.Maven, MavenModuleResolutionFilter.class)
                                .put(MetaDataOrigin.Gradle, GradleModuleResolutionFilter.class)
                                .build();
    }

    public DefaultModuleResolutionFilterFactory(MetaDataOrigin origin) {
        assert SUPPORTED_ORIGINS.containsKey(origin) : String.format("Unsupported origin '%s'", origin);
        filterClass = SUPPORTED_ORIGINS.get(origin);
    }

    public ModuleResolutionFilter all() {
        return JavaReflectionUtil.staticMethod(filterClass, ModuleResolutionFilter.class, "all").invokeStatic();
    }

    public ModuleResolutionFilter excludeAny(ExcludeRule... excludeRules) {
        return (ModuleResolutionFilter)JavaReflectionUtil.staticMethod(filterClass, ModuleResolutionFilter.class, "excludeAny", new Class[] {ExcludeRule[].class}).invokeStatic(new Object[] {excludeRules});
    }

    public ModuleResolutionFilter excludeAny(Collection<ExcludeRule> excludeRules) {
        return JavaReflectionUtil.staticMethod(filterClass, ModuleResolutionFilter.class, "excludeAny", Collection.class).invokeStatic(excludeRules);
    }
}
