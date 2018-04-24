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

package org.gradle.api.publish.maven.internal.publication;

import org.gradle.api.Action;
import org.gradle.api.publish.maven.MavenPomDistributionManagement;
import org.gradle.api.publish.maven.MavenPomRelocation;
import org.gradle.internal.reflect.Instantiator;

public class DefaultMavenPomDistributionManagement implements MavenPomDistributionManagement {
    private final Instantiator instantiator;
    private MavenPomRelocation relocation;

    public DefaultMavenPomDistributionManagement(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public void relocation(Action<? super MavenPomRelocation> action) {
        if (relocation == null) {
            relocation = instantiator.newInstance(DefaultMavenPomRelocation.class);
        }
        action.execute(relocation);
    }

    @Override
    public MavenPomRelocation getRelocation() {
        return relocation;
    }
}
