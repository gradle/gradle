/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.plugins.matcher.Matcher;
import org.apache.ivy.plugins.matcher.NoMatcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.internal.reflect.JavaReflectionUtil;

public class ChangingModuleDetector {
    private final DependencyResolver resolver;

    public ChangingModuleDetector(DependencyResolver resolver) {
        this.resolver = resolver;
    }

    public boolean isChangingModule(ModuleDescriptor moduleDescriptor) {
        return getChangingMatcher().matches(moduleDescriptor.getResolvedModuleRevisionId().getRevision());
    }

    private Matcher getChangingMatcher() {
        if (!(resolver instanceof AbstractResolver)) {
            return NoMatcher.INSTANCE;
        }

        AbstractResolver abstractResolver = (AbstractResolver) resolver;
        String changingMatcherName = JavaReflectionUtil.method(AbstractResolver.class, String.class, "getChangingMatcherName").invoke(abstractResolver);
        String changingPattern = JavaReflectionUtil.method(AbstractResolver.class, String.class, "getChangingPattern").invoke(abstractResolver);
        if (changingMatcherName == null || changingPattern == null) {
            return NoMatcher.INSTANCE;
        }
        PatternMatcher matcher = abstractResolver.getSettings().getMatcher(changingMatcherName);
        if (matcher == null) {
            throw new IllegalStateException("unknown matcher '" + changingMatcherName
                    + "'. It is set as changing matcher in " + this);
        }
        return matcher.getMatcher(changingPattern);
    }
}
