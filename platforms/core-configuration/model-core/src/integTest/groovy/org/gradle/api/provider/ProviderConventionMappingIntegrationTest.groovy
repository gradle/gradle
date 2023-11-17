/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

// TODO: Also do this for FileCollection types eventually
class ProviderConventionMappingIntegrationTest extends AbstractIntegrationSpec {
    private void expectDocumentedFailure() {
        failure.assertHasDocumentedCause('Using internal convention mapping with a Provider backed property. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#convention_mapping')
    }

    def "emits deprecation warning when convention mapping is used with Provider"() {
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @Inject abstract ProviderFactory getProviderFactory()

                @Internal abstract Property<String> getOther()

                @Internal Provider<String> getFoo() {
                    return other;
                }

                @TaskAction
                void useIt() {
                    assert foo.get() == "foobar"
                }
            }
            tasks.register("mytask", MyTask) {
                conventionMapping.map("foo", { providerFactory.provider { "foobar" } })
                other.convention("other")
            }
        """

        expect:
        runAndFail 'mytask'
        expectDocumentedFailure()
    }

    def "convention mapping can be used with Property and an actual value"() {
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @Internal abstract Property<String> getFoo()
                @Internal abstract Property<String> getBar()

                @TaskAction
                void useIt() {
                    assert foo.get() == "foobar"
                }
            }
            tasks.register("mytask", MyTask) {
                conventionMapping.map("foo") { "foobar" }
            }
        """

        expect:
        succeeds 'mytask'
    }

    def "convention mapping cannot be used with Property and a Provider value"() {
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @Internal abstract Property<String> getFoo()
                @Internal abstract Property<String> getBar()

                @TaskAction
                void useIt() {
                    bar.get()
                }
            }
            tasks.register("mytask", MyTask) {
                conventionMapping.map("bar") { providers.provider { "foobar" } }
            }
        """

        expect:
        fails 'mytask'
        failureHasCause("Cannot get the value of task ':mytask' property 'bar' of type $String.name as the provider associated with this property returned a value of type $DefaultProvider.name.")
    }

    def "emits deprecation warning when convention mapping is used with MapProperty"() {
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @Internal abstract MapProperty<String, String> getFoo()

                @TaskAction
                void useIt() {
                    // convention mapping for MapProperty is already ignored
                    assert foo.get() == [other: "other"]
                }
            }
            tasks.register("mytask", MyTask) {
                conventionMapping.map("foo", { project.objects.mapProperty(String, String).convention([foobar: "foobar"]) })
                foo.convention([other: "other"])
            }
        """

        expect:
        runAndFail 'mytask'
        expectDocumentedFailure()
    }

    def "emits deprecation warning when convention mapping is used with ListProperty"() {
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @Internal abstract ListProperty<String> getFoo()

                @TaskAction
                void useIt() {
                    // convention mapping for ListProperty is already ignored
                    assert foo.get() == ["other"]
                }
            }
            tasks.register("mytask", MyTask) {
                conventionMapping.map("foo", { project.objects.listProperty(String).convention(["foobar"]) })
                foo.convention(["other"])
            }
        """

        expect:
        runAndFail 'mytask'
        expectDocumentedFailure()
    }

    def "can use file collection as convention for FileCollectionProperty"() {
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @InputFiles abstract FileCollectionProperty<FileCollection> getFoo()
                @Inject abstract ProjectLayout getProjectLayout()

                public MyTask() {
                    def fileCollection = projectLayout.files("file.txt")
                    foo.convention(fileCollection)
                }

                @TaskAction
                void useIt() {
                    assert foo.get().files == projectLayout.files("file2.txt").files
                }
            }
            tasks.register("mytask", MyTask) {
                foo.convention(objects.fileCollection().from(["file2.txt"]))
            }
        """

        expect:
        run 'mytask'
    }

    def "convention mapping works with Property in a ConventionTask"() {
        buildFile << """
            abstract class MyTask extends org.gradle.api.internal.ConventionTask {
                @Internal abstract Property<String> getFoo()

                @TaskAction
                void useIt() {
                    // convention mapping for Property is already ignored
                    assert foo.get() == "other"
                }
            }
            tasks.register("mytask", MyTask) {
                conventionMapping.map("foo", { project.objects.property(String).convention("foobar") })
                foo.convention("other")
            }
        """

        expect:
        succeeds 'mytask'
    }

    def "emits deprecation warning when convention mapping is used with Provider in domain object other than task"() {
        buildFile << """
            abstract class MyExtension {
                abstract Property<String> getOther()

                Provider<String> getFoo() {
                    return other
                }
            }

            extensions.create("myext", MyExtension)
            myext {
                conventionMapping.map("foo", { project.provider { "foobar" } })
                other.convention("other")
            }
        """

        expect:
        runAndFail 'help'
        expectDocumentedFailure()
    }
}
