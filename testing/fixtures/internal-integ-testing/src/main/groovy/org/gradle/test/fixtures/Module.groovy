/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.test.fixtures

import org.gradle.test.fixtures.gradle.VariantMetadataSpec

/**
 * Represents a module in a repository.
 */
interface Module {
    Module publish()
    Module publishWithChangedContent()
    Module withModuleMetadata()
    Module withSignature(@DelegatesTo(value = File, strategy = Closure.DELEGATE_FIRST) Closure<?> signer)

    String getGroup()
    String getModule()
    String getVersion()

    /**
     * Returns the Gradle module metadata file of this module
     */
    ModuleArtifact getModuleMetadata()
    GradleModuleMetadata getParsedModuleMetadata()

    Module withVariant(String name, @DelegatesTo(value=VariantMetadataSpec.class, strategy = Closure.DELEGATE_FIRST) groovy.lang.Closure<?> action)

    Map<String, String> getAttributes()

    Module withoutDefaultVariants()
}
