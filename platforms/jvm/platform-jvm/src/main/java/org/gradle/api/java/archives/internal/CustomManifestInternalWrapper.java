/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.java.archives.internal;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.ManifestException;
import org.gradle.api.java.archives.ManifestMergeSpec;

import java.io.OutputStream;
import java.util.Map;

public class CustomManifestInternalWrapper implements ManifestInternal {
    private final Manifest delegate;
    private String contentCharset = DefaultManifest.DEFAULT_CONTENT_CHARSET;

    public CustomManifestInternalWrapper(Manifest delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getContentCharset() {
        return contentCharset;
    }

    @Override
    public void setContentCharset(String contentCharset) {
        this.contentCharset = contentCharset;
    }

    @Override
    public Manifest writeTo(OutputStream outputStream) {
        DefaultManifest.writeTo(this, outputStream, contentCharset);
        return this;
    }

    @Override
    public Attributes getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Map<String, Attributes> getSections() {
        return delegate.getSections();
    }

    @Override
    public Manifest attributes(Map<String, ?> attributes) throws ManifestException {
        delegate.attributes(attributes);
        return this;
    }

    @Override
    public Manifest attributes(Map<String, ?> attributes, String sectionName) throws ManifestException {
        delegate.attributes(attributes, sectionName);
        return this;
    }

    @Override
    public Manifest getEffectiveManifest() {
        return delegate.getEffectiveManifest();
    }

    @Override
    public Manifest writeTo(Object path) {
        delegate.writeTo(path);
        return this;
    }

    @Override
    public Manifest from(Object... mergePath) {
        delegate.from(mergePath);
        return this;
    }

    @Override
    public Manifest from(Object mergePath, Closure<?> closure) {
        delegate.from(mergePath, closure);
        return this;
    }

    @Override
    public Manifest from(Object mergePath, Action<ManifestMergeSpec> action) {
        delegate.from(mergePath, action);
        return this;
    }
}
