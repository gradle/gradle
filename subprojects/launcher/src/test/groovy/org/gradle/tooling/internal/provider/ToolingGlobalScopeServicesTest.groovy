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

package org.gradle.tooling.internal.provider

import org.gradle.cache.internal.CacheFactory
import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.service.DefaultServiceRegistry
import spock.lang.Specification

class ToolingGlobalScopeServicesTest extends Specification {
    def classClassLoaderFactory = Stub(ClassLoaderFactory)
    def cacheFactory = Stub(CacheFactory)

    def services = DefaultServiceRegistry.create(new ToolingGlobalScopeServices(), new Object() {
        ClassLoaderFactory createClassLoaderFactory() {
            return classClassLoaderFactory
        }

        CacheFactory createCacheFactory() {
            return cacheFactory
        }
    })

    def "provides a PayloadSerializer"() {
        expect:
        services.get(PayloadSerializer) instanceof PayloadSerializer
    }
}
