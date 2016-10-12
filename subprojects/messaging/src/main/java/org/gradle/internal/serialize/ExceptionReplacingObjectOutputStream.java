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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public class ExceptionReplacingObjectOutputStream extends ObjectOutputStream {
    private Transformer<Object, Object> objectTransformer = new Transformer<Object, Object>() {
        @Override
        public Object transform(Object obj) {
            try {
                return doReplaceObject(obj);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    };

    public ExceptionReplacingObjectOutputStream(OutputStream outputSteam) throws IOException {
        super(outputSteam);
        enableReplaceObject(true);
    }

    public final Transformer<ExceptionReplacingObjectOutputStream, OutputStream> getObjectOutputStreamCreator() {
        return new Transformer<ExceptionReplacingObjectOutputStream, OutputStream>() {
            @Override
            public ExceptionReplacingObjectOutputStream transform(OutputStream outputStream) {
                try {
                    return createNewInstance(outputStream);
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        };
    }

    protected ExceptionReplacingObjectOutputStream createNewInstance(OutputStream outputStream) throws IOException {
        return new ExceptionReplacingObjectOutputStream(outputStream);
    }

    @Override
    protected final Object replaceObject(Object obj) throws IOException {
        return getObjectTransformer().transform(obj);
    }

    protected Object doReplaceObject(Object obj) throws IOException {
        if (obj instanceof Throwable) {
            return new TopLevelExceptionPlaceholder((Throwable) obj, getObjectOutputStreamCreator());
        }
        return obj;
    }

    public Transformer<Object, Object> getObjectTransformer() {
        return objectTransformer;
    }

    public void setObjectTransformer(Transformer<Object, Object> objectTransformer) {
        this.objectTransformer = objectTransformer;
    }
}
