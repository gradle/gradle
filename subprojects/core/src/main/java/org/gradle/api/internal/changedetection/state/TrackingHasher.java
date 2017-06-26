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

package org.gradle.api.internal.changedetection.state;

import com.google.common.hash.Funnel;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;

import java.nio.charset.Charset;

public class TrackingHasher implements Hasher {
    private final Hasher delegate;
    private boolean modified;

    public TrackingHasher(Hasher delegate) {
        this.delegate = delegate;
    }

    @Override
    public Hasher putByte(byte b) {
        markModified();
        return delegate.putByte(b);
    }

    @Override
    public Hasher putBytes(byte[] bytes) {
        markModified();
        return delegate.putBytes(bytes);
    }

    @Override
    public Hasher putBytes(byte[] bytes, int off, int len) {
        markModified();
        return delegate.putBytes(bytes, off, len);
    }

    @Override
    public Hasher putShort(short s) {
        markModified();
        return delegate.putShort(s);
    }

    @Override
    public Hasher putInt(int i) {
        markModified();
        return delegate.putInt(i);
    }

    @Override
    public Hasher putLong(long l) {
        markModified();
        return delegate.putLong(l);
    }

    @Override
    public Hasher putFloat(float f) {
        markModified();
        return delegate.putFloat(f);
    }

    @Override
    public Hasher putDouble(double d) {
        markModified();
        return delegate.putDouble(d);
    }

    @Override
    public Hasher putBoolean(boolean b) {
        markModified();
        return delegate.putBoolean(b);
    }

    @Override
    public Hasher putChar(char c) {
        markModified();
        return delegate.putChar(c);
    }

    @Override
    public Hasher putUnencodedChars(CharSequence charSequence) {
        markModified();
        return delegate.putUnencodedChars(charSequence);
    }

    @Override
    public Hasher putString(CharSequence charSequence, Charset charset) {
        markModified();
        return delegate.putString(charSequence, charset);
    }

    @Override
    public <T> Hasher putObject(T instance, Funnel<? super T> funnel) {
        markModified();
        return delegate.putObject(instance, funnel);
    }

    private void markModified() {
        modified = true;
    }

    @Override
    public HashCode hash() {
        if (modified) {
            return delegate.hash();
        } else {
            return null;
        }
    }
}
