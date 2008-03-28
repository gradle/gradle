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

package org.gradle.api.internal.dependencies

import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.gradle.api.InvalidUserDataException
import org.gradle.util.GradleUtil

/**
 * @author Hans Dockter
 */
class ResolverContainer {
    ResolverFactory resolverFactory = new ResolverFactory()

    List resolverNames = []

    Map resolvers = [:]

    DependencyResolver add(def userDescription, Closure configureClosure = null) {
        addInternal(userDescription, configureClosure) {String resolverName ->
            resolverNames << resolverName
        }
    }

    DependencyResolver addBefore(def userDescription, String afterResolverName, Closure configureClosure = null) {
        if (!afterResolverName) {throw new InvalidUserDataException(
                'You must specify userDescription and afterResolverName')}
        if (!resolvers[afterResolverName]) {throw new InvalidUserDataException(
                "Resolver $afterResolverName does not exists!")}
        addInternal(userDescription, configureClosure) {String resolverName ->
            resolverNames.add(resolverNames.indexOf(afterResolverName), resolverName)
        }
    }

    DependencyResolver addAfter(def userDescription, String beforeResolverName, Closure configureClosure = null) {
        if (!beforeResolverName) {throw new InvalidUserDataException(
                'You must specify userDescription and beforeResolverName')}
        if (!resolvers[beforeResolverName]) {throw new InvalidUserDataException(
                "Resolver $beforeResolverName does not exists!")}
        addInternal(userDescription, configureClosure) {String resolverName ->
            int insertPos = resolverNames.indexOf(beforeResolverName) + 1
            insertPos == resolverNames.size() ? resolverNames.add(resolverName) : resolverNames.add(insertPos, resolverName)
        }
    }

    DependencyResolver addFirst(def userDescription, Closure configureClosure = null) {
        if (!userDescription) {throw new InvalidUserDataException('You must specify userDescription')}
        addInternal(userDescription, configureClosure) {String resolverName ->
            resolverNames.size() == 0 ? resolverNames.add(resolverName) : resolverNames.add(0, resolverName)
        }
    }

    private DependencyResolver addInternal(def userDescription, Closure configureClosure, Closure orderClosure) {
        if (!userDescription) {throw new InvalidUserDataException('You must specify userDescription')}
        DependencyResolver resolver = resolverFactory.createResolver(userDescription)
        GradleUtil.configure(configureClosure, resolver)
        resolvers[resolver.name] = resolver
        orderClosure(resolver.name)
        resolver
    }


    RepositoryResolver getAt(String name) {
        resolvers[name]
    }

    List getResolverList() {
        resolverNames.collect {resolvers[it]}
    }
}
