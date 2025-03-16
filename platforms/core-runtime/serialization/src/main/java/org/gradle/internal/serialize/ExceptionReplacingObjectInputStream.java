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

import org.gradle.internal.InternalTransformer;
import org.gradle.internal.UncheckedException;

import java.io.IOException;
import java.io.InputStream;

public class ExceptionReplacingObjectInputStream extends ClassLoaderObjectInputStream {
    private InternalTransformer<Object, Object> objectTransformer = new InternalTransformer<Object, Object>() {
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

    public final InternalTransformer<ExceptionReplacingObjectInputStream, InputStream> getObjectInputStreamCreator() {
        return new InternalTransformer<ExceptionReplacingObjectInputStream, InputStream>() {
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

    protected final InternalTransformer<Class<?>, String> getClassNameTransformer() {
        return new InternalTransformer<Class<?>, String>() {
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

    public InternalTransformer<Object, Object> getObjectTransformer() {
        return objectTransformer;
    }

    public void setObjectTransformer(InternalTransformer<Object, Object> objectTransformer) {
        this.objectTransformer = objectTransformer;
    }
}
