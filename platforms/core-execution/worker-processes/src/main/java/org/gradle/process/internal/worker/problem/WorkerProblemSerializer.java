/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.process.internal.worker.problem;

import org.gradle.api.problems.internal.DefaultProblem;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SerializerRegistry;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class WorkerProblemSerializer {
    public static SerializerRegistry create() {
        DefaultSerializerRegistry registry = new DefaultSerializerRegistry(false);

        registry.register(DefaultProblem.class, new ProblemSerializer());
        return registry;
    }

    private static class ProblemSerializer implements Serializer<DefaultProblem> {

        @Override
        public void write(Encoder encoder, DefaultProblem problem) throws Exception {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(encoder.getOutputStream());
            objectOutputStream.writeObject(problem);
        }

        @Override
        public DefaultProblem read(Decoder decoder) throws Exception {
            ObjectInputStream objectInputStream = new ObjectInputStream(decoder.getInputStream());
            return (DefaultProblem) objectInputStream.readObject();
        }

    }
}
