/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.testing.execution.fork;

import java.security.Permission;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;

public class TestPolicy extends Policy {
    private final Collection<ClassLoader> systemClassLoaders = new ArrayList<ClassLoader>();
    private final Policy policy;

    public TestPolicy(Policy policy) {
        this.policy = policy;

        ClassLoader thisClassLoader = getClass().getProtectionDomain().getClassLoader();
        systemClassLoaders.add(thisClassLoader);

        ClassLoader bootstrapClassLoader = thisClassLoader.getClass().getProtectionDomain().getClassLoader();
        systemClassLoaders.add(bootstrapClassLoader);
    }

    @Override
    public boolean implies(ProtectionDomain protectionDomain, final Permission permission) {
        boolean ok;
        if (systemClassLoaders.contains(protectionDomain.getClassLoader())) {
            ok = true;
        } else {
            ok = policy.implies(protectionDomain, permission);
        }
        return ok;
    }

    @Override
    public void refresh() {
        policy.refresh();
    }
}
