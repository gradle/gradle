/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class OutgoingVariantsMutationIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            def usage = Attribute.of('usage', String)
            def format = Attribute.of('format', String)
            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(usage)
                    }
                }
                configurations { compile { attributes.attribute(usage, 'for-compile') } }
            }
        """
    }

    @ToBeFixedForConfigurationCache(because = "Task uses the Configuration API")
    def "cannot mutate outgoing variants after configuration is resolved"() {
        given:
        buildFile << """

        configurations {
            compile {
                attributes.attribute(usage, 'for compile')
                outgoing {
                    artifact file('lib1.jar')
                    variants {
                        classes {
                            attributes.attribute(format, 'classes-dir')
                            artifact file('classes')
                        }
                        jar {
                            attributes.attribute(format, 'classes-jar')
                            artifact file('lib.jar')
                        }
                        sources {
                            attributes.attribute(format, 'source-jar')
                            artifact file('source.zip')
                        }
                    }
                }
            }
        }
        task mutateBeforeResolve {
            doLast {
                def classes = configurations.compile.outgoing.variants['classes']
                classes.attributes.attribute(format, 'classes2')
            }
        }
        task mutateAfterResolve {
            doLast {
                configurations.compile.resolve()
                def classes = configurations.compile.outgoing.variants['classes']
                classes.attributes.attribute(format, 'classes-dir')
            }
        }
        """

        when:
        run 'mutateBeforeResolve'

        then:
        noExceptionThrown()

        when:
        fails("mutateAfterResolve")

        then:
        failure.assertHasCause "Cannot change attributes of dependency configuration ':compile' after it has been resolved"
    }

    @ToBeFixedForConfigurationCache(because = "Task uses the Configuration API")
    def "cannot add outgoing variants after configuration is resolved"() {
        given:
        buildFile << """

        configurations {
            compile {
                attributes.attribute(usage, 'for compile')
                outgoing {
                    artifact file('lib1.jar')
                    variants {
                        classes {
                            attributes.attribute(format, 'classes-dir')
                            artifact file('classes')
                        }
                    }
                }
            }
        }
        task mutateBeforeResolve {
            doLast {
                configurations.compile.outgoing.variants {
                    jar {
                        attributes.attribute(format, 'classes-jar')
                        artifact file('lib.jar')
                    }
                }
            }
        }
        task mutateAfterResolve {
            doLast {
                configurations.compile.resolve()
                configurations.compile.outgoing.variants {
                    sources {
                        attributes.attribute(format, 'source-jar')
                        artifact file('source.zip')
                    }
                }
            }
        }
        """

        when:
        run 'mutateBeforeResolve'

        then:
        noExceptionThrown()

        when:
        fails("mutateAfterResolve")

        then:
        failure.assertHasCause "Cannot create variant 'sources' after dependency configuration ':compile' has been resolved"
    }
}
