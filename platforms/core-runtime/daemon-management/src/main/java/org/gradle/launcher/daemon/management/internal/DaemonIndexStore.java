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

package org.gradle.launcher.daemon.management.internal;

import org.gradle.launcher.daemon.management.DaemonIndexEntry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the cross-version daemon index.
 *
 * <p>The index lives at {@code <daemon base dir>/index} and holds one file per daemon, named after that
 * daemon's uid. Only the daemon that owns an entry ever writes it, so no lock is needed and two daemons
 * starting at once cannot interfere with each other. Writes go to a temporary file and are moved into
 * place, so a reader sees either the previous content or the new content and never a half written file.
 *
 * <p>A daemon writes its entry once, when it starts, and deletes it when it stops. Nothing is written
 * while a build runs.
 */
public class DaemonIndexStore {

    public static final String INDEX_DIR_NAME = "index";

    private static final Logger LOGGER = LoggerFactory.getLogger(DaemonIndexStore.class);
    private static final String ENTRY_SUFFIX = ".json";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final File indexDir;

    public DaemonIndexStore(File daemonBaseDir) {
        this.indexDir = new File(daemonBaseDir, INDEX_DIR_NAME);
    }

    public File getIndexDir() {
        return indexDir;
    }

    /**
     * Publishes an entry, replacing any entry with the same uid.
     *
     * @throws IOException when the entry could not be written; callers on the daemon startup path treat
     * this as non-fatal, because a missing index entry costs discoverability and nothing else
     */
    public void store(DaemonIndexEntry entry) throws IOException {
        if (!indexDir.isDirectory()) {
            if (!indexDir.mkdirs() && !indexDir.isDirectory()) {
                throw new IOException("Could not create the daemon index directory " + indexDir);
            }
            // Only on creation. Re-applying this on every write would take the directory through
            // unreadable states while other daemons are listing it and writing into it.
            restrictToOwner(indexDir, true);
        }
        File target = entryFile(entry.getUid());
        // Named after the daemon rather than randomly: only that daemon ever writes this entry, so the
        // name cannot collide, and a leftover from a crashed write is overwritten rather than accumulating.
        File temporary = new File(indexDir, entry.getUid() + ENTRY_SUFFIX + ".tmp");
        try {
            if (!temporary.exists() && !temporary.createNewFile() && !temporary.exists()) {
                throw new IOException("Could not create the temporary daemon index file " + temporary);
            }
            restrictToOwner(temporary, false);
            Files.write(temporary.toPath(), Json.writeObject(toMap(entry)).getBytes(UTF_8));
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (UnsupportedOperationException e) {
                // Not every file system offers an atomic move. A plain replace is still better than a partial write.
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                LOGGER.debug("Could not remove the temporary daemon index file {}.", temporary);
            }
        }
    }

    /**
     * Removes the entry for the given daemon. Removing an entry that is not there is not an error.
     */
    public void remove(String uid) {
        File file = entryFile(uid);
        if (file.exists() && !file.delete()) {
            LOGGER.debug("Could not remove the daemon index entry {}.", file);
        }
    }

    /**
     * Every entry currently published, in no particular order.
     *
     * <p>Entries that cannot be read are skipped rather than failing the call: the index describes other
     * processes, which may be writing, stopping or running a version whose entries this reader predates.
     */
    public List<DaemonIndexEntry> getAll() {
        File[] files = indexDir.listFiles();
        if (files == null) {
            return new ArrayList<DaemonIndexEntry>();
        }
        List<DaemonIndexEntry> result = new ArrayList<DaemonIndexEntry>(files.length);
        for (File file : files) {
            if (!file.getName().endsWith(ENTRY_SUFFIX)) {
                continue;
            }
            DaemonIndexEntry entry = tryRead(file);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    private @Nullable DaemonIndexEntry tryRead(File file) {
        try {
            String text = new String(Files.readAllBytes(file.toPath()), UTF_8);
            return fromMap(Json.parseObject(text));
        } catch (Exception e) {
            LOGGER.debug("Ignoring unreadable daemon index entry {}.", file, e);
            return null;
        }
    }

    private File entryFile(String uid) {
        return new File(indexDir, uid + ENTRY_SUFFIX);
    }

    public static Map<String, Object> toMap(DaemonIndexEntry entry) {
        Map<String, Object> members = new LinkedHashMap<String, Object>();
        members.put("schemaVersion", entry.getSchemaVersion());
        members.put("gradleVersion", entry.getGradleVersion());
        members.put("uid", entry.getUid());
        members.put("pid", entry.getPid());
        members.put("port", entry.getPort());
        members.put("addresses", entry.getAddresses());
        members.put("token", Base64.getEncoder().encodeToString(entry.getToken()));
        members.put("startedAt", entry.getStartedAt());
        members.put("daemonBaseDir", entry.getDaemonBaseDir());
        members.put("javaHome", entry.getJavaHome());
        members.put("logFile", entry.getLogFile());
        return members;
    }

    public static DaemonIndexEntry fromMap(Map<String, Object> members) {
        int schemaVersion = (int) requireNumber(members, "schemaVersion");
        if (schemaVersion > DaemonIndexEntry.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Daemon index entry uses schema version " + schemaVersion
                + ", which is newer than the highest version this reader understands ("
                + DaemonIndexEntry.CURRENT_SCHEMA_VERSION + ")");
        }
        List<String> addresses = new ArrayList<String>();
        Object rawAddresses = members.get("addresses");
        if (rawAddresses instanceof List) {
            for (Object address : (List<?>) rawAddresses) {
                addresses.add(String.valueOf(address));
            }
        }
        return new DaemonIndexEntry(
            schemaVersion,
            requireString(members, "gradleVersion"),
            requireString(members, "uid"),
            optionalNumber(members, "pid"),
            (int) requireNumber(members, "port"),
            addresses,
            Base64.getDecoder().decode(requireString(members, "token")),
            requireNumber(members, "startedAt"),
            requireString(members, "daemonBaseDir"),
            optionalString(members, "javaHome"),
            optionalString(members, "logFile")
        );
    }

    private static String requireString(Map<String, Object> members, String name) {
        Object value = members.get(name);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Daemon index entry is missing the '" + name + "' member");
        }
        return (String) value;
    }

    private static @Nullable String optionalString(Map<String, Object> members, String name) {
        Object value = members.get(name);
        return value instanceof String ? (String) value : null;
    }

    private static long requireNumber(Map<String, Object> members, String name) {
        Object value = members.get(name);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Daemon index entry is missing the '" + name + "' member");
        }
        return ((Number) value).longValue();
    }

    private static @Nullable Long optionalNumber(Map<String, Object> members, String name) {
        Object value = members.get(name);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    /**
     * Makes a file or directory readable by its owner alone, matching how the per-version registry is
     * protected. The token in an entry is what authorises stopping a daemon, so the entry must not be
     * world readable. This is a no-op on file systems that do not carry these permissions, exactly as
     * the equivalent registry code behaves there.
     */
    private static void restrictToOwner(File file, boolean executable) {
        boolean applied = file.setReadable(false, false);
        applied = file.setWritable(false, false) && applied;
        applied = file.setReadable(true, true) && applied;
        applied = file.setWritable(true, true) && applied;
        if (executable) {
            file.setExecutable(false, false);
            file.setExecutable(true, true);
        }
        if (!applied) {
            LOGGER.debug("Could not restrict {} to its owner.", file);
        }
    }

    @Override
    public String toString() {
        return "DaemonIndexStore{" + indexDir + "}";
    }
}
