/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal;

import com.google.common.collect.Lists;
import org.gradle.initialization.MixInLegacyTypesClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.net.URL;
import java.util.List;

public class VisitableURLClassLoaderSpecSerializer implements Serializer<VisitableURLClassLoader.Spec> {
    private static final byte VISITABLE_URL_CLASSLOADER_SPEC = (byte) 0;
    private static final byte MIXIN_CLASSLOADER_SPEC = (byte) 1;

    @Override
    public void write(Encoder encoder, VisitableURLClassLoader.Spec spec) throws Exception {
        if (spec instanceof MixInLegacyTypesClassLoader.Spec) {
            encoder.writeByte(MIXIN_CLASSLOADER_SPEC);
        } else {
            encoder.writeByte(VISITABLE_URL_CLASSLOADER_SPEC);
        }

        encoder.writeString(spec.getName());
        encoder.writeInt(spec.getClasspath().size());
        for (URL url : spec.getClasspath()) {
            encoder.writeString(url.toString());
        }
    }

    @Override
    public VisitableURLClassLoader.Spec read(Decoder decoder) throws Exception {
        byte typeTag = decoder.readByte();
        String name = decoder.readString();
        List<URL> classpath = Lists.newArrayList();
        int classpathSize = decoder.readInt();
        for (int i=0; i<classpathSize; i++) {
            classpath.add(new URL(decoder.readString()));
        }

        switch(typeTag) {
            case VISITABLE_URL_CLASSLOADER_SPEC:
                return new VisitableURLClassLoader.Spec(name, classpath);
            case MIXIN_CLASSLOADER_SPEC:
                return new MixInLegacyTypesClassLoader.Spec(name, classpath);
            default:
                throw new IllegalArgumentException("Unexpected payload type.");
        }
    }
}
