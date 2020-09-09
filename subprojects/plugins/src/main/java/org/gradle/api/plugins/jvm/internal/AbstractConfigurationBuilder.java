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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

abstract class AbstractConfigurationBuilder<T extends AbstractConfigurationBuilder<T>> {
    final String name;
    final JvmPluginServices jvmEcosystemUtilities;
    final ConfigurationContainer configurations;
    String description;
    List<Object> extendsFrom;
    Action<? super JvmEcosystemAttributesDetails> attributesRefiner;

    public AbstractConfigurationBuilder(String name,
                                        JvmPluginServices jvmEcosystemUtilities,
                                        ConfigurationContainer configurations) {
        this.name = name;
        this.jvmEcosystemUtilities = jvmEcosystemUtilities;
        this.configurations = configurations;
    }

    abstract Configuration build();

    @Nullable
    protected Configuration[] buildExtendsFrom() {
        if (extendsFrom == null) {
            return null;
        }
        Configuration[] out = new Configuration[extendsFrom.size()];
        int i=0;
        for (Object obj : extendsFrom) {
            if (obj instanceof Configuration) {
                out[i] = (Configuration) obj;
            } else if (obj instanceof Provider) {
                out[i] = Cast.<Provider<Configuration>>uncheckedCast(obj).get();
            } else {
                throw new IllegalStateException("Unexpected configuration provider type: " + obj);
            }
            i++;
        }
        return out;
    }

    public T withDescription(String description) {
        this.description = description;
        return Cast.uncheckedCast(this);
    }

    public T extendsFrom(Configuration... parentConfigurations) {
        if (extendsFrom == null) {
            extendsFrom = Lists.newArrayListWithExpectedSize(parentConfigurations.length);
        }
        Collections.addAll(extendsFrom, parentConfigurations);
        return Cast.uncheckedCast(this);
    }

    public final T extendsFrom(List<Provider<Configuration>> parentConfigurations) {
        if (extendsFrom == null) {
            extendsFrom = Lists.newArrayListWithExpectedSize(parentConfigurations.size());
        }
        extendsFrom.addAll(parentConfigurations);
        return Cast.uncheckedCast(this);
    }

    protected T attributes(Action<? super JvmEcosystemAttributesDetails> refiner) {
        this.attributesRefiner = refiner;
        return Cast.uncheckedCast(this);
    }
}
