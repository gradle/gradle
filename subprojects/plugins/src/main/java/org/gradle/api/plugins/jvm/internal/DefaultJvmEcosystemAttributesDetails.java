/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.VerificationType;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

import static org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE;

public class DefaultJvmEcosystemAttributesDetails implements JvmEcosystemAttributesDetails {
    private final ObjectFactory objectFactory;
    private final AttributeContainerInternal attributes;

    @Inject
    public DefaultJvmEcosystemAttributesDetails(ObjectFactory objectFactory, AttributeContainerInternal attributes) {
        this.objectFactory = objectFactory;
        this.attributes = attributes;
    }

    @Override
    public JvmEcosystemAttributesDetails apiUsage() {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API));
        return this;
    }

    @Override
    public JvmEcosystemAttributesDetails runtimeUsage() {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        return this;
    }

    @Override
    public JvmEcosystemAttributesDetails library() {
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.LIBRARY));
        return this;
    }

    @Override
    public JvmEcosystemAttributesDetails library(String elementsType) {
        library();
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, elementsType));
        return this;
    }

    @Override
    public JvmEcosystemAttributesDetails platform() {
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.REGULAR_PLATFORM));
        return this;
    }

    @Override
    public JvmEcosystemAttributesDetails documentation(String docsType) {
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.DOCUMENTATION));
        attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(DocsType.class, docsType));
        return this;
    }

    @Override
    public JvmEcosystemAttributesDetails withExternalDependencies() {
        attributes.attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
        return this;
    }

    @Override
    public JvmEcosystemAttributesDetails withEmbeddedDependencies() {
        attributes.attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EMBEDDED));
        return this;
    }

    @Override
    public JvmEcosystemAttributesDetails withShadowedDependencies() {
        attributes.attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.SHADOWED));
        return this;
    }

    @Override
    public JvmEcosystemAttributesDetails asJar() {
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.JAR));
        return this;
    }

    @Override
    public JvmEcosystemAttributesDetails withTargetJvmVersion(int version) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, version);
        return this;
    }

    @Override
    public JvmEcosystemAttributesDetails preferStandardJVM() {
        attributes.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objectFactory.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM));
        return this;
    }

    @Override
    public JvmEcosystemAttributesDetails asSources() {
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.VERIFICATION));
        attributes.attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objectFactory.named(VerificationType.class, VerificationType.MAIN_SOURCES));
        return this;
    }
}
