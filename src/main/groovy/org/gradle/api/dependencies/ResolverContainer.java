/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.dependencies;

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.*;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.dependencies.LocalReposCacheHandler;
import org.gradle.api.internal.dependencies.ResolverFactory;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class ResolverContainer {
    private ResolverFactory resolverFactory = new ResolverFactory(new LocalReposCacheHandler());

    private List<String> resolverNames = new ArrayList<String>();

    private Map<String, DependencyResolver> resolvers = new HashMap<String, DependencyResolver>();

    public ResolverContainer() {
    }

    public ResolverContainer(LocalReposCacheHandler localReposCacheHandler) {
        resolverFactory = new ResolverFactory(localReposCacheHandler);
    }

    public DependencyResolver add(Object userDescription) {
        return add(userDescription, null);
    }

    public DependencyResolver add(Object userDescription, Closure configureClosure) {
        return addInternal(userDescription, configureClosure, new OrderAction() {
            public void apply(String resolverName) {
                resolverNames.add(resolverName);
            }
        });
    }

    public DependencyResolver addBefore(Object userDescription, final String afterResolverName) {
        return addBefore(userDescription, afterResolverName, null);
    }

    public DependencyResolver addBefore(Object userDescription, final String afterResolverName, Closure configureClosure) {
        if (!GUtil.isTrue(afterResolverName)) {
            throw new InvalidUserDataException(
                    "You must specify userDescription and afterResolverName");
        }
        if (!GUtil.isTrue(resolvers.get(afterResolverName))) {
            throw new InvalidUserDataException(
                    "Resolver $afterResolverName does not exists!");
        }
        return addInternal(userDescription, configureClosure, new OrderAction() {
            public void apply(String resolverName) {
                resolverNames.add(resolverNames.indexOf(afterResolverName), resolverName);
            }
        });
    }

    public DependencyResolver addAfter(Object userDescription, final String beforeResolverName) {
        return addAfter(userDescription, beforeResolverName, null);
    }

    public DependencyResolver addAfter(Object userDescription, final String beforeResolverName, Closure configureClosure) {
        if (!GUtil.isTrue(beforeResolverName)) {
            throw new InvalidUserDataException(
                    "You must specify userDescription and beforeResolverName");
        }
        if (!GUtil.isTrue(resolvers.get(beforeResolverName))) {
            throw new InvalidUserDataException(
                    "Resolver $beforeResolverName does not exists!");
        }
        return addInternal(userDescription, configureClosure, new OrderAction() {
            public void apply(String resolverName) {
                int insertPos = resolverNames.indexOf(beforeResolverName) + 1;
                if (insertPos == resolverNames.size()) {
                    resolverNames.add(resolverName);
                } else {
                    resolverNames.add(insertPos, resolverName);
                }
            }
        });
    }

    public DependencyResolver addFirst(Object userDescription) {
        return addFirst(userDescription, null);
    }

    public DependencyResolver addFirst(Object userDescription, Closure configureClosure) {
        if (!GUtil.isTrue(userDescription)) {
            throw new InvalidUserDataException("You must specify userDescription");
        }
        return addInternal(userDescription, configureClosure, new OrderAction() {
            public void apply(String resolverName) {
                if (resolverNames.size() == 0) {
                    resolverNames.add(resolverName);
                } else {
                    resolverNames.add(0, resolverName);
                }
            }
        });
    }

    private DependencyResolver addInternal(Object userDescription, Closure configureClosure, OrderAction orderAction) {
        if (!GUtil.isTrue(userDescription)) {
            throw new InvalidUserDataException("You must specify userDescription");
        }
        DependencyResolver resolver = resolverFactory.createResolver(userDescription);
        ConfigureUtil.configure(configureClosure, resolver);
        resolvers.put(resolver.getName(), resolver);
        orderAction.apply(resolver.getName());
        return resolver;
    }


    public DependencyResolver get(String name) {
        return resolvers.get(name);
    }

    public List<DependencyResolver> getResolverList() {
        List<DependencyResolver> returnedResolvers = new ArrayList<DependencyResolver>();
        for (String resolverName : resolverNames) {
            returnedResolvers.add(resolvers.get(resolverName));
        }
        return returnedResolvers;
    }

    public FileSystemResolver createFlatDirResolver(String name, File[] roots) {
        return resolverFactory.createFlatDirResolver(name, roots);
    }

    public AbstractResolver createMavenRepoResolver(String name, String root, String[] jarRepoUrls) {
        return resolverFactory.createMavenRepoResolver(name, root, jarRepoUrls);
    }

    private static interface OrderAction {
        void apply(String resolverName);
    }

    public ResolverFactory getResolverFactory() {
        return resolverFactory;
    }

    public void setResolverFactory(ResolverFactory resolverFactory) {
        this.resolverFactory = resolverFactory;
    }

    public List<String> getResolverNames() {
        return resolverNames;
    }

    public void setResolverNames(List<String> resolverNames) {
        this.resolverNames = resolverNames;
    }

    public Map<String, DependencyResolver> getResolvers() {
        return resolvers;
    }

    public void setResolvers(Map<String, DependencyResolver> resolvers) {
        this.resolvers = resolvers;
    }
}
