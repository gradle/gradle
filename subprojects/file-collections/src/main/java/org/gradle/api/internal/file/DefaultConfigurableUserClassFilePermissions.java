/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.file;

import javax.inject.Inject;

public abstract class DefaultConfigurableUserClassFilePermissions extends AbstractUserClassFilePermissions implements ConfigurableUserClassFilePermissionsInternal {

    @Inject
    public DefaultConfigurableUserClassFilePermissions(int unixNumeric) {
        setRead(isRead(unixNumeric));
        setWrite(isWrite(unixNumeric));
        setExecute(isExecute(unixNumeric));
    }

    @Override
    public void unix(String unixSymbolic) {
        setRead(isRead(unixSymbolic));
        setWrite(isWrite(unixSymbolic));
        setExecute(isExecute(unixSymbolic));
    }

    @Override
    public void unix(int unixNumeric) {
        setRead(isRead(unixNumeric));
        setWrite(isWrite(unixNumeric));
        setExecute(isExecute(unixNumeric));
    }
}
