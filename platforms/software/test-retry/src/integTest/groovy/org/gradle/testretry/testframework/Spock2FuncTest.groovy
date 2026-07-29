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
package org.gradle.testretry.testframework

import spock.lang.IgnoreIf

@IgnoreIf(
    value = { jvm.java21Compatible },
    reason = "Current version of spock2-groovy4 does not support Java 21 or above"
)
class Spock2FuncTest extends SpockBaseJunit5FuncTest {

    @Override
    boolean isRerunsParameterizedMethods() {
        true
    }

    @Override
    boolean canTargetInheritedMethods() {
        true
    }

    @Override
    protected String beforeClassErrorTestMethodName() {
        "initializationError"
    }

    @Override
    protected String afterClassErrorTestMethodName() {
        "executionError"
    }

    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                implementation '${spock2Dependency()}'
                // Since Gradle 9, the JUnit platform launcher is no longer provided by Gradle.
                testRuntimeOnly '${junitPlatformLauncherDependency()}'
            }
            test {
                useJUnitPlatform()
            }
        """
    }

    @Override
    protected String contextualTestExtension() {
        """
            package acme

            import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
            import org.spockframework.runtime.model.SpecInfo

            class ContextualTestExtension extends AbstractAnnotationDrivenExtension<ContextualTest> {

                @Override
                void visitSpecAnnotation(ContextualTest annotation, SpecInfo spec) {

                    spec.features.each { feature ->
                        feature.reportIterations = true
                        if (feature.parameterized) {
                            def currentNameProvider = feature.iterationNameProvider
                            feature.iterationNameProvider = {
                                def defaultName = currentNameProvider != null ? currentNameProvider.getName(it) : feature.name
                                defaultName + " [suffix]"
                            }
                        } else {
                            feature.displayName += " [suffix]"
                        }
                    }
                }
            }
        """
    }
}
