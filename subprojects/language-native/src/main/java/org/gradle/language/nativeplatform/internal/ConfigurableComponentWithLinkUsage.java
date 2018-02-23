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

package org.gradle.language.nativeplatform.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.language.nativeplatform.ComponentWithLinkUsage;
import org.gradle.nativeplatform.Linkage;

import javax.annotation.Nullable;

/**
 * A configurable view of a component that has a link usage. This should become public in some form.
 */
public interface ConfigurableComponentWithLinkUsage extends ComponentWithLinkUsage, ComponentWithNames {
    Configuration getImplementationDependencies();

    @Nullable
    Linkage getLinkage();

    @Override
    Property<Configuration> getLinkElements();

    Provider<RegularFile> getLinkFile();

    AttributeContainer getLinkAttributes();
}
