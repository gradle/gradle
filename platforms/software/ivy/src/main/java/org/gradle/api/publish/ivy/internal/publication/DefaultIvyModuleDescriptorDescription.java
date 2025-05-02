/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publication;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.ivy.IvyModuleDescriptorDescription;

import javax.inject.Inject;

public class DefaultIvyModuleDescriptorDescription implements IvyModuleDescriptorDescription {

    private final Property<String> text;
    private final Property<String> homepage;

    @Inject
    public DefaultIvyModuleDescriptorDescription(ObjectFactory objectFactory) {
        text = objectFactory.property(String.class);
        homepage = objectFactory.property(String.class);
    }

    @Override
    public Property<String> getText() {
        return text;
    }

    @Override
    public Property<String> getHomepage() {
        return homepage;
    }
}
