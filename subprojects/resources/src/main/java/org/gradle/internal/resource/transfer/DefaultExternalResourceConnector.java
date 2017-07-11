/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.resource.transfer;

import org.gradle.internal.resource.ReadableContent;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultExternalResourceConnector implements ExternalResourceConnector {
    private static final String SYSPROP_KEY = "gradle.externalresources.recordstats";
    private final static ExternalResourceAccessStats.Mode STATS_MODE = ExternalResourceAccessStats.Mode.valueOf(System.getProperty(SYSPROP_KEY, "none"));
    private final static ExternalResourceAccessStats STATS = STATS_MODE.create();

    private final ExternalResourceAccessor accessor;
    private final ExternalResourceLister lister;
    private final ExternalResourceUploader uploader;

    public DefaultExternalResourceConnector(ExternalResourceAccessor accessor, ExternalResourceLister lister, ExternalResourceUploader uploader) {
        this.accessor = accessor;
        this.lister = lister;
        this.uploader = uploader;
    }

    public static ExternalResourceAccessStats getStatistics() {
        return STATS;
    }

    @Nullable
    @Override
    public ExternalResourceReadResponse openResource(URI location, boolean revalidate) {
        STATS.resource(location);
        return accessor.openResource(location, revalidate);
    }

    @Nullable
    @Override
    public ExternalResourceMetaData getMetaData(URI location, boolean revalidate) {
        STATS.metadata(location);
        return accessor.getMetaData(location, revalidate);
    }

    @Nullable
    @Override
    public List<String> list(URI parent) {
        STATS.list(parent);
        return lister.list(parent);
    }

    @Override
    public void upload(ReadableContent resource, URI destination) throws IOException {
        STATS.upload(destination);
        uploader.upload(resource, destination);
    }

    public interface ExternalResourceAccessStats {
        enum Mode {
            none,
            count,
            trace;

            public ExternalResourceAccessStats create() {
                switch (this) {
                    case none:
                        return NoOpStats.INSTANCE;
                    case count:
                        return new CountingStats();
                    case trace:
                        return new MemoizingStats();
                }
                throw new UnsupportedOperationException();
            }
        }

        void resource(URI location);

        void metadata(URI location);

        void list(URI parent);

        void upload(URI destination);

        void reset();
    }

    private static class NoOpStats implements ExternalResourceAccessStats {

        public static final NoOpStats INSTANCE = new NoOpStats();

        @Override
        public void resource(URI location) {
        }

        @Override
        public void metadata(URI location) {
        }

        @Override
        public void list(URI parent) {
        }

        @Override
        public void upload(URI destination) {
        }

        @Override
        public void reset() {
        }

        @Override
        public String toString() {
            return "External resources access stats are not recorded. Run Gradle with -D" + SYSPROP_KEY + "=(count|trace) to record statistics";
        }
    }

    private static class CountingStats implements ExternalResourceAccessStats {
        private final AtomicInteger resourceCount = new AtomicInteger();
        private final AtomicInteger metadataCount = new AtomicInteger();
        private final AtomicInteger listCount = new AtomicInteger();
        private final AtomicInteger uploadCount = new AtomicInteger();

        @Override
        public void resource(URI location) {
            resourceCount.incrementAndGet();
        }

        @Override
        public void metadata(URI location) {
            metadataCount.incrementAndGet();
        }

        @Override
        public void list(URI parent) {
            listCount.incrementAndGet();
        }

        @Override
        public void upload(URI destination) {
            uploadCount.incrementAndGet();
        }

        @Override
        public synchronized void reset() {
            resourceCount.set(0);
            metadataCount.set(0);
            listCount.set(0);
            uploadCount.set(0);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("External resources connector statistics: \n");
            sb.append("   - Resources fetched : ").append(resourceCount.get()).append("\n");
            sb.append("   - Metadata fetched  : ").append(metadataCount.get()).append("\n");
            sb.append("   - Lists             : ").append(listCount.get()).append("\n");
            sb.append("   - Uploads           : ").append(uploadCount.get()).append("\n");
            return sb.toString();
        }
    }

    private static class MemoizingStats extends CountingStats {
        private final Map<URI, Integer> resources = new HashMap<URI, Integer>();
        private final Map<URI, Integer> metadata = new HashMap<URI, Integer>();
        private final Map<URI, Integer> lists = new HashMap<URI, Integer>();
        private final Map<URI, Integer> uploads = new HashMap<URI, Integer>();

        private synchronized void record(Map<URI, Integer> container, URI uri) {
            Integer count = container.get(uri);
            if (count == null) {
                container.put(uri, 1);
            } else {
                container.put(uri, count+1);
            }
        }

        @Override
        public void list(URI parent) {
            record(lists, parent);
            super.list(parent);
        }

        @Override
        public void metadata(URI location) {
            record(metadata, location);
            super.metadata(location);
        }

        @Override
        public void resource(URI location) {
            record(resources, location);
            super.resource(location);
        }

        @Override
        public void upload(URI destination) {
            record(uploads, destination);
            super.upload(destination);
        }

        @Override
        public synchronized void reset() {
            super.reset();
            resources.clear();
            metadata.clear();
            lists.clear();
            uploads.clear();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(super.toString());
            statsFor("fetched resources", resources, sb, 10);
            statsFor("fetched metadata", metadata, sb, 10);
            statsFor("lists queries", lists, sb, 10);
            statsFor("uploaded URIs", uploads, sb, 10);
            return sb.toString();
        }

        private void statsFor(String label, Map<URI, Integer> stats, StringBuilder sb, int max) {
            if (stats.isEmpty()) {
                return;
            }
            List<Map.Entry<URI, Integer>> entries = new ArrayList<Map.Entry<URI, Integer>>(stats.entrySet());
            Collections.sort(entries, new Comparator<Map.Entry<URI, Integer>>() {
                @Override
                public int compare(Map.Entry<URI, Integer> o1, Map.Entry<URI, Integer> o2) {
                    return o2.getValue() - o1.getValue();
                }
            });
            sb.append("Top ").append(max).append(" most ").append(label).append("\n");
            int cpt = 0;
            for (Map.Entry<URI, Integer> entry : entries) {
                sb.append("   ").append(entry.getKey()).append(" (").append(entry.getValue()).append(" times)\n");
                if (++cpt==max) {
                    break;
                }
            }
        }
    }
}
