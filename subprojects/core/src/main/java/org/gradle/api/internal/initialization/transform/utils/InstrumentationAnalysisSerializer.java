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

package org.gradle.api.internal.initialization.transform.utils;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.initialization.transform.InstrumentationArtifactMetadata;
import org.gradle.internal.serialize.HierarchicalNameSerializer;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.SetSerializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

public class InstrumentationAnalysisSerializer {

    private final StringInterner stringInterner;

    public InstrumentationAnalysisSerializer(StringInterner stringInterner) {
        this.stringInterner = stringInterner;
    }

    public void writeMetadata(File output, InstrumentationArtifactMetadata metadata) {
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(Files.newOutputStream(output.toPath()))) {
            encoder.writeString(metadata.getArtifactName());
            encoder.writeString(metadata.getArtifactHash());
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types map to a file: " + output, e);
        }
    }

    public InstrumentationArtifactMetadata readMetadata(File input) {
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(input.toPath()))) {
            return new InstrumentationArtifactMetadata(decoder.readString(), decoder.readString());
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize metadata from a file: " + input, e);
        }
    }

    public void writeTypesMap(File output, Map<String, Set<String>> typesMap) {
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(Files.newOutputStream(output.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            MapSerializer<String, Set<String>> serializer = new MapSerializer<>(nameSerializer, new SetSerializer<>(nameSerializer));
            serializer.write(encoder, typesMap);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types map to a file: " + output, e);
        }
    }

    public Map<String, Set<String>> readTypesMap(File input) {
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(input.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            MapSerializer<String, Set<String>> serializer = new MapSerializer<>(nameSerializer, new SetSerializer<>(nameSerializer));
            return serializer.read(decoder);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize types map from a file: " + input, e);
        }
    }

    public void writeTypes(File output, Set<String> types) {
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(Files.newOutputStream(output.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            SetSerializer<String> serializer = new SetSerializer<>(nameSerializer);
            serializer.write(encoder, types);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types to a file: " + output, e);
        }
    }

    public Set<String> readTypes(File input) {
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(input.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            SetSerializer<String> serializer = new SetSerializer<>(nameSerializer);
            return serializer.read(decoder);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize types from a file: " + input, e);
        }
    }

}
