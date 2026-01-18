/*
 * Copyright 2025 the original author or authors.
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

import java.nio.file.*;
import java.util.*;

/**
 * Validates that remote project refs specified in gradle.properties are tagged.
 */
public class CheckRemoteProjectRef {
    static void die(String m) {
        System.err.println(m);
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            die("At least one property name must be provided\nUsage: java CheckRemoteProjectRef.java <property-name> [property-name ...]");
        }
        var props = new Properties();
        try (var in = Files.newInputStream(Path.of("gradle.properties"))) {
            props.load(in);
        }
        for (var name : args) {
            var value = props.getProperty(name);
            if (value == null || value.isBlank()) {
                die("Property '" + name + "' not found in gradle.properties");
            }
            validate(name, value.trim());
        }
    }

    static void validate(String name, String value) throws Exception {
        int i = value.indexOf('#');
        if (i < 1 || i == value.length() - 1) {
            die(name + " must be in format: <repository-url>#<commit-sha>, got: " + value);
        }
        var repo = value.substring(0, i);
        var sha = value.substring(i + 1).trim();
        if (sha.isEmpty()) {
            die(name + " must be in format: <repository-url>#<commit-sha>, got: " + value);
        }

        System.out.println("Checking if commit " + sha + " is tagged in " + repo);
        var r = exec("git", "ls-remote", "--tags", repo);
        if (r.code != 0) {
            die("Failed to list tags from " + repo + ": " + r.out);
        }

        var tags = new ArrayList<String>();
        for (var line : r.out.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            var parts = line.split("\t");
            if (parts.length != 2) {
                continue;
            }
            if (parts[0].trim().startsWith(sha)) {
                tags.add(parts[1].trim().replaceFirst("^refs/tags/", "").replaceFirst("\\^\\{\\}$", ""));
            }
        }
        if (tags.isEmpty()) {
            die("Commit " + sha + " in repository " + repo
                    + " is not tagged. Please ensure the commit is tagged before using it in smoke tests.");
        }
        System.out.println("✓ Commit " + sha + " is tagged: " + String.join(", ", tags));
    }

    static Res exec(String... cmd) throws Exception {
        var p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        var out = new String(p.getInputStream().readAllBytes());
        return new Res(out, p.waitFor());
    }

    record Res(String out, int code) {
    }
}
