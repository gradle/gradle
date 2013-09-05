package org.gradle.model.dsl.internal

import org.gradle.model.internal.DefaultModelRegistry
import org.gradle.model.internal.ModelRegistryBackedModelRules
import spock.lang.Specification

class GroovyModelDslTest extends Specification {

    def modelRegistry = new DefaultModelRegistry()
    def modelRules = new ModelRegistryBackedModelRules(modelRegistry)
    def modelDsl = new GroovyModelDsl(modelRules)

    def "can add rules via dsl"() {
        given:
        modelRules.register("foo.bar", [])

        when:
        modelDsl.foo.bar {
            add 1
        }

        then:
        modelRegistry.get("foo.bar", List) == [1]

    }

}
