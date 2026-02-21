/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugins.signing.signatory.internal.pgp;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/**
 * A helper service that ensures that only one copy of ASCII-armored key representation exists.
 */
public abstract class KeyDataInternService implements BuildService<BuildServiceParameters.None> {
    // The PgpSignatoryService holds references to these strings as cache keys, so we're not really leaking them - even without CC.
    // We can change this interner to weak if we change the lifetime of those strings later.
    private final Interner<String> interner = Interners.newStrongInterner();

    public String internKeyData(String keyData) {
        return interner.intern(keyData);
    }

    public static Provider<KeyDataInternService> obtain(Project project) {
        return project.getGradle().getSharedServices().registerIfAbsent(KeyDataInternService.class.getName(), KeyDataInternService.class);
    }
}
