/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.mirror;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.InvalidUserDataException;
import org.jspecify.annotations.Nullable;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code mirrors.toml} files. Schema:
 * <pre>
 * [[mirror]]
 * name = "corp-central"
 * match-url = "https://repo.maven.apache.org/**"
 * url = "https://artifactory.corp/maven-central"
 * credentials.username = "${env.CORP_USER}"
 * credentials.password = "${env.CORP_TOKEN}"
 * </pre>
 *
 * <p>Unknown keys and malformed TOML cause a fail-fast {@link InvalidUserDataException} with
 * file location, line, and column.
 */
public final class MirrorsTomlParser {

    private static final String MIRROR_KEY = "mirror";
    private static final Set<String> ALLOWED_TOP_LEVEL = ImmutableSet.of(MIRROR_KEY);
    private static final Set<String> ALLOWED_MIRROR_KEYS = ImmutableSet.of("name", "match-url", "url", "credentials");
    private static final Set<String> ALLOWED_CREDENTIALS_KEYS = ImmutableSet.of("username", "password");

    private static final Pattern REFERENCE_PATTERN = Pattern.compile("\\$\\{(env|sys|gradleProperty)\\.([A-Za-z0-9_.\\-]+)}");

    private MirrorsTomlParser() {
    }

    public static List<MirrorDefinition> parse(Path file) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            TomlParseResult result = Toml.parse(in);
            if (result.hasErrors()) {
                TomlParseError first = result.errors().get(0);
                throw fail(file, first.position(), first.getMessage());
            }
            checkTopLevelKeys(file, result);
            TomlArray mirrors = result.getArray(MIRROR_KEY);
            if (mirrors == null) {
                return new ArrayList<>();
            }
            List<MirrorDefinition> definitions = new ArrayList<>(mirrors.size());
            Set<String> names = new LinkedHashSet<>();
            for (int i = 0; i < mirrors.size(); i++) {
                TomlTable table = mirrors.getTable(i);
                MirrorDefinition def = parseMirror(file, mirrors.inputPositionOf(i), table);
                if (!names.add(def.getName())) {
                    throw fail(file, mirrors.inputPositionOf(i),
                        "Duplicate mirror name '" + def.getName() + "'");
                }
                definitions.add(def);
            }
            return definitions;
        } catch (IOException e) {
            throw new InvalidUserDataException("Failed to read mirrors file " + file + ": " + e.getMessage(), e);
        }
    }

    private static void checkTopLevelKeys(Path file, TomlParseResult result) {
        for (String key : result.keySet()) {
            if (!ALLOWED_TOP_LEVEL.contains(key)) {
                throw fail(file, result.inputPositionOf(key),
                    "Unknown top-level key '" + key + "'. Allowed: " + ALLOWED_TOP_LEVEL);
            }
        }
    }

    private static MirrorDefinition parseMirror(Path file, @Nullable TomlPosition tablePosition, TomlTable table) {
        for (String key : table.keySet()) {
            if (!ALLOWED_MIRROR_KEYS.contains(key)) {
                throw fail(file, table.inputPositionOf(key),
                    "Unknown key '" + key + "' in [[mirror]]. Allowed: " + ALLOWED_MIRROR_KEYS);
            }
        }
        String name = requireString(file, table, "name", tablePosition);
        String matchUrl = requireString(file, table, "match-url", tablePosition);
        String urlString = requireString(file, table, "url", tablePosition);
        URI url;
        try {
            url = new URI(urlString);
        } catch (URISyntaxException e) {
            throw fail(file, table.inputPositionOf("url"),
                "Invalid URL '" + urlString + "' for mirror '" + name + "': " + e.getMessage());
        }
        MirrorCredentialReferences credentials = null;
        TomlTable credsTable = table.getTableOrEmpty("credentials");
        if (table.contains("credentials")) {
            credentials = parseCredentials(file, name, credsTable);
        }
        return new MirrorDefinition(name, matchUrl, url, credentials);
    }

    private static MirrorCredentialReferences parseCredentials(Path file, String mirrorName, TomlTable credsTable) {
        for (String key : credsTable.keySet()) {
            if (!ALLOWED_CREDENTIALS_KEYS.contains(key)) {
                throw fail(file, credsTable.inputPositionOf(key),
                    "Unknown key '" + key + "' in credentials for mirror '" + mirrorName + "'. Allowed: " + ALLOWED_CREDENTIALS_KEYS);
            }
        }
        MirrorValueReference user = requireReference(file, credsTable, "username", mirrorName);
        MirrorValueReference pass = requireReference(file, credsTable, "password", mirrorName);
        return new MirrorCredentialReferences(user, pass);
    }

    private static MirrorValueReference requireReference(Path file, TomlTable table, String key, String mirrorName) {
        if (!table.contains(key)) {
            throw fail(file, table.inputPositionOf(""),
                "Missing required credentials." + key + " for mirror '" + mirrorName + "'");
        }
        String value = table.getString(key);
        TomlPosition position = table.inputPositionOf(key);
        if (value == null) {
            throw fail(file, position,
                "credentials." + key + " for mirror '" + mirrorName + "' must be a string");
        }
        Matcher matcher = REFERENCE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw fail(file, position,
                "credentials." + key + " for mirror '" + mirrorName
                    + "' must be a reference of the form ${env.NAME}, ${sys.NAME}, or ${gradleProperty.NAME}; literal values are not supported. Found: " + value);
        }
        MirrorValueReference.Kind kind = MirrorValueReference.Kind.valueOf(toEnum(matcher.group(1)));
        return new MirrorValueReference(kind, matcher.group(2));
    }

    private static String toEnum(String prefix) {
        switch (prefix) {
            case "env":
                return "ENV";
            case "sys":
                return "SYS";
            case "gradleProperty":
                return "GRADLE_PROPERTY";
            default:
                throw new IllegalStateException("Unknown reference prefix: " + prefix);
        }
    }

    private static String requireString(Path file, TomlTable table, String key, @Nullable TomlPosition tablePosition) {
        if (!table.contains(key)) {
            throw fail(file, tablePosition,
                "Missing required key '" + key + "' in [[mirror]]");
        }
        String value = table.getString(key);
        if (value == null) {
            throw fail(file, table.inputPositionOf(key),
                "Key '" + key + "' must be a string");
        }
        if (value.trim().isEmpty()) {
            throw fail(file, table.inputPositionOf(key),
                "Key '" + key + "' must not be empty");
        }
        return value;
    }

    private static InvalidUserDataException fail(Path file, @Nullable TomlPosition position, String message) {
        StringBuilder sb = new StringBuilder("Invalid mirrors file ").append(file);
        if (position != null) {
            sb.append(" (line ").append(position.line()).append(", column ").append(position.column()).append(")");
        }
        sb.append(": ").append(message);
        return new InvalidUserDataException(sb.toString());
    }
}
