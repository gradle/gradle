/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExcludeRuleConverter;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.changedetection.state.CoercingStringValueSnapshot;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.component.external.model.MutableComponentVariant;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.gson.stream.JsonToken.*;

public class ModuleMetadataParser {
    public static final String FORMAT_VERSION = "0.4";
    private final ImmutableAttributesFactory attributesFactory;
    private final NamedObjectInstantiator instantiator;
    private final ExcludeRuleConverter excludeRuleConverter;

    public ModuleMetadataParser(ImmutableAttributesFactory attributesFactory, ImmutableModuleIdentifierFactory moduleIdentifierFactory, NamedObjectInstantiator instantiator) {
        this.attributesFactory = attributesFactory;
        this.instantiator = instantiator;
        this.excludeRuleConverter = new DefaultExcludeRuleConverter(moduleIdentifierFactory);
    }

    public void parse(final LocallyAvailableExternalResource resource, final MutableModuleComponentResolveMetadata metadata) {
        resource.withContent(new Transformer<Void, InputStream>() {
            @Override
            public Void transform(InputStream inputStream) {
                try {
                    JsonReader reader = new JsonReader(new InputStreamReader(inputStream, "utf-8"));
                    reader.beginObject();
                    if (reader.peek() != JsonToken.NAME) {
                        throw new RuntimeException("Module metadata should contain a 'formatVersion' value.");
                    }
                    String name = reader.nextName();
                    if (!name.equals("formatVersion")) {
                        throw new RuntimeException(String.format("The 'formatVersion' value should be the first value in a module metadata. Found '%s' instead.", name));
                    }
                    if (reader.peek() != JsonToken.STRING) {
                        throw new RuntimeException("The 'formatVersion' value should have a string value.");
                    }
                    String version = reader.nextString();
                    if (!version.equals(FORMAT_VERSION)) {
                        throw new RuntimeException(String.format("Unsupported format version '%s' specified in module metadata. This version of Gradle supports format version %s only.", version, FORMAT_VERSION));
                    }
                    consumeTopLevelElements(reader, metadata);
                    return null;
                } catch (Exception e) {
                    throw new MetaDataParseException("module metadata", resource, e);
                }
            }
        });
    }

    private void consumeTopLevelElements(JsonReader reader, MutableModuleComponentResolveMetadata metadata) throws IOException {
        while (reader.peek() != END_OBJECT) {
            String name = reader.nextName();
            if ("variants".equals(name)) {
                consumeVariants(reader, metadata);
            } else if ("component".equals(name)) {
                consumeComponent(reader, metadata);
            } else {
                consumeAny(reader);
            }
        }
    }

    private void consumeComponent(JsonReader reader, MutableModuleComponentResolveMetadata metadata) throws IOException {
        reader.beginObject();
        while (reader.peek() != END_OBJECT) {
            String name = reader.nextName();
            if ("attributes".equals(name)) {
                metadata.setAttributes(consumeAttributes(reader));
            } else {
                consumeAny(reader);
            }
        }
        reader.endObject();
    }

    private void consumeVariants(JsonReader reader, MutableModuleComponentResolveMetadata metadata) throws IOException {
        reader.beginArray();
        while (reader.peek() != JsonToken.END_ARRAY) {
            consumeVariant(reader, metadata);
        }
        reader.endArray();
    }

    private void consumeVariant(JsonReader reader, MutableModuleComponentResolveMetadata metadata) throws IOException {
        reader.beginObject();
        String variantName = null;
        ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
        List<ModuleFile> files = Collections.emptyList();
        List<ModuleDependency> dependencies = Collections.emptyList();
        List<ModuleDependencyConstraint> dependencyConstraints = Collections.emptyList();
        List<VariantCapability> capabilities = Collections.emptyList();
        while (reader.peek() != END_OBJECT) {
            String name = reader.nextName();
            if (name.equals("name")) {
                variantName = reader.nextString();
            } else if (name.equals("attributes")) {
                attributes = consumeAttributes(reader);
            } else if (name.equals("files")) {
                files = consumeFiles(reader);
            } else if (name.equals("dependencies")) {
                dependencies = consumeDependencies(reader);
            } else if (name.equals("dependencyConstraints")) {
                dependencyConstraints = consumeDependencyConstraints(reader);
            }  else if (name.equals("capabilities")) {
                capabilities = consumeCapabilities(reader);
            } else if (name.equals("available-at")) {
                // For now just collect this as another dependency
                // TODO - collect this information and merge the metadata from the other module
                dependencies = consumeVariantLocation(reader);
            } else {
                consumeAny(reader);
            }
        }
        reader.endObject();

        MutableComponentVariant variant = metadata.addVariant(variantName, attributes);
        for (ModuleFile file : files) {
            variant.addFile(file.name, file.uri);
        }
        for (ModuleDependency dependency : dependencies) {
            variant.addDependency(dependency.group, dependency.module, dependency.versionConstraint, dependency.excludes, dependency.reason, dependency.attributes);
        }
        for (ModuleDependencyConstraint dependencyConstraint : dependencyConstraints) {
            variant.addDependencyConstraint(dependencyConstraint.group, dependencyConstraint.module, dependencyConstraint.versionConstraint, dependencyConstraint.reason, dependencyConstraint.attributes);
        }
        for (VariantCapability capability : capabilities) {
            variant.addCapability(capability.group, capability.name, capability.version);
        }
    }

    private List<ModuleDependency> consumeVariantLocation(JsonReader reader) throws IOException {
        String group = null;
        String module = null;
        String version = null;
        reader.beginObject();
        while (reader.peek() != END_OBJECT) {
            String name = reader.nextName();
            if (name.equals("group")) {
                group = reader.nextString();
            } else if (name.equals("module")) {
                module = reader.nextString();
            } else if (name.equals("version")) {
                version = reader.nextString();
            } else {
                consumeAny(reader);
            }
        }
        reader.endObject();
        return ImmutableList.of(new ModuleDependency(group, module, new DefaultImmutableVersionConstraint(version), ImmutableList.<ExcludeMetadata>of(), null, ImmutableAttributes.EMPTY));
    }

    private List<ModuleDependency> consumeDependencies(JsonReader reader) throws IOException {
        List<ModuleDependency> dependencies = new ArrayList<ModuleDependency>();
        reader.beginArray();
        while (reader.peek() != END_ARRAY) {
            reader.beginObject();
            String group = null;
            String module = null;
            String reason = null;
            ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
            VersionConstraint version = DefaultImmutableVersionConstraint.of();
            ImmutableList<ExcludeMetadata> excludes = ImmutableList.of();
            while (reader.peek() != END_OBJECT) {
                String name = reader.nextName();
                if (name.equals("group")) {
                    group = reader.nextString();
                } else if (name.equals("module")) {
                    module = reader.nextString();
                } else if (name.equals("version")) {
                    version = consumeVersion(reader);
                } else if (name.equals("excludes")) {
                    excludes = consumeExcludes(reader);
                } else if (name.equals("reason")) {
                    reason = reader.nextString();
                } else if (name.equals("attributes")) {
                    attributes = consumeAttributes(reader);
                } else {
                    consumeAny(reader);
                }
            }
            dependencies.add(new ModuleDependency(group, module, version, excludes, reason, attributes));
            reader.endObject();
        }
        reader.endArray();
        return dependencies;
    }

    private List<VariantCapability> consumeCapabilities(JsonReader reader) throws IOException {
        ImmutableList.Builder<VariantCapability> capabilities = ImmutableList.builder();
        reader.beginArray();
        while (reader.peek() != END_ARRAY) {
            reader.beginObject();
            String group = null;
            String name = null;
            String version = null;
            while (reader.peek() != END_OBJECT) {
                String val = reader.nextName();
                if (val.equals("group")) {
                    group = reader.nextString();
                } else if (val.equals("name")) {
                    name = reader.nextString();
                } else if (val.equals("version")) {
                    version = reader.nextString();
                }
            }
            capabilities.add(new VariantCapability(group, name, version));
            reader.endObject();
        }
        reader.endArray();
        return capabilities.build();
    }

    private List<ModuleDependencyConstraint> consumeDependencyConstraints(JsonReader reader) throws IOException {
        List<ModuleDependencyConstraint> dependencies = new ArrayList<ModuleDependencyConstraint>();
        reader.beginArray();
        while (reader.peek() != END_ARRAY) {
            reader.beginObject();
            String group = null;
            String module = null;
            String reason = null;
            VersionConstraint version = null;
            ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
            while (reader.peek() != END_OBJECT) {
                String name = reader.nextName();
                if (name.equals("group")) {
                    group = reader.nextString();
                } else if (name.equals("module")) {
                    module = reader.nextString();
                } else if (name.equals("version")) {
                    version = consumeVersion(reader);
                }  else if (name.equals("reason")) {
                    reason = reader.nextString();
                } else if (name.equals("attributes")) {
                    attributes = consumeAttributes(reader);
                } else {
                    consumeAny(reader);
                }
            }
            dependencies.add(new ModuleDependencyConstraint(group, module, version, reason, attributes));
            reader.endObject();
        }
        reader.endArray();
        return dependencies;
    }

    private ImmutableVersionConstraint consumeVersion(JsonReader reader) throws IOException {
        reader.beginObject();
        String preferredVersion = "";
        String strictVersion = "";
        List<String> rejects = Lists.newArrayList();
        while (reader.peek() != END_OBJECT) {
            String cst = reader.nextName();
            if ("prefers".equals(cst)) {
                preferredVersion = reader.nextString();
            } else if ("strictly".equals(cst)) {
                strictVersion = reader.nextString();
            } else if ("rejects".equals(cst)) {
                reader.beginArray();
                while (reader.peek() != END_ARRAY) {
                    rejects.add(reader.nextString());
                }
                reader.endArray();
            }
        }
        reader.endObject();
        return DefaultImmutableVersionConstraint.of(preferredVersion, strictVersion, rejects);
    }

    private ImmutableList<ExcludeMetadata> consumeExcludes(JsonReader reader) throws IOException {
        ImmutableList.Builder<ExcludeMetadata> builder = new ImmutableList.Builder<ExcludeMetadata>();
        reader.beginArray();
        while (reader.peek() != END_ARRAY) {
            reader.beginObject();
            String group = null;
            String module = null;
            while (reader.peek() != END_OBJECT) {
                String name = reader.nextName();
                if (name.equals("group")) {
                    group = reader.nextString();
                } else if (name.equals("module")) {
                    module = reader.nextString();
                } else {
                    consumeAny(reader);
                }
            }
            reader.endObject();
            ExcludeMetadata exclude = excludeRuleConverter.createExcludeRule(group, module);
            builder.add(exclude);
        }
        reader.endArray();
        return builder.build();
    }

    private List<ModuleFile> consumeFiles(JsonReader reader) throws IOException {
        List<ModuleFile> files = new ArrayList<ModuleFile>();
        reader.beginArray();
        while (reader.peek() != END_ARRAY) {
            reader.beginObject();
            String fileName = null;
            String fileUri = null;
            while (reader.peek() != END_OBJECT) {
                String name = reader.nextName();
                if (name.equals("name")) {
                    fileName = reader.nextString();
                } else if (name.equals("url")) {
                    fileUri = reader.nextString();
                } else {
                    consumeAny(reader);
                }
            }
            reader.endObject();
            files.add(new ModuleFile(fileName, fileUri));
        }
        reader.endArray();
        return files;
    }

    private ImmutableAttributes consumeAttributes(JsonReader reader) throws IOException {
        ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
        reader.beginObject();
        while (reader.peek() != END_OBJECT) {
            String attrName = reader.nextName();
            if (reader.peek() == BOOLEAN) {
                boolean attrValue = reader.nextBoolean();
                attributes = attributesFactory.concat(attributes, Attribute.of(attrName, Boolean.class), attrValue);
            } else {
                String attrValue = reader.nextString();
                attributes = attributesFactory.concat(attributes, Attribute.of(attrName, String.class), new CoercingStringValueSnapshot(attrValue, instantiator));
            }
        }
        reader.endObject();
        return attributes;
    }

    private void consumeAny(JsonReader reader) throws IOException {
        reader.skipValue();
    }

    private static class ModuleFile {
        final String name;
        final String uri;

        ModuleFile(String name, String uri) {
            this.name = name;
            this.uri = uri;
        }
    }

    private static class ModuleDependency {
        final String group;
        final String module;
        final VersionConstraint versionConstraint;
        final ImmutableList<ExcludeMetadata> excludes;
        final String reason;
        final ImmutableAttributes attributes;

        ModuleDependency(String group, String module, VersionConstraint versionConstraint, ImmutableList<ExcludeMetadata> excludes, String reason, ImmutableAttributes attributes) {
            this.group = group;
            this.module = module;
            this.versionConstraint = versionConstraint;
            this.excludes = excludes;
            this.reason = reason;
            this.attributes = attributes;
        }
    }

    private static class ModuleDependencyConstraint {
        final String group;
        final String module;
        final VersionConstraint versionConstraint;
        final String reason;
        final ImmutableAttributes attributes;

        ModuleDependencyConstraint(String group, String module, VersionConstraint versionConstraint, String reason, ImmutableAttributes attributes) {
            this.group = group;
            this.module = module;
            this.versionConstraint = versionConstraint;
            this.reason = reason;
            this.attributes = attributes;
        }
    }

    private static class VariantCapability {
        final String group;
        final String name;
        final String version;

        private VariantCapability(String group, String name, String version) {
            this.group = group;
            this.name = name;
            this.version = version;
        }
    }
}
