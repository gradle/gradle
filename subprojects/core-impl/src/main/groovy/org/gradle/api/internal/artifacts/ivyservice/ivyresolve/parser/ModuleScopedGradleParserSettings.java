/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.matcher.*;
import org.apache.ivy.plugins.resolver.DependencyResolver;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * ParserSettings that control the scope of searches carried out during parsing.
 * If the parser asks for a resolver for the currently resolving revision, the resolver scope is only the repository where the module was resolved.
 * If the parser asks for a resolver for a different revision, the resolver scope is all repositories.
 */
public class ModuleScopedGradleParserSettings implements GradleParserSettings {
    private final DependencyResolver mainResolver;
    private final DependencyResolver moduleResolver;
    private final ModuleRevisionId moduleRevisionId;
    private final Map<String, String> properties = new HashMap<String, String>();
    private final String defaultStatus;

    public ModuleScopedGradleParserSettings(DependencyResolver mainResolver, DependencyResolver moduleResolver, ModuleRevisionId moduleRevisionId, String defaultStatus) {
        this.defaultStatus = defaultStatus;
        this.mainResolver = mainResolver;
        this.moduleResolver = moduleResolver;
        this.moduleRevisionId = moduleRevisionId;
        populateProperties();
    }

    private void populateProperties() {
        String baseDir = new File(".").getAbsolutePath();
        properties.put("ivy.default.settings.dir", baseDir);
        properties.put("ivy.basedir", baseDir);

        for (String property : System.getProperties().stringPropertyNames()) {
            properties.put(property, System.getProperty(property));
        }
    }

    public ModuleRevisionId getCurrentRevisionId() {
        return moduleRevisionId;
    }

    public DependencyResolver getResolver(ModuleRevisionId mRevId) {
        if (mRevId.equals(moduleRevisionId)) {
            return moduleResolver;
        }
        return mainResolver;
    }

    public String substitute(String value) {
        return IvyPatternHelper.substituteVariables(value, properties);
    }

    public PatternMatcher getMatcher(String matcherName) {
        return PatternMatchers.INSTANCE.getMatcher(matcherName);
    }

    public String getDefaultStatus() {
        return defaultStatus;
    }

    private static class PatternMatchers {
        public static final PatternMatchers INSTANCE = new PatternMatchers();

        private final Map<String, PatternMatcher> matchers = new HashMap<String, PatternMatcher>();

        private PatternMatchers() {
           addMatcher(ExactPatternMatcher.INSTANCE);
           addMatcher(RegexpPatternMatcher.INSTANCE);
           addMatcher(ExactOrRegexpPatternMatcher.INSTANCE);
           addMatcher(GlobPatternMatcher.INSTANCE);
       }

       private void addMatcher(PatternMatcher instance) {
           matchers.put(instance.getName(), instance);
       }

        public PatternMatcher getMatcher(String name) {
            return matchers.get(name);
        }
    }
}
