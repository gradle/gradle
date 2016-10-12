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

package org.gradle.internal.serialize;

import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.io.ClassLoaderObjectInputStream;

import java.io.IOException;
import java.io.InputStream;

public class ExceptionReplacingObjectInputStream extends ClassLoaderObjectInputStream {
    private Transformer<Object, Object> objectTransformer = new Transformer<Object, Object>() {
        @Override
        public Object transform(Object o) {
            try {
                return doResolveObject(o);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    };

    public ExceptionReplacingObjectInputStream(InputStream inputSteam, ClassLoader classLoader) throws IOException {
        super(inputSteam, classLoader);
        enableResolveObject(true);
    }

    public final Transformer<ExceptionReplacingObjectInputStream, InputStream> getObjectInputStreamCreator() {
        return new Transformer<ExceptionReplacingObjectInputStream, InputStream>() {
            @Override
            public ExceptionReplacingObjectInputStream transform(InputStream inputStream) {
                try {
                    return createNewInstance(inputStream);
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        };
    }

    protected ExceptionReplacingObjectInputStream createNewInstance(InputStream inputStream) throws IOException {
        return new ExceptionReplacingObjectInputStream(inputStream, getClassLoader());
    }

    @Override
    protected final Object resolveObject(Object obj) throws IOException {
        return getObjectTransformer().transform(obj);
    }

    protected Object doResolveObject(Object obj) throws IOException {
        if (obj instanceof TopLevelExceptionPlaceholder) {
            return ((ExceptionPlaceholder) obj).read(getClassNameTransformer(), getObjectInputStreamCreator());
        }
        return obj;
    }

    protected final Transformer<Class<?>, String> getClassNameTransformer() {
        return new Transformer<Class<?>, String>() {
            @Override
            public Class<?> transform(String type) {
                try {
                    return lookupClass(type);
                } catch (ClassNotFoundException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        };
    }

    protected Class<?> lookupClass(String type) throws ClassNotFoundException {
        return getClassLoader().loadClass(type);
    }

    public Transformer<Object, Object> getObjectTransformer() {
        return objectTransformer;
    }

    public void setObjectTransformer(Transformer<Object, Object> objectTransformer) {
        this.objectTransformer = objectTransformer;
    }
}
