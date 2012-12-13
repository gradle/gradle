/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.result

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import spock.lang.Specification

class DependencyResultPrinterSpec extends Specification {
    def "prints requested version if no version was selected"() {
        def dependency = new SimpleUnresolvedDependencyResult(
                requested: new DefaultModuleVersionSelector("group", "name", "version"),
                from: new SimpleResolvedModuleVersionResult(id: new DefaultModuleVersionIdentifier("group", "origin", "version")),
                failure: new Exception()
        )

        expect:
        DependencyResultPrinter.print(dependency) == "group:name:version"
    }

    static class SimpleResolvedModuleVersionResult implements ResolvedModuleVersionResult {
        ModuleVersionIdentifier id
        Set<? extends DependencyResult> dependencies = []
        Set<? extends DependencyResult> dependents = []
        ModuleVersionSelectionReason selectionReason
    }

    static class SimpleUnresolvedDependencyResult implements UnresolvedDependencyResult {
        ModuleVersionSelector requested
        ResolvedModuleVersionResult selected
        ResolvedModuleVersionResult from
        Exception failure
    }
}
