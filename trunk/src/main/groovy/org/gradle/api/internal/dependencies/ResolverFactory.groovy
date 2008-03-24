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
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.gradle.api.InvalidUserDataException

/**
 * @author Hans Dockter
 */
class ResolverFactory {
    static final String MAVEN2_PATTERN = "[organisation]/[module]/[revision]/[module]-[revision].[ext]"
    
    DependencyResolver createResolver(def userDescription) {
        DependencyResolver result
        switch (userDescription.getClass()) {
            case String: result = createIBiblioResolver(userDescription, userDescription); break;
            case Map: result = createIBiblioResolver(userDescription.name, userDescription.url); break;
            case DependencyResolver: result = userDescription; break;
            default: throw new InvalidUserDataException('Illegal Resolver type')
        }
        result
    }

    IBiblioResolver createIBiblioResolver(String name, String root) {
        IBiblioResolver iBiblioResolver = new IBiblioResolver()
        iBiblioResolver.setUsepoms(true)
        iBiblioResolver.name = name
        iBiblioResolver.pattern = MAVEN2_PATTERN
        iBiblioResolver.root = root
        iBiblioResolver.m2compatible = true
        iBiblioResolver
    }
}
