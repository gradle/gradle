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
package org.gradle.api.plugins.internal.support;

import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JvmEcosystemUtilities;

import static org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE;

class DefaultJvmEcosystemAttributesDetails implements JvmEcosystemUtilities.JvmEcosystemAttributesDetails {
    private final ObjectFactory objectFactory;
    private final AttributeContainerInternal attributes;

    public DefaultJvmEcosystemAttributesDetails(ObjectFactory objectFactory, AttributeContainerInternal attributes) {
        this.objectFactory = objectFactory;
        this.attributes = attributes;
    }

    @Override
    public JvmEcosystemUtilities.JvmEcosystemAttributesDetails apiUsage() {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API));
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JvmEcosystemAttributesDetails runtimeUsage() {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JvmEcosystemAttributesDetails library() {
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.LIBRARY));
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JvmEcosystemAttributesDetails platform() {
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.REGULAR_PLATFORM));
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JvmEcosystemAttributesDetails enforcedPlatform() {
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.ENFORCED_PLATFORM));
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JvmEcosystemAttributesDetails documentation(String docsType) {
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.DOCUMENTATION));
        attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(DocsType.class, docsType));
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JvmEcosystemAttributesDetails withExternalDependencies() {
        attributes.attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JvmEcosystemAttributesDetails withEmbeddedDependencies() {
        attributes.attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EMBEDDED));
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JvmEcosystemAttributesDetails withShadowedDependencies() {
        attributes.attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.SHADOWED));
        return this;
    }

    @Override
    public JvmEcosystemUtilities.JvmEcosystemAttributesDetails asJar() {
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.JAR));
        return this;
    }
}
