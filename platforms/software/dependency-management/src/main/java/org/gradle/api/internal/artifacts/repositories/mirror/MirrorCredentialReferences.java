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
package org.gradle.api.internal.artifacts.repositories.mirror;

public final class MirrorCredentialReferences {
    private final MirrorValueReference username;
    private final MirrorValueReference password;

    public MirrorCredentialReferences(MirrorValueReference username, MirrorValueReference password) {
        this.username = username;
        this.password = password;
    }

    public MirrorValueReference getUsername() {
        return username;
    }

    public MirrorValueReference getPassword() {
        return password;
    }
}
