package org.gradle.api.internal.artifacts.result

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import spock.lang.Specification

class DependencyResultPrinterTest extends Specification {
    def "prints requested version if no version was selected"() {
        def dependency = new DefaultUnresolvedDependencyResult(
                new DefaultModuleVersionSelector("group", "name", "version"),
                null,
                new SimpleResolvedModuleVersionResult(id: new DefaultModuleVersionIdentifier("group", "origin", "version")),
                new Exception()
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
}
