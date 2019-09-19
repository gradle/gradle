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

package org.gradle.api.internal.tasks.scala;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.scala.ScalaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.language.scala.internal.toolchain.DefaultScalaToolProvider;

import javax.inject.Inject;

public class DefaultScalaPluginExtension implements ScalaPluginExtension {
    private final Property<String> zincVersion;

    @Inject
    public DefaultScalaPluginExtension(ObjectFactory objectFactory) {
        this.zincVersion = objectFactory.property(String.class).value(DefaultScalaToolProvider.DEFAULT_ZINC_VERSION);
    }

    @Override
    public Property<String> getZincVersion() {
        return zincVersion;
    }
}
