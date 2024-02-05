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

package org.gradle.internal.snapshot.impl;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class IsolatedLambda implements Isolatable<Object> {
    private final LambdaMetadata metadata;
    private final ImmutableList<Isolatable<?>> isolatedArgs;

    public IsolatedLambda(LambdaMetadata metadata, ImmutableList<Isolatable<?>> isolatedArgs) {
        this.metadata = metadata;
        this.isolatedArgs = isolatedArgs;
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        throw new IllegalStateException("not implemented yet");
    }

    @Override
    public ValueSnapshot asSnapshot() {
        throw new IllegalStateException("not implemented yet");
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        throw new IllegalStateException("not implemented yet");
    }

    @Nullable
    @Override
    public Object isolate() {
        SerializedLambda serializedLambda = metadata.toSerializedLambda(capturedArgs());
        try {
            return readResolve(serializedLambda);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Object[] capturedArgs() {
        return isolatedArgs.stream().map(Isolatable::isolate).toArray();
    }

    private static Object readResolve(SerializedLambda serializedLambda) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method readResolve = serializedLambda.getClass().getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        return readResolve.invoke(serializedLambda);
    }
}
