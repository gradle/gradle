package org.gradle.api.internal.artifacts.result

import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import spock.lang.Specification

class DependencyResultPrinterTest extends Specification {
    def "prints requested version if no version was selected"() {
        def dependency = new DefaultUnresolvedDependencyResult(
                new DefaultModuleVersionSelector("group", "name", "version"),
                null,
                new DefaultResolvedModuleVersionResult(new DefaultModuleVersionIdentifier("group", "origin", "version")),
                new Exception()
        )

        expect:
        DependencyResultPrinter.print(dependency) == "group:name:version"
    }
}
