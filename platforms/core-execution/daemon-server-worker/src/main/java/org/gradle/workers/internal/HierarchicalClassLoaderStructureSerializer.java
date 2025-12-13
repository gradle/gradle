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

import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

public class HierarchicalClassLoaderStructureSerializer implements Serializer<HierarchicalClassLoaderStructure> {
    private static final byte ROOT = (byte) 0;
    private static final byte HAS_PARENT = (byte) 1;

    private static final byte FILTERING_SPEC = (byte) 0;
    private static final byte VISITABLE_URL_CLASSLOADER_SPEC = (byte) 1;

    private final FilteringClassLoaderSpecSerializer filteringClassLoaderSpecSerializer = new FilteringClassLoaderSpecSerializer();
    private final VisitableURLClassLoaderSpecSerializer visitableURLClassLoaderSpecSerializer = new VisitableURLClassLoaderSpecSerializer();

    @Override
    public void write(Encoder encoder, HierarchicalClassLoaderStructure classLoaderStructure) throws Exception {
        if (classLoaderStructure.getParent() == null) {
            encoder.writeByte(ROOT);
        } else {
            encoder.writeByte(HAS_PARENT);
            write(encoder, classLoaderStructure.getParent());
        }

        if (classLoaderStructure.getSpec() instanceof FilteringClassLoader.Spec) {
            encoder.writeByte(FILTERING_SPEC);
            filteringClassLoaderSpecSerializer.write(encoder, (FilteringClassLoader.Spec) classLoaderStructure.getSpec());
        } else if (classLoaderStructure.getSpec() instanceof VisitableURLClassLoader.Spec) {
            encoder.writeByte(VISITABLE_URL_CLASSLOADER_SPEC);
            visitableURLClassLoaderSpecSerializer.write(encoder, (VisitableURLClassLoader.Spec) classLoaderStructure.getSpec());
        }
    }

    @Override
    public HierarchicalClassLoaderStructure read(Decoder decoder) throws Exception {
        byte parentTag = decoder.readByte();
        HierarchicalClassLoaderStructure parent;
        switch (parentTag) {
            case ROOT:
                parent = null;
                break;
            case HAS_PARENT:
                parent = read(decoder);
                break;
            default:
                throw new IllegalArgumentException("Unexpected payload type.");
        }

        byte specTag = decoder.readByte();
        ClassLoaderSpec spec;
        switch (specTag) {
            case FILTERING_SPEC:
                spec = filteringClassLoaderSpecSerializer.read(decoder);
                break;
            case VISITABLE_URL_CLASSLOADER_SPEC:
                spec = visitableURLClassLoaderSpecSerializer.read(decoder);
                break;
            default:
                throw new IllegalArgumentException("Unexpected payload type.");
        }

        return new HierarchicalClassLoaderStructure(spec, parent);
    }
}
