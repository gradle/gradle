/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publication;

import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;

public class DefaultIvyPublicationIdentity implements IvyPublicationIdentity {
    private Module delegate;
    private String organisation;
    private String module;
    private String revision;

    public DefaultIvyPublicationIdentity(Module delegate) {
        this.delegate = delegate;
    }

    public DefaultIvyPublicationIdentity(String organisation, String module, String revision) {
        this.organisation = organisation;
        this.module = module;
        this.revision = revision;
    }

    @Override
    public String getOrganisation() {
        return organisation != null ? organisation : (delegate != null ? delegate.getGroup() : null);
    }

    @Override
    public void setOrganisation(String organisation) {
        this.organisation = organisation;
    }

    @Override
    public String getModule() {
        return module != null ? module : (delegate != null ? delegate.getName() : null);
    }

    @Override
    public void setModule(String module) {
        this.module = module;
    }

    @Override
    public String getRevision() {
        return revision != null ? revision : (delegate != null ? delegate.getVersion() : null);
    }

    @Override
    public void setRevision(String revision) {
        this.revision = revision;
    }
}
