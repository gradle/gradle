/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.performance.measure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Units<Q> implements Comparable<Units<Q>> {
    private final Class<Q> type;
    private final String displaySingular;
    private final String displayPlural;

    protected Units(Class<Q> type, String displaySingular, String displayPlural) {
        this.type = type;
        this.displaySingular = displaySingular;
        this.displayPlural = displayPlural;
    }

    /**
     * Creates the base units for a given quantity.
     */
    public static <Q> Units<Q> base(Class<Q> type, String displaySingular) {
        return new BaseUnits<Q>(type, displaySingular, displaySingular);
    }

    /**
     * Creates the base units for a given quantity.
     */
    public static <Q> Units<Q> base(Class<Q> type, String displaySingular, String displayPlural) {
        return new BaseUnits<Q>(type, displaySingular, displayPlural);
    }

    protected Class<Q> getType() {
        return type;
    }

    @Override
    public String toString() {
        return displayPlural;
    }

    /**
     * Creates units that are some multiple of this units.
     */
    public abstract Units<Q> times(long value, String displaySingular, String displayPlural);

    /**
     * Creates units that are some multiple of this units.
     */
    public Units<Q> times(long value, String displaySingular) {
        return times(value, displaySingular, displaySingular);
    }

    protected abstract Units<Q> getBaseUnits();

    protected abstract List<Units<Q>> getUnitsForQuantity();

    protected abstract BigDecimal getFactor();

    protected abstract BigDecimal scaleTo(BigDecimal value, Units<Q> units);

    protected String format(BigDecimal value) {
        return value.compareTo(BigDecimal.ONE) == 0 ? displaySingular : displayPlural;
    }

    private static class BaseUnits<Q> extends Units<Q> {
        private final List<Units<Q>> units = new ArrayList<Units<Q>>();

        protected BaseUnits(Class<Q> type, String displaySingular, String displayPlural) {
            super(type, displaySingular, displayPlural);
            units.add(this);
        }

        @Override
        public BigDecimal scaleTo(BigDecimal value, Units<Q> units) {
            if (units == this) {
                return value;
            }
            ScaledUnits<Q> scaledUnits = (ScaledUnits<Q>) units;
            return value.divide(scaledUnits.factor, 6, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        @Override
        protected List<Units<Q>> getUnitsForQuantity() {
            return units;
        }

        @Override
        public Units<Q> times(long value, String displaySingular, String displayPlural) {
            return new ScaledUnits<Q>(this, displaySingular, displayPlural, BigDecimal.valueOf(value));
        }

        @Override
        protected Units<Q> getBaseUnits() {
            return this;
        }

        @Override
        protected BigDecimal getFactor() {
            return BigDecimal.ONE;
        }

        public int compareTo(Units<Q> o) {
            if (o == this) {
                return 0;
            }
            if (o.getType() != getType()) {
                throw new IllegalArgumentException(String.format("Cannot compare units of %s with units of %s.", getType(), o.getType()));
            }
            return -1;
        }

        public void add(ScaledUnits<Q> units) {
            this.units.add(units);
            Collections.sort(this.units);
        }
    }

    private static class ScaledUnits<Q> extends Units<Q> {
        private final BaseUnits<Q> baseUnits;
        private final BigDecimal factor;

        public ScaledUnits(BaseUnits<Q> baseUnits, String displaySingular, String displayPlural, BigDecimal factor) {
            super(baseUnits.getType(), displaySingular, displayPlural);
            assert factor.compareTo(BigDecimal.ONE) > 0;
            this.baseUnits = baseUnits;
            this.factor = factor;
            baseUnits.add(this);
        }

        @Override
        public Units<Q> times(long value, String displaySingular, String displayPlural) {
            return new ScaledUnits<Q>(baseUnits, displaySingular, displayPlural, factor.multiply(BigDecimal.valueOf(value)));
        }

        @Override
        public BigDecimal scaleTo(BigDecimal value, Units<Q> units) {
            if (units == this) {
                return value;
            }
            if (units.equals(baseUnits)) {
                return value.multiply(factor);
            }
            ScaledUnits<Q> other = (ScaledUnits<Q>) units;
            return value.multiply(factor).divide(other.factor, 6, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        @Override
        protected List<Units<Q>> getUnitsForQuantity() {
            return baseUnits.getUnitsForQuantity();
        }

        @Override
        protected Units<Q> getBaseUnits() {
            return baseUnits;
        }

        @Override
        protected BigDecimal getFactor() {
            return factor;
        }

        public int compareTo(Units<Q> o) {
            if (o.getType() != getType()) {
                throw new IllegalArgumentException(String.format("Cannot compare units of %s with units of %s.", getType(), o.getType()));
            }
            if (o.equals(baseUnits)) {
                return 1;
            }
            ScaledUnits<Q> other = (ScaledUnits<Q>) o;
            if (!other.baseUnits.equals(baseUnits)) {
                throw new IllegalArgumentException("Cannot compare units with different base units.");
            }
            return factor.compareTo(other.factor);
        }
    }
}
