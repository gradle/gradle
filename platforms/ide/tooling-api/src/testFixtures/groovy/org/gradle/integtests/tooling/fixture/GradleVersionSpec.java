/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.tooling.fixture;

import org.gradle.util.GradleVersion;

import java.util.ArrayList;
import java.util.List;

public class GradleVersionSpec {
    private GradleVersion lowestTestedVersion = getLowestTestedVersion();

    private static GradleVersion getLowestTestedVersion() {
        String property = System.getProperty("org.gradle.integtest.crossVersion.lowestTestedVersion");
        if(property == null) {
            return GradleVersion.version("0.0");
        }
        return GradleVersion.version(property);
    }

    public GradleVersionSpec() {
        this.lowestTestedVersion = getLowestTestedVersion();
    }

    public GradleVersionSpec(String lowestTestedVersion) {
        this.lowestTestedVersion = GradleVersion.version(lowestTestedVersion);
    }

    public Spec<GradleVersion> toSpec(String constraint) {
        String trimmed = constraint.trim();
        if (trimmed.equals("current")) {
            return new Spec<GradleVersion>() {
                @Override
                public boolean isSatisfiedBy(GradleVersion element) {
                    return element.equals(GradleVersion.current());
                }
            };
        }
        if (trimmed.equals("!current")) {
            return new Spec<GradleVersion>() {
                @Override
                public boolean isSatisfiedBy(GradleVersion element) {
                    return !element.equals(GradleVersion.current());
                }
            };
        }
        if (trimmed.startsWith("=")) {
            final GradleVersion target = GradleVersion.version(trimmed.substring(1)).getBaseVersion();
            return new Spec<GradleVersion>() {
                @Override
                public boolean isSatisfiedBy(GradleVersion element) {
                    return element.getBaseVersion().equals(target);
                }
            };
        }

        List<Spec<GradleVersion>> specs = new ArrayList<Spec<GradleVersion>>();
        String[] patterns = trimmed.split("\\s+");
        for (String value : patterns) {
            if (value.startsWith(">=")) {
                final GradleVersion minVersion = getVersion(value, 2);
                validateLowestTestedVersion(constraint, value, minVersion);
                specs.add(new Spec<GradleVersion>() {
                    @Override
                    public boolean isSatisfiedBy(GradleVersion element) {
                        return element.getBaseVersion().compareTo(minVersion) >= 0;
                    }
                });
            } else if (value.startsWith(">")) {
                final GradleVersion minVersion = getVersion(value, 1);
                validateLowestTestedVersion(constraint, value, minVersion);
                specs.add(new Spec<GradleVersion>() {
                    @Override
                    public boolean isSatisfiedBy(GradleVersion element) {
                        return element.getBaseVersion().compareTo(minVersion) > 0;
                    }
                });
            } else if (value.startsWith("<=")) {
                final GradleVersion maxVersion = getVersion(value, 2);
                validateLowestTestedVersion(constraint, value, maxVersion);
                specs.add(new Spec<GradleVersion>() {
                    @Override
                    public boolean isSatisfiedBy(GradleVersion element) {
                        return element.getBaseVersion().compareTo(maxVersion) <= 0;
                    }
                });
            } else if (value.startsWith("<")) {
                final GradleVersion maxVersion = getVersion(value, 1);
                validateLowestTestedVersion(constraint, value, maxVersion);
                specs.add(new Spec<GradleVersion>() {
                    @Override
                    public boolean isSatisfiedBy(GradleVersion element) {
                        return element.getBaseVersion().compareTo(maxVersion) < 0;
                    }
                });
            } else if (value.startsWith("!")) {
                final GradleVersion excludedVersion = getVersion(value, 1);
                validateLowestTestedVersion(constraint, value, excludedVersion);
                specs.add(new Spec<GradleVersion>() {
                    @Override
                    public boolean isSatisfiedBy(GradleVersion element) {
                        return !element.getBaseVersion().equals(excludedVersion);
                    }
                });
            } else {
                throw new RuntimeException(String.format("Unsupported version range '%s' specified in constraint '%s'. Supported formats: '>=nnn', '>nnn', '<=nnn', '<nnn', '!nnn' or space-separate patterns", value, constraint));
            }
        }
        if (specs.size() == 1) {
            return specs.get(0);
        }
        return Specs.intersect(specs);
    }

    private void validateLowestTestedVersion(String constraint, String value, GradleVersion minVersion) {
        // The cross-version tests should fail if the specified version range is outside the supported set of versions
        if(minVersion.getBaseVersion().compareTo(lowestTestedVersion) < 0) {
            throw new RuntimeException(String.format("Unsupported version range '%s' specified in constraint '%s'. " +
                "The minimum version that can be provided is '%s'", value, constraint, lowestTestedVersion.getVersion()));
        }
    }

    private static GradleVersion getVersion(String value, int beginIndex) {
        return GradleVersion.version(value.substring(beginIndex));
    }

}
