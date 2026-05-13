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
import com.google.common.collect.ImmutableSet;
import org.apache.tools.ant.DirectoryScanner;
import org.gradle.BuildAdapter;
import org.gradle.api.initialization.Settings;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.event.AnonymousListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.excludes.FileSystemDefaultExcludesListener;
import org.gradle.internal.file.excludes.GradleDefaultExcludes;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultFileSystemDefaultExcludesProvider implements FileSystemDefaultExcludesProvider {

    private ImmutableList<String> currentDefaultExcludes = GradleDefaultExcludes.DEFAULT_EXCLUDES;

    /**
     * The DirectoryScanner state we last observed or wrote ourselves. Used to detect user-side
     * mutations to {@link DirectoryScanner} (the deprecated legacy path) without false-positives
     * from the mirror writes this class performs in {@link #syncDirectoryScanner(Set)}.
     *
     * <p>Initialized to {@link DirectoryScanner#getDefaultExcludes()} immediately after
     * {@link DirectoryScanner#resetDefaultExcludes()} in {@code afterStart}, so the user-mutation
     * diff stays correct even if Ant's {@code DEFAULTEXCLUDES} ever drifts from
     * {@link GradleDefaultExcludes#DEFAULT_EXCLUDES_SET} (the parity test would catch the drift,
     * but we shouldn't emit false-positive deprecation warnings if it slips through).</p>
     */
    private ImmutableSet<String> lastObservedDirectoryScannerState = GradleDefaultExcludes.DEFAULT_EXCLUDES_SET;

    /**
     * Accumulated additions across every settings script seen this session. Each Settings has its
     * own {@code fileSystemDefaultExcludes} property; the historical DirectoryScanner-based path
     * was process-global, so each script's contribution stacked. Preserve that behavior by
     * tracking deltas vs the baseline across all scripts and recomputing the resolved set as
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
                DirectoryScanner.resetDefaultExcludes();
                currentDefaultExcludes = GradleDefaultExcludes.DEFAULT_EXCLUDES;
                // Snapshot the freshly-reset DirectoryScanner state rather than assuming it
                // matches GradleDefaultExcludes.DEFAULT_EXCLUDES_SET. If Ant's DEFAULTEXCLUDES
                // ever drifts from our port, the user-mutation diff still works correctly and
                // we don't emit false-positive deprecations.
                lastObservedDirectoryScannerState = ImmutableSet.copyOf(DirectoryScanner.getDefaultExcludes());
                accumulatedAdditions.clear();
                accumulatedRemovals.clear();
                broadcast.getSource().onDefaultExcludesChanged(currentDefaultExcludes);
            }
        });

        listenerManager.addListener(new BuildAdapter() {

            @Override
            public void settingsEvaluated(@NonNull Settings settings) {
                Set<String> fromSettings = settings.getFileSystemDefaultExcludes().get();
                ImmutableSet<String> fromAnt = ImmutableSet.copyOf(DirectoryScanner.getDefaultExcludes());

                boolean settingsCustomized = !fromSettings.equals(GradleDefaultExcludes.DEFAULT_EXCLUDES_SET);
                // Compare against what we last wrote/observed, not against the original baseline:
                // otherwise our own mirror writes from a previous settings.gradle would look like
                // user-side Ant mutations and trigger false-positive deprecations.
                boolean antMutatedByUser = !fromAnt.equals(lastObservedDirectoryScannerState);

                if (settingsCustomized) {
                    if (antMutatedByUser) {
                        warnAntMutationIgnored();
                    }
                    accumulateDelta(GradleDefaultExcludes.DEFAULT_EXCLUDES_SET, fromSettings);
                } else if (antMutatedByUser) {
                    warnAntMutationDeprecated();
                    accumulateDelta(lastObservedDirectoryScannerState, fromAnt);
                }

                Set<String> resolved = new LinkedHashSet<>(GradleDefaultExcludes.DEFAULT_EXCLUDES);
                resolved.addAll(accumulatedAdditions);
                resolved.removeAll(accumulatedRemovals);

                syncDirectoryScanner(resolved);
                applyDefaultExcludes(resolved);
            }
        });
    }

    private void applyDefaultExcludes(Set<String> resolved) {
        lastObservedDirectoryScannerState = ImmutableSet.copyOf(resolved);
        currentDefaultExcludes = ImmutableList.copyOf(resolved);
        broadcast.getSource().onDefaultExcludesChanged(currentDefaultExcludes);
    }

    @Override
    public List<String> getCurrentDefaultExcludes() {
        return currentDefaultExcludes;
    }

    @Override
    public void updateCurrentDefaultExcludes(Set<String> excludes) {
        syncDirectoryScanner(excludes);

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

    private static void syncDirectoryScanner(Set<String> excludes) {
        for (String oldExclude : DirectoryScanner.getDefaultExcludes()) {
            if (!excludes.contains(oldExclude)) {
                DirectoryScanner.removeDefaultExclude(oldExclude);
            }
        }
        for (String exclude : excludes) {
            DirectoryScanner.addDefaultExclude(exclude);
        }
    }

    private static void warnAntMutationDeprecated() {
        DeprecationLogger.deprecateAction("Mutating org.apache.tools.ant.DirectoryScanner default excludes")
            .withAdvice("Use settings.fileSystemDefaultExcludes in settings.gradle(.kts) instead. " +
                "For example: fileSystemDefaultExcludes.add(\"**/node_modules\").")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "directoryscanner_default_excludes_deprecation")
            .nagUser();
    }

    private static void warnAntMutationIgnored() {
        DeprecationLogger.deprecateAction("Configuring file-system default excludes via both " +
                "org.apache.tools.ant.DirectoryScanner and settings.fileSystemDefaultExcludes")
            .withAdvice("settings.fileSystemDefaultExcludes takes precedence; the DirectoryScanner mutation is ignored. " +
                "Remove the DirectoryScanner calls from your settings script.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "directoryscanner_default_excludes_deprecation")
            .nagUser();
    }
}
