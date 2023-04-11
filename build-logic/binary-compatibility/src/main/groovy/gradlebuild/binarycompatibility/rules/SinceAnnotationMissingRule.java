/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.binarycompatibility.rules;

import gradlebuild.binarycompatibility.metadata.KotlinMetadataQueries;
import japicmp.model.JApiClass;
import japicmp.model.JApiCompatibility;
import japicmp.model.JApiConstructor;
import japicmp.model.JApiField;
import japicmp.model.JApiHasAnnotations;
import japicmp.model.JApiMethod;
import me.champeau.gradle.japicmp.report.Violation;

import java.util.Map;

public class SinceAnnotationMissingRule extends AbstractGradleViolationRule {

    public SinceAnnotationMissingRule(Map<String, Object> params) {
        super(params);
    }

    @Override
    public Violation maybeViolation(final JApiCompatibility member) {

        if (shouldSkipViolationCheckFor(member) || getRepository().isSince(getCurrentVersion(), member)) {
            return null;
        }

        return acceptOrReject(member, Violation.error(member, "Is not annotated with @since " + getCurrentVersion()));
    }

    private boolean shouldSkipViolationCheckFor(JApiCompatibility member) {
        return !isClassFieldConstructorOrMethod(member) ||
            isInject(member) ||
            isOverrideMethod(member) ||
            isKotlinFileFacadeClass(member);
    }

    private boolean isClassFieldConstructorOrMethod(JApiCompatibility member) {
        return member instanceof JApiClass || member instanceof JApiField || member instanceof JApiConstructor || member instanceof JApiMethod;
    }

    private boolean isInject(JApiCompatibility member) {
        return member instanceof JApiHasAnnotations && isInject((JApiHasAnnotations) member);
    }

    private boolean isOverrideMethod(JApiCompatibility member) {
        return member instanceof JApiMethod && isOverride((JApiMethod) member);
    }

    /**
     * Kotlin file-facade classes can't have kdoc comments.
     */
    private boolean isKotlinFileFacadeClass(JApiCompatibility member) {
        return member instanceof JApiClass && KotlinMetadataQueries.INSTANCE.isKotlinFileFacadeClass(((JApiClass) member).getNewClass().get());
    }
}
