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
import org.gradle.api.internal.initialization.transform.InstrumentationArtifactAnalysis;
import org.gradle.api.internal.initialization.transform.InstrumentationArtifactMetadata;
import org.gradle.internal.serialize.HierarchicalNameSerializer;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.SetSerializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import java.io.EOFException;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

public class InstrumentationAnalysisSerializer {

    private final StringInterner stringInterner;

    public InstrumentationAnalysisSerializer(StringInterner stringInterner) {
        this.stringInterner = stringInterner;
    }

    public void writeAnalysis(
        File output,
        InstrumentationArtifactMetadata metadata,
        Map<String, Set<String>> typesMap,
        Set<String> types
    ) {
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(Files.newOutputStream(output.toPath()))) {
            writeMetadata(metadata, encoder);
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            writeTypesMap(typesMap, encoder, nameSerializer);
            writeTypes(types, encoder, nameSerializer);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types map to a file: " + output, e);
        }
    }

    public InstrumentationArtifactAnalysis readFullAnalysis(File output) {
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(output.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            return new InstrumentationArtifactAnalysis(
                readMetadata(decoder),
                readTypesMap(decoder, nameSerializer),
                readTypes(decoder, nameSerializer)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types map to a file: " + output, e);
        }
    }

    public InstrumentationArtifactMetadata readMetadataFromAnalysis(File input) {
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(input.toPath()))) {
            return readMetadata(decoder);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize analysis from a file: " + input, e);
        }
    }

    public Map<String, Set<String>> readTypeHierarchyFromAnalysis(File input) {
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(input.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            readMetadata(decoder);
            return readTypesMap(decoder, nameSerializer);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize analysis from a file: " + input, e);
        }
    }

    public void writeMetadata(File output, InstrumentationArtifactMetadata metadata) {
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(Files.newOutputStream(output.toPath()))) {
            writeMetadata(metadata, encoder);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types map to a file: " + output, e);
        }
    }

    private static void writeMetadata(InstrumentationArtifactMetadata metadata, KryoBackedEncoder encoder) {
        encoder.writeString(metadata.getArtifactName());
        encoder.writeString(metadata.getArtifactHash());
    }

    public InstrumentationArtifactMetadata readMetadata(File input) {
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(input.toPath()))) {
            return readMetadata(decoder);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize metadata from a file: " + input, e);
        }
    }

    private static InstrumentationArtifactMetadata readMetadata(KryoBackedDecoder decoder) throws EOFException {
        return new InstrumentationArtifactMetadata(decoder.readString(), decoder.readString());
    }

    public void writeTypesMap(File output, Map<String, Set<String>> typesMap) {
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(Files.newOutputStream(output.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            writeTypesMap(typesMap, encoder, nameSerializer);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types map to a file: " + output, e);
        }
    }

    private void writeTypesMap(Map<String, Set<String>> typesMap, KryoBackedEncoder encoder, HierarchicalNameSerializer nameSerializer) throws Exception {
        MapSerializer<String, Set<String>> serializer = new MapSerializer<>(nameSerializer, new SetSerializer<>(nameSerializer));
        serializer.write(encoder, typesMap);
    }

    public Map<String, Set<String>> readTypesMap(File input) {
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(input.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            return readTypesMap(decoder, nameSerializer);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize types map from a file: " + input, e);
        }
    }

    private Map<String, Set<String>> readTypesMap(KryoBackedDecoder decoder, HierarchicalNameSerializer nameSerializer) throws Exception {
        MapSerializer<String, Set<String>> serializer = new MapSerializer<>(nameSerializer, new SetSerializer<>(nameSerializer));
        return serializer.read(decoder);
    }

    public void writeTypes(File output, Set<String> types) {
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(Files.newOutputStream(output.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            writeTypes(types, encoder, nameSerializer);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types to a file: " + output, e);
        }
    }

    private void writeTypes(Set<String> types, KryoBackedEncoder encoder, HierarchicalNameSerializer nameSerializer) throws Exception {
        SetSerializer<String> serializer = new SetSerializer<>(nameSerializer);
        serializer.write(encoder, types);
    }

    public Set<String> readTypes(File input) {
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(input.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            return readTypes(decoder, nameSerializer);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize types from a file: " + input, e);
        }
    }

    private static Set<String> readTypes(KryoBackedDecoder decoder, HierarchicalNameSerializer nameSerializer) throws Exception {
        SetSerializer<String> serializer = new SetSerializer<>(nameSerializer);
        return serializer.read(decoder);
    }

}
