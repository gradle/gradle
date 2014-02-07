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
package org.gradle.nativebinaries.platform.internal;

public class DefaultArchitecture implements ArchitectureInternal {
    private final String name;
    private final InstructionSet instructionSet;
    private final int registerSize;

    public DefaultArchitecture(String name, InstructionSet instructionSet, int registerSize) {
        this.name = name;
        this.instructionSet = instructionSet;
        this.registerSize = registerSize;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getDisplayName() {
        return String.format("architecture '%s'", name);
    }

    public InstructionSet getInstructionSet() {
        return instructionSet;
    }

    public int getRegisterSize() {
        return registerSize;
    }

    public boolean isI386() {
        return instructionSet == InstructionSet.X86 && registerSize == 32;
    }

    public boolean isAmd64() {
        return instructionSet == InstructionSet.X86 && registerSize == 64;
    }

    public boolean isIa64() {
        return instructionSet == InstructionSet.ITANIUM && registerSize == 64;
    }

    public boolean isArm() {
        return instructionSet == InstructionSet.ARM && registerSize == 32;
    }

    public boolean isArmv8() {
        return instructionSet == InstructionSet.ARM && registerSize == 64;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((instructionSet == null) ? 0 : instructionSet.hashCode());
        result = prime * result + registerSize;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DefaultArchitecture other = (DefaultArchitecture) obj;
        if (instructionSet != other.instructionSet) {
            return false;
        }
        if (registerSize != other.registerSize) {
            return false;
        }
        return true;
    }
}
