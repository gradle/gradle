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
package org.gradle.api.plugins.internal;

import com.google.common.collect.Lists;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.ecosystem.Ecosystem;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.Factory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DefaultSoftwareComponentFactory implements SoftwareComponentFactory {

    private final AttributesSchemaInternal attributesSchema;

    public DefaultSoftwareComponentFactory(AttributesSchemaInternal attributesSchema) {
        this.attributesSchema = attributesSchema;
    }

    @Override
    public AdhocComponentWithVariants adhoc(String name) {
        return new DefaultAdhocSoftwareComponent(name, new Factory<List<Ecosystem>>() {
            @Override
            public List<Ecosystem> create() {
                List<Ecosystem> ecosystems = Lists.newArrayList(attributesSchema.getEcosystems());
                Collections.sort(ecosystems, new EcosystemComparator());
                return ecosystems;
            }
        });
    }

    private static class EcosystemComparator implements Comparator<Ecosystem> {

        @Override
        public int compare(Ecosystem o1, Ecosystem o2) {
            return o1.getName().compareTo(o2.getName());
        }
    }
}
