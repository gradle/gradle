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

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * An amount is an immutable value of some quantity, such as duration or length. Each amount has a decimal value and associated units.
 *
 * TODO - need to sort out scaling when dividing or converting between units.
 */
public class Amount<Q> implements Comparable<Amount<Q>> {
    // decimalFormat is not thread safe - synchronize access to the instance
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.###", new DecimalFormatSymbols(Locale.US));
    private final BigDecimal value;
    private final Units<Q> units;
    private final BigDecimal normalised;
    private String cachedToString;
    private String cachedFormattedString;

    private Amount(BigDecimal value, Units<Q> units) {
        this.value = value;
        this.units = units;
        normalised = units.scaleTo(value, units.getBaseUnits());
    }

    public static <Q> Amount<Q> valueOf(long value, Units<Q> units) {
        return valueOf(BigDecimal.valueOf(value), units);
    }

    /**
     * Returns null if the given value is null.
     */
    @Nullable
    public static <Q> Amount<Q> valueOf(@Nullable BigDecimal value, Units<Q> units) {
        if (value == null) {
            return null;
        }
        return new Amount<Q>(value, units);
    }

    /**
     * Returns a string representation of this amount. Uses the original value and units of this amount.
     */
    @Override
    public String toString() {
        if (cachedToString == null) {
            cachedToString = String.valueOf(value) + " " + units.format(value);
        }
        return cachedToString;
    }

    public Units<Q> getUnits() {
        return units;
    }

    public BigDecimal getValue() {
        return value;
    }

    /**
     * Returns a human consumable string representation of this amount.
     */
    public String format() {
        if (cachedFormattedString == null) {
            cachedFormattedString = doFormat();
        }
        return cachedFormattedString;
    }

    private String doFormat() {
        List<Units<Q>> allUnits = units.getUnitsForQuantity();
        BigDecimal base = normalised.abs();
        for (int i = allUnits.size()-1; i >= 0; i--) {
            Units<Q> candidate = allUnits.get(i);
            if (base.compareTo(candidate.getFactor()) >= 0) {
                BigDecimal scaled = units.scaleTo(value, candidate);
                return formatValue(scaled) + " " + candidate.format(scaled);
            }
        }
        return formatValue(value) + " " + units.format(value);
    }

    private static String formatValue(BigDecimal value) {
        synchronized (DECIMAL_FORMAT) {
            return DECIMAL_FORMAT.format(value);
        }
    }

    /**
     * Converts this amount to an equivalent amount in the specified units.
     *
     * @return The converted amount.
     */
    public Amount<Q> toUnits(Units<Q> units) {
        if (units.equals(this.units)) {
            return this;
        }
        return new Amount<Q>(this.units.scaleTo(value, units), units);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Amount<Q> other = (Amount) obj;
        return compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return normalised.hashCode();
    }

    @Override
    public int compareTo(Amount<Q> o) {
        if (o.units.getType() != units.getType()) {
            throw new IllegalArgumentException(String.format("Cannot compare amount with units %s.", o.units));
        }
        return normalised.compareTo(o.normalised);
    }

    public Amount<Q> plus(Amount<Q> other) {
        if (other.value.equals(BigDecimal.ZERO)) {
            return this;
        }
        if (value.equals(BigDecimal.ZERO)) {
            return other;
        }
        int diff = units.compareTo(other.units);
        if (diff == 0) {
            return new Amount<Q>(value.add(other.value), units);
        }
        if (diff < 0) {
            return new Amount<Q>(value.add(other.units.scaleTo(other.value, units)), units);
        }
        return new Amount<Q>(units.scaleTo(value, other.units).add(other.value), other.units);
    }

    public Amount<Q> minus(Amount<Q> other) {
        if (other.value.equals(BigDecimal.ZERO)) {
            return this;
        }
        int diff = units.compareTo(other.units);
        if (diff == 0) {
            return new Amount<Q>(value.subtract(other.value), units);
        }
        if (diff < 0) {
            return new Amount<Q>(value.subtract(other.units.scaleTo(other.value, units)), units);
        }
        return new Amount<Q>(units.scaleTo(value, other.units).subtract(other.value), other.units);
    }

    public Amount<Q> multiply(BigDecimal other) {
        return new Amount<Q>(value.multiply(other), units);
    }

    public Amount<Q> div(long other) {
        return div(BigDecimal.valueOf(other));
    }
    public Amount<Q> div(BigDecimal other) {
        return new Amount<Q>(value.divide(other, 6, RoundingMode.HALF_UP), units);
    }

    public BigDecimal div(Amount<Q> other) {
        return normalised.divide(other.normalised, 6, RoundingMode.HALF_UP);
    }

    public Amount<Q> abs() {
        if (value.compareTo(BigDecimal.ZERO) >= 0) {
            return this;
        }
        return new Amount<Q>(value.abs(), units);
    }
}
