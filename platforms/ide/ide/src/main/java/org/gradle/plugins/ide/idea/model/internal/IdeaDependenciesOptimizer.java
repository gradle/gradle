/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugins.ide.idea.model.internal;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.ModuleDependency;
import org.gradle.plugins.ide.idea.model.ModuleLibrary;
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;


/**
 * Minimizes a set of IDEA dependencies based on knowledge about how IDEA handles compilation and runtime of main and test classes:
 *
 * <ul>
 * <li> COMPILE dependencies are visible everywhere. </li>
 * <li> PROVIDED dependencies are visible both when compiling main and test code as well as when running tests (but not when running main). </li>
 * <li> RUNTIME dependencies are visible when running main and test code.</li>
 * <li> TEST dependencies are visible when compiling and running tests.</li>
 * </ul>
 *
 * This means we can do the following simplifications:
 *
 * <ul>
 * <li>If a dependency is in COMPILE, we can remove it everywhere else.</li>
 * <li>If a dependency is PROVIDED, we don't need it in TEST. </li>
 * <li>If a dependency is in RUNTIME and PROVIDED, we can hoist it up to COMPILE.</li>
 * </ul>
 *
 * This results is much closer to what a user would do by hand. Having less dependencies also makes IntelliJ faster.
 */
class IdeaDependenciesOptimizer {
    public void optimizeDeps(Collection<Dependency> deps) {
        Multimap<Object, GeneratedIdeaScope> scopesByDependencyKey = collectScopesByDependency(deps);
        optimizeScopes(scopesByDependencyKey);
        applyScopesToDependencies(deps, scopesByDependencyKey);
    }

    private Multimap<Object, GeneratedIdeaScope> collectScopesByDependency(Collection<Dependency> deps) {
        Multimap<Object, GeneratedIdeaScope> scopesByDependencyKey = MultimapBuilder.hashKeys().enumSetValues(GeneratedIdeaScope.class).build();
        for (Dependency dep : deps) {
            scopesByDependencyKey.put(getKey(dep), GeneratedIdeaScope.nullSafeValueOf(dep.getScope()));
        }
        return scopesByDependencyKey;
    }

    private void optimizeScopes(Multimap<Object, GeneratedIdeaScope> scopesByDependencyKey) {
        for (Map.Entry<Object, Collection<GeneratedIdeaScope>> entry : scopesByDependencyKey.asMap().entrySet()) {
            optimizeScopes(entry.getValue());
        }
    }

    private void applyScopesToDependencies(Collection<Dependency> deps, Multimap<Object, GeneratedIdeaScope> scopesByDependencyKey) {
        for (Iterator<Dependency> iterator = deps.iterator(); iterator.hasNext();) {
            applyScopeToNextDependency(iterator, scopesByDependencyKey);
        }
    }

    private void applyScopeToNextDependency(Iterator<Dependency> iterator, Multimap<Object, GeneratedIdeaScope> scopesByDependencyKey) {
        Dependency dep = iterator.next();
        Object key = getKey(dep);
        Collection<GeneratedIdeaScope> ideaScopes = scopesByDependencyKey.get(key);
        if (ideaScopes.isEmpty()) {
            iterator.remove();
        } else {
            GeneratedIdeaScope scope = ideaScopes.iterator().next();
            dep.setScope(scope.name());
            scopesByDependencyKey.remove(key, scope);
        }
    }

    private Object getKey(Dependency dep) {
        if (dep instanceof ModuleDependency) {
            return ((ModuleDependency) dep).getName();
        } else if (dep instanceof SingleEntryModuleLibrary) {
            return ((SingleEntryModuleLibrary) dep).getLibraryFile();
        }  else if (dep instanceof ModuleLibrary) {
            return ((ModuleLibrary)dep).getClasses();
        } else {
            throw new IllegalArgumentException("Unsupported type: " + dep.getClass().getName());
        }
    }

    private void optimizeScopes(Collection<GeneratedIdeaScope> ideaScopes) {
        boolean isRuntime = ideaScopes.contains(GeneratedIdeaScope.RUNTIME);
        boolean isProvided = ideaScopes.contains(GeneratedIdeaScope.PROVIDED);
        boolean isCompile = ideaScopes.contains(GeneratedIdeaScope.COMPILE);

        if (isProvided) {
            ideaScopes.remove(GeneratedIdeaScope.TEST);
        }

        if (isRuntime && isProvided) {
            ideaScopes.add(GeneratedIdeaScope.COMPILE);
            isCompile = true;
        }

        if (isCompile) {
            ideaScopes.remove(GeneratedIdeaScope.TEST);
            ideaScopes.remove(GeneratedIdeaScope.RUNTIME);
            ideaScopes.remove(GeneratedIdeaScope.PROVIDED);
        }
    }
}
