/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.integtests.resolve.rules

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Issue

class AdditionalVariantsMetadataRulesIntegrationTest extends AbstractModuleDependencyResolveTest {

    ResolveTestFixture resolve

    def setup() {
        resolve = new ResolveTestFixture(buildFile, "samples")
        resolve.prepare()
    }

    @Override
    boolean isJavaEcosystem() {
        false
    }

    @Issue("https://github.com/gradle/gradle/issues/20145")
    def "component metadata rules can add variants even if no derivation rules are present"() {
        given: "a published module"
        repository {
            group("org") {
                module("a") {
                    version("1") {
                        withModule {
                            undeclaredArtifact(type: 'jar', classifier: "my-samples")
                        }
                        withoutGradleMetadata()
                    }
                }
            }
        }

        and: "a component metadata rule that adds a variant"
        file("buildSrc/build.gradle") << """
            plugins {
                id("java")
            }
        """
        file("buildSrc/src/main/java/AddVariantRule.java") << """
            package com.example;

            import org.gradle.api.artifacts.ComponentMetadataContext;
            import org.gradle.api.artifacts.ComponentMetadataRule;
            import org.gradle.api.artifacts.ModuleVersionIdentifier;
            import org.gradle.api.attributes.Category;
            import org.gradle.api.attributes.DocsType;
            import org.gradle.api.model.ObjectFactory;

            import javax.inject.Inject;

            public class AddVariantRule implements ComponentMetadataRule {
                private final ObjectFactory objectFactory;

                @Inject
                public AddVariantRule(ObjectFactory objectFactory) {
                    this.objectFactory = objectFactory;
                }

                @Override
                public void execute(ComponentMetadataContext componentMetadataContext) {
                    Category category = objectFactory.named(Category.class, Category.DOCUMENTATION);
                    DocsType docsType = objectFactory.named(DocsType.class, "my-classifier");

                    ModuleVersionIdentifier id = componentMetadataContext.getDetails().getId();
                    componentMetadataContext.getDetails().addVariant("my-samples", variantMetadata -> {
                        variantMetadata.attributes(attributeContainer -> {
                            attributeContainer.attribute(Category.CATEGORY_ATTRIBUTE, category);
                            attributeContainer.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, docsType);
                        });
                        variantMetadata.withFiles(variantFiles -> {
                            variantFiles.addFile(id.getName() + "-" + id.getVersion() + "-my-samples.jar");
                        });
                    });
                }
            }
        """

        when: "a project does not apply the ecosystem plugins and therefore doesn't have derivation rules"
        buildFile << """
            configurations {
                create("samples") {
                    canBeResolved = true
                    canBeConsumed = false
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION))
                        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, "my-classifier"))
                    }
                }
            }

            dependencies {
                samples("org:a:1")
            }

            dependencies.components.all(com.example.AddVariantRule)
        """

        then: "the variant added by the rule should be present and should be chosen as the resolution result"
        repositoryInteractions {
            group("org") {
                module("a") {
                    version("1") {
                        expectGetMetadata()
                        allowAll()
                    }
                }
            }
        }
        succeeds 'checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1") {
                    variant("my-samples", [
                        "org.gradle.category": "documentation",
                        "org.gradle.docstype": "my-classifier",
                        "org.gradle.status": GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release'
                    ]).artifact(classifier: "my-samples")
                }
            }
        }
    }

    def getRepo() {
        return maven(file('repo'))
    }
}
