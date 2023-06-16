/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationTestFor
import spock.lang.Issue

class TaskInputIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/24979")
    @ValidationTestFor(ValidationProblemId.UNSUPPORTED_VALUE_TYPE)
    def "cannot annotate type 'java.net.URL' with @Input"() {

        executer.beforeExecute {
            executer.noDeprecationChecks()
            executer.withArgument("-Dorg.gradle.internal.max.validation.errors=20")
        }

        given:
        buildFile << """
            interface NestedBean {
                @Input
                Property<URL> getNested()
            }

            abstract class TaskWithInput extends DefaultTask {

                private final NestedBean nested = project.objects.newInstance(NestedBean.class)

                @Input
                URL getDirect() { null }

                @Input
                Provider<URL> getProviderInput() { propertyInput }

                @Input
                abstract Property<URL> getPropertyInput();

                @Input
                abstract SetProperty<URL> getSetPropertyInput();

                @Input
                abstract ListProperty<URL> getListPropertyInput();

                @Input
                abstract MapProperty<String, URL> getMapPropertyInput();

                @Nested
                abstract NestedBean getNestedInput();
            }

            tasks.register('verify', TaskWithInput) {
                def url = new URI("https://www.foo.com").toURL()
                propertyInput.set(url)
                setPropertyInput.set([url])
                listPropertyInput.set([url])
                mapPropertyInput.put("some", url)
                nestedInput.nested.set(url)
                doLast {
                    println(setPropertyInput.get())
                }
            }
        """

        when:
        fails "verify"

        then:
        failureDescriptionStartsWith("Some problems were found with the configuration of task ':verify' (type 'TaskWithInput').")
        failureDescriptionContains("Type 'TaskWithInput' property 'direct' has @Input annotation used on type 'java.net.URL' or a property of this type.")
        failureDescriptionContains("Type 'TaskWithInput' property 'providerInput' has @Input annotation used on type 'java.net.URL' or a property of this type.")
        failureDescriptionContains("Type 'TaskWithInput' property 'propertyInput' has @Input annotation used on type 'java.net.URL' or a property of this type.")
        failureDescriptionContains("Type 'TaskWithInput' property 'setPropertyInput' has @Input annotation used on type 'java.net.URL' or a property of this type.")
        failureDescriptionContains("Type 'TaskWithInput' property 'listPropertyInput' has @Input annotation used on type 'java.net.URL' or a property of this type.")
        failureDescriptionContains("Type 'TaskWithInput' property 'mapPropertyInput' has @Input annotation used on type 'java.net.URL' or a property of this type.")
        failureDescriptionContains("Type 'TaskWithInput' property 'nestedInput.nested' has @Input annotation used on type 'java.net.URL' or a property of this type.")

        // Reason
        failureDescriptionContains("Type 'java.net.URL' cannot be annotated with @Input because Java Serialization can lead to the same object of this type being detected as different by Gradle")

        // Solution
        failureDescriptionContains("Possible solution: Use type 'java.net.URI' instead.")

        // Documentation
        failureDescriptionContains(documentationRegistry.getDocumentationRecommendationFor("information", "validation_problems", "unsupported_value_type"))
    }
}
