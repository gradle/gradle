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

import org.gradle.api.Project

class ProjectDeduper {

    def moduleNameDeduper = new ModuleNameDeduper()

    void dedupe(Collection<Project> projects, Closure createDeduplicationTarget) {
        //Deduper acts on first-come first-served basis.
        //Therefore it's better if the inputs are sorted that first items are least wanted to be prefixed
        //Hence I'm sorting by nesting level:
        def sorted = projects.sort { (it.parent == null)? 0 : it.path.count(":") }
        def deduplicationTargets = sorted.collect({ createDeduplicationTarget(it) })
        moduleNameDeduper.dedupe(deduplicationTargets)
    }
}