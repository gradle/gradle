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

package org.gradle.internal.filewatch.jdk7;

import com.sun.nio.file.SensitivityWatchEventModifier;
import org.gradle.internal.Cast;
import org.gradle.internal.concurrent.Stoppable;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

class WatchServiceWatchStrategy implements WatchStrategy {
    // http://stackoverflow.com/a/18362404
    // make watch sensitivity as 2 seconds on MacOSX, polls every 2 seconds for changes. Default is 10 seconds.
    private static final WatchEvent.Modifier[] WATCH_MODIFIERS = new WatchEvent.Modifier[]{SensitivityWatchEventModifier.HIGH};
    private static final WatchEvent.Kind[] WATCH_KINDS = new WatchEvent.Kind[]{StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY};
    private final WatchService watchService;

    WatchServiceWatchStrategy(WatchService watchService) {
        this.watchService = watchService;
    }

    @Override
    public Stoppable watchSingleDirectory(Path path) throws IOException {
        WatchKey watchKey = path.register(watchService, WATCH_KINDS, WATCH_MODIFIERS);
        return new WatchKeyStoppable(watchKey);
    }

    @Override
    public boolean pollChanges(long timeout, TimeUnit unit, WatchListener watchHandler) throws InterruptedException {
        WatchKey watchKey = watchService.poll(timeout, unit);
        if (watchKey != null) {
            handleWatchKey(watchKey, watchHandler);
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        try {
            watchService.close();
        } catch (IOException e) {
            // ignore
        }
    }

    protected void handleWatchKey(WatchKey watchKey, WatchListener watchHandler) {
        Path watchedPath = (Path)watchKey.watchable();

        for (WatchEvent<?> event : watchKey.pollEvents()) {
            WatchEvent.Kind kind = event.kind();

            if (kind == OVERFLOW) {
                // overflow event occurs when some change event might have been lost
                // notify changes in that case
                watchHandler.onOverflow();
                continue;
            }

            if (kind.type() == Path.class) {
                WatchEvent<Path> ev = Cast.uncheckedCast(event);
                Path fullPath = watchedPath.resolve(ev.context());
                ChangeDetails changeDetails = new ChangeDetails(watchedPath, fullPath, toChangeType(kind));

                watchHandler.onChange(changeDetails);
            }
        }

        watchKey.reset();
    }

    private ChangeDetails.ChangeType toChangeType(WatchEvent.Kind kind) {
        if(kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return ChangeDetails.ChangeType.CREATE;
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return ChangeDetails.ChangeType.DELETE;
        } else {
            return ChangeDetails.ChangeType.MODIFY;
        }
    }

    public static class WatchKeyStoppable implements Stoppable {
        private final WatchKey watchKey;

        public WatchKeyStoppable(WatchKey watchKey) {
            this.watchKey = watchKey;
        }

        @Override
        public void stop() {
            watchKey.cancel();
        }
    }
}
