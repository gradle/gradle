/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import com.google.common.collect.ImmutableMap;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.IgnoredPathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.NameOnlyFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.RelativePathFingerprintingStrategy;

import java.util.SortedMap;

public abstract class AbstractFingerprintChanges implements ChangeContainer {
    private static final ImmutableMap<String, FingerprintCompareStrategy> COMPARE_STRATEGY_MAPPING = ImmutableMap.<String, FingerprintCompareStrategy>builder()
        .put(AbsolutePathFingerprintingStrategy.IDENTIFIER, AbsolutePathFingerprintCompareStrategy.INSTANCE)
        .put(NameOnlyFingerprintingStrategy.IDENTIFIER, NormalizedPathFingerprintCompareStrategy.INSTANCE)
        .put(RelativePathFingerprintingStrategy.IDENTIFIER, NormalizedPathFingerprintCompareStrategy.INSTANCE)
        .put(IgnoredPathFingerprintingStrategy.IDENTIFIER, IgnoredPathCompareStrategy.INSTANCE)
        .put(FingerprintingStrategy.CLASSPATH_IDENTIFIER, ClasspathCompareStrategy.INSTANCE)
        .put(FingerprintingStrategy.COMPILE_CLASSPATH_IDENTIFIER, ClasspathCompareStrategy.INSTANCE)
        .build();

    protected final SortedMap<String, FileCollectionFingerprint> previous;
    protected final SortedMap<String, CurrentFileCollectionFingerprint> current;
    private final String title;

    protected AbstractFingerprintChanges(SortedMap<String, FileCollectionFingerprint> previous, SortedMap<String, CurrentFileCollectionFingerprint> current, String title) {
        this.previous = previous;
        this.current = current;
        this.title = title;
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        return SortedMapDiffUtil.diff(previous, current, new PropertyDiffListener<String, FileCollectionFingerprint, CurrentFileCollectionFingerprint>() {
            @Override
            public boolean removed(String previousProperty) {
                return true;
            }

            @Override
            public boolean added(String currentProperty) {
                return true;
            }

            @Override
            public boolean updated(String property, FileCollectionFingerprint previousFingerprint, CurrentFileCollectionFingerprint currentFingerprint) {
                String propertyTitle = title + " property '" + property + "'";
                FingerprintCompareStrategy compareStrategy = determineCompareStrategy(currentFingerprint);
                return compareStrategy.visitChangesSince(previousFingerprint, currentFingerprint, propertyTitle, visitor);
            }
        });
    }

    protected FingerprintCompareStrategy determineCompareStrategy(CurrentFileCollectionFingerprint currentFingerprint) {
        return COMPARE_STRATEGY_MAPPING.get(currentFingerprint.getStrategyIdentifier());
    }
}
