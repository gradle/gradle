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
package org.gradle.api.internal.artifacts.verification.serializer;

abstract class DependencyVerificationXmlTags {
    static final String ALSO_TRUST = "also-trust";
    static final String ARTIFACT = "artifact";
    static final String COMPONENT = "component";
    static final String COMPONENTS = "components";
    static final String CONFIG = "configuration";
    static final String ENABLED = "enabled";
    static final String FILE = "file";
    static final String GROUP = "group";
    static final String ID = "id";
    static final String IGNORED_KEY = "ignored-key";
    static final String IGNORED_KEYS = "ignored-keys";
    static final String KEY_SERVER = "key-server";
    static final String KEY_SERVERS = "key-servers";
    static final String NAME = "name";
    static final String ORIGIN = "origin";
    static final String PGP = "pgp";
    static final String REASON = "reason";
    static final String REGEX = "regex";
    static final String TRUST = "trust";
    static final String TRUSTED_ARTIFACTS = "trusted-artifacts";
    static final String TRUSTED_KEY = "trusted-key";
    static final String TRUSTED_KEYS = "trusted-keys";
    static final String TRUSTING = "trusting";
    static final String URI = "uri";
    static final String VALUE = "value";
    static final String VERIFICATION_METADATA = "verification-metadata";
    static final String VERIFY_METADATA = "verify-metadata";
    static final String VERIFY_SIGNATURES = "verify-signatures";
    static final String VERSION = "version";
}
