/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.daemon.protocol;

import org.gradle.api.problems.AdditionalData;
import org.gradle.internal.InternalTransformer;
import org.gradle.internal.UncheckedException;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public class AdditionalDataReplacingObjectOutputStream extends ObjectOutputStream {
    //private final Class<?> classToBeReplaced;
    private final PayloadSerializer payloadSerializer;
    //    private final InternalTransformer<Object, Object> transformer;
    private InternalTransformer<Object, Object> objectTransformer = new InternalTransformer<Object, Object>() {
        @Override
        public Object transform(Object obj) {
            try {
                return doReplaceObject(obj);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    };

    public AdditionalDataReplacingObjectOutputStream(OutputStream outputStream, PayloadSerializer payloadSerializer) throws IOException {
        super(outputStream);
        this.payloadSerializer = payloadSerializer;
        enableReplaceObject(true);
    }

//    public final InternalTransformer<ObjectReplacingObjectOutputStream, OutputStream> getObjectOutputStreamCreator() {
//        return new InternalTransformer<ObjectReplacingObjectOutputStream, OutputStream>() {
//            @Override
//            public ObjectReplacingObjectOutputStream transform(OutputStream outputStream) {
//                try {
//                    return createNewInstance(outputStream);
//                } catch (IOException e) {
//                    throw UncheckedException.throwAsUncheckedException(e);
//                }
//            }
//        };
//    }
//
//    protected ObjectReplacingObjectOutputStream createNewInstance(OutputStream outputStream) throws IOException {
//        return new ObjectReplacingObjectOutputStream(outputStream, classToBeReplaced, transformer);
//    }

    @Override
    protected final Object replaceObject(Object obj) throws IOException {
        if (obj instanceof AdditionalData) {
            return new AdditionalDataPlaceHolder(payloadSerializer.serialize(obj));
        }
        return obj;
        //return getObjectTransformer().transform(obj);
    }

    protected Object doReplaceObject(Object obj) throws IOException {
        if (obj instanceof AdditionalDataPlaceHolder) {
            return objectTransformer.transform(obj);
        }
        return obj;
    }

    public InternalTransformer<Object, Object> getObjectTransformer() {
        return objectTransformer;
    }

    public void setObjectTransformer(InternalTransformer<Object, Object> objectTransformer) {
        this.objectTransformer = objectTransformer;
    }
}
