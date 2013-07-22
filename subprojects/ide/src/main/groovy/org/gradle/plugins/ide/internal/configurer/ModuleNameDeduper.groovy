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
package org.gradle.plugins.ide.internal.configurer

/**
 * Able to deduplicate names. Useful for IDE plugins to make sure module names (IDEA) or project names (Eclipse) are unique.
 * <p>
 */
class ModuleNameDeduper {

    void dedupe(Collection<DeduplicationTarget> targets) {
        def allNames = []
        targets.each { target ->
            def name = target.candidateNames.find { !allNames.contains(it) }
            if (name) {
                allNames << name
                target.updateModuleName(name)
            }
        }
    }
}