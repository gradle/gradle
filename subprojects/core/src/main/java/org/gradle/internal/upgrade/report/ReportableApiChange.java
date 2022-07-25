/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.upgrade.report;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface ReportableApiChange {
    String getApiChangeReport();
    ApiChangeId getId();
    List<ApiChangeId> getAllKnownTypeIds();
    Optional<DynamicGroovyUpgradeDecoration> mapToDynamicGroovyDecoration(ApiUpgradeProblemCollector problemCollector);

    class ApiChangeId {
        private final int opcode;
        private final String owner;
        private final String name;
        private final String desc;

        public ApiChangeId(int opcode, String owner, String name, String desc) {
            this.opcode = opcode;
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        public int getOpcode() {
            return opcode;
        }

        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public String getDesc() {
            return desc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ApiChangeId that = (ApiChangeId) o;
            return opcode == that.opcode
                && Objects.equals(owner, that.owner)
                && Objects.equals(name, that.name)
                && Objects.equals(desc, that.desc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(opcode, owner, name, desc);
        }
    }
}
