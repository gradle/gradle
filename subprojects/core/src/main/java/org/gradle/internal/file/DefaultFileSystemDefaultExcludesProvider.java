/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.file;

import com.google.common.collect.ImmutableList;
import org.gradle.BuildAdapter;
import org.gradle.api.initialization.Settings;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.event.AnonymousListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.excludes.FileSystemDefaultExcludesListener;
import org.gradle.internal.file.excludes.GradleDefaultExcludes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultFileSystemDefaultExcludesProvider implements FileSystemDefaultExcludesProvider {

    private ImmutableList<String> currentDefaultExcludes = GradleDefaultExcludes.DEFAULT_EXCLUDES;

    /**
     * Accumulated additions across every settings script seen this session. Each Settings has its
     * own {@code fileSystemDefaultExcludes} property; the historical Ant-based path was
     * process-global, so each script's contribution stacked. Preserve that behavior by tracking
     * deltas vs the baseline across all scripts and recomputing the resolved set as
     * {@code baseline + additions - removals}.
     */
    private final Set<String> accumulatedAdditions = new LinkedHashSet<>();
    private final Set<String> accumulatedRemovals = new LinkedHashSet<>();

    private final AnonymousListenerBroadcast<FileSystemDefaultExcludesListener> broadcast;

    public DefaultFileSystemDefaultExcludesProvider(ListenerManager listenerManager) {
        broadcast = listenerManager.createAnonymousBroadcaster(FileSystemDefaultExcludesListener.class);

        listenerManager.addListener(new RootBuildLifecycleListener() {
            @Override
            public void afterStart() {
                currentDefaultExcludes = GradleDefaultExcludes.DEFAULT_EXCLUDES;
                accumulatedAdditions.clear();
                accumulatedRemovals.clear();
                broadcast.getSource().onDefaultExcludesChanged(currentDefaultExcludes);
            }
        });

        listenerManager.addListener(new BuildAdapter() {

            @Override
            public void settingsEvaluated(Settings settings) {
                Set<String> fromSettings = settings.getFileSystemDefaultExcludes().get();
                if (!fromSettings.equals(GradleDefaultExcludes.DEFAULT_EXCLUDES_SET)) {
                    accumulateDelta(GradleDefaultExcludes.DEFAULT_EXCLUDES_SET, fromSettings);
                }

                Set<String> resolved = new LinkedHashSet<>(GradleDefaultExcludes.DEFAULT_EXCLUDES);
                resolved.addAll(accumulatedAdditions);
                resolved.removeAll(accumulatedRemovals);

                applyDefaultExcludes(resolved);
            }
        });
    }

    private void applyDefaultExcludes(Set<String> resolved) {
        currentDefaultExcludes = ImmutableList.copyOf(resolved);
        broadcast.getSource().onDefaultExcludesChanged(currentDefaultExcludes);
    }

    @Override
    public List<String> getCurrentDefaultExcludes() {
        return currentDefaultExcludes;
    }

    @Override
    public void updateCurrentDefaultExcludes(Set<String> excludes) {
        // Re-derive the accumulators from the supplied state so a later settingsEvaluated
        // call (which is unusual after this point but defensible) stacks correctly on top.
        accumulatedAdditions.clear();
        accumulatedRemovals.clear();
        accumulateDelta(GradleDefaultExcludes.DEFAULT_EXCLUDES_SET, excludes);

        applyDefaultExcludes(excludes);
    }

    /**
     * Records the diff between {@code before} and {@code after} into the running accumulators.
     * A pattern explicitly re-included by a later script is removed from {@code accumulatedRemovals}
     * so a later script can reverse an earlier script's removal; symmetrically, a pattern explicitly
     * dropped by a later script is removed from {@code accumulatedAdditions}.
     */
    private void accumulateDelta(Set<String> before, Set<String> after) {
        for (String e : after) {
            if (!before.contains(e)) {
                accumulatedAdditions.add(e);
            } else {
                // A baseline pattern present in `after` means this script wants it kept;
                // cancel any earlier removal.
                accumulatedRemovals.remove(e);
            }
        }
        for (String e : before) {
            if (!after.contains(e)) {
                accumulatedRemovals.add(e);
                // A baseline pattern absent from `after` means this script wants it gone;
                // cancel any earlier explicit addition (defensive — additions are non-baseline).
                accumulatedAdditions.remove(e);
            }
        }
    }
}
