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
package org.gradle.language.base.internal.tasks.apigen.abi;

import java.util.Collection;

public class DefaultMemberOfApiChecker implements MemberOfApiChecker {

    private final Collection<String> allowedPackages;

    public DefaultMemberOfApiChecker(Collection<String> allowedPackages) {
        this.allowedPackages = allowedPackages;
    }

    private static String extractPackageName(String className) {
        return className.indexOf(".") > 0 ? className.substring(0, className.lastIndexOf(".")) : "";
    }

    @Override
    public boolean belongsToApi(String className) {
        String pkg = extractPackageName(className);

        for (String javaBasePackage : JavaBaseModule.PACKAGES) {
            if (pkg.equals(javaBasePackage)) {
                return true;
            }
        }
        boolean allowed = false;
        for (String allowedPackage : allowedPackages) {
            if (pkg.equals(allowedPackage)) {
                allowed = true;
                break;
            }
        }
        return allowed;
    }
}
