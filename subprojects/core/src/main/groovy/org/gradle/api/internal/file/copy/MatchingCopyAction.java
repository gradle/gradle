/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import org.gradle.api.Action;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;

public class MatchingCopyAction implements Action<FileCopyDetails> {

    private final Spec<RelativePath> matchSpec;

    private final Action<? super FileCopyDetails> toApply;

    public MatchingCopyAction(Spec<RelativePath> matchSpec, Action<? super FileCopyDetails> toApply) {
        this.matchSpec = matchSpec;
        this.toApply = toApply;
    }

    public void execute(FileCopyDetails details) {
        if (matchSpec.isSatisfiedBy(details.getRelativePath())) {
            toApply.execute(details);
        }
    }

}
