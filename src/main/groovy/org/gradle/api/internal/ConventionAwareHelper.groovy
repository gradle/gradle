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

package org.gradle.api.internal

import org.gradle.api.InvalidUserDataException

/**
 * I would love to have this as a mixin. But Groovy does not support them yet.
 *
 * @author Hans Dockter
 */
class ConventionAwareHelper {
    def convention

    def source

    Map conventionMapping = [:]
    Map conventionMappingCache = [:]

    ConventionAwareHelper(def source) {
        this.source = source
    }

    def convention(def convention, Map conventionMapping) {
        this.convention = convention
        this.conventionMapping = conventionMapping
        source
    }

    def conventionMapping(Map mapping) {
        mapping.keySet().each {String propertyName ->
            if (!(source.getMetaClass().hasProperty(source, propertyName))) {
                throw new InvalidUserDataException("You can't map a property that does not exists: propertyName=$propertyName")
            }
        }
        this.conventionMapping.putAll(mapping)
        source
    }

    def getValue(String propertyName) {
        def value = source.getMetaClass().getProperty(source, propertyName)
        if (!value && getConventionMapping().keySet().contains(propertyName)) {
            if (!conventionMappingCache.keySet().contains(propertyName)) {
                conventionMappingCache[propertyName] = conventionMapping[propertyName]?.call(convention)
            }
            value = conventionMappingCache[propertyName]
        }
        value
    }
}
