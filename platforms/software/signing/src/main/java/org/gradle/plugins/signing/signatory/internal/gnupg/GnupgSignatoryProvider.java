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
package org.gradle.plugins.signing.signatory.internal.gnupg;

import org.gradle.api.Project;
import org.gradle.plugins.signing.signatory.SignatoryProvider;
import org.gradle.plugins.signing.signatory.internal.ConfigurableSignatoryProvider;
import org.gradle.security.internal.gnupg.BaseGnupgSignatoryProvider;
import org.gradle.security.internal.gnupg.GnupgSignatory;

import java.util.Arrays;

/**
 * A {@link SignatoryProvider} of {@link GnupgSignatory} instances.
 *
 * @since 4.5
 */
public class GnupgSignatoryProvider extends BaseGnupgSignatoryProvider implements ConfigurableSignatoryProvider<GnupgSignatory> {

    @Override
    public void createSignatoryFor(Project project, String name, Object[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("Invalid args (" + name + ": " + Arrays.toString(args) + ")");
        }
        addSignatory(project, name);
    }

    @SuppressWarnings("unused") // invoked by Groovy
    public GnupgSignatory propertyMissing(String signatoryName) {
        return getSignatory(signatoryName);
    }

}
