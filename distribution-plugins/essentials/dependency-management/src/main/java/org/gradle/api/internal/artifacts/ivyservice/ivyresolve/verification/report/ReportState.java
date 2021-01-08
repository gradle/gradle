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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report;

import com.google.common.collect.Sets;

import java.util.Set;

class ReportState implements VerificationHighLevelErrors {
    private final Set<String> affectedFiles = Sets.newTreeSet();
    private boolean maybeCompromised;
    private boolean hasMissing;
    private boolean failedSignatures;
    private boolean hasUntrustedKeys;

    public void maybeCompromised() {
        maybeCompromised = true;
    }

    public void hasMissing() {
        hasMissing = true;
    }

    public void failedSignatures() {
        failedSignatures = true;
    }

    public void hasUntrustedKeys() {
        hasUntrustedKeys = true;
    }

    @Override
    public boolean isMaybeCompromised() {
        return maybeCompromised;
    }

    @Override
    public boolean hasFailedSignatures() {
        return failedSignatures;
    }

    @Override
    public boolean canSuggestWriteMetadata() {
        return (hasMissing || hasUntrustedKeys) && !maybeCompromised;
    }

    @Override
    public Set<String> getAffectedFiles() {
        return affectedFiles;
    }

    public void addAffectedFile(String file) {
        affectedFiles.add(file);
    }
}
