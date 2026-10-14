/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.logging.console;

/**
 * Scales the done, running and queued segments of the progress bar.
 *
 * <p>Each segment gets {@code count ^ p} cells. {@code p} goes down towards 0.5 when the queued work is much larger than the done and running work,
 * so the done segment stays visible when a lot of new work arrives. {@code p} goes back up towards 1.0, a linear bar, as the work gets done.</p>
 */
class AdaptiveBarScale {
    private static final double QUEUE_RATIO_FOR_MIN_EXPONENT = 8.0;
    private static final double MIN_EXPONENT = 0.5;
    private static final double MAX_RISE_PER_MILLI = 0.5 / 1000;
    private static final int SEARCH_STEPS = 30;

    private double exponent = 1.0;
    private double lastDoneShare;
    private long lastFrameTime = -1;

    static double targetExponent(long done, long running, long queued) {
        double ratio = (double) queued / Math.max(done + running, 1);
        if (ratio < 1.0) {
            return 1.0;
        }
        double target = 1.0 - (1.0 - MIN_EXPONENT) * (ratio - 1.0) / (QUEUE_RATIO_FOR_MIN_EXPONENT - 1.0);
        return Math.max(MIN_EXPONENT, Math.min(1.0, target));
    }

    static Cells cells(int width, long done, long running, long queued, double exponent) {
        double doneWeight = weight(done, exponent);
        double runningWeight = weight(running, exponent);
        double queuedWeight = weight(queued, exponent);
        double totalWeight = doneWeight + runningWeight + queuedWeight;
        if (totalWeight == 0) {
            return new Cells(0, 0);
        }
        double doneCells = width * doneWeight / totalWeight;
        double runningCells = width * runningWeight / totalWeight;
        double queuedCells = width - doneCells - runningCells;
        if (running > 0 && runningCells < 1.0) {
            // Running work always gets one cell, taken from done and queued in proportion so the done edge does not jump
            double missing = 1.0 - runningCells;
            double others = doneCells + queuedCells;
            if (others > 0) {
                doneCells -= missing * doneCells / others;
            }
            runningCells = 1.0;
        }
        return new Cells(doneCells, runningCells);
    }

    /**
     * Moves the exponent for a new frame.
     *
     * @param totalChange greater than 0 when the known total grew since the last frame, less than 0 when it shrank
     * @param now the time of this frame, in milliseconds
     */
    double next(long done, long running, long queued, int totalChange, long now, int width) {
        long elapsed = lastFrameTime < 0 ? 0 : Math.max(0, now - lastFrameTime);
        double target = targetExponent(done, running, queued);
        if (target < exponent) {
            exponent = target;
        } else if (target > exponent) {
            double highest = totalChange < 0 ? target : Math.min(target, exponent + MAX_RISE_PER_MILLI * elapsed);
            if (totalChange > 0) {
                exponent = highest;
            } else {
                exponent = highestKeepingDoneShare(width, done, running, queued, exponent, highest);
            }
        }
        lastDoneShare = doneShare(width, done, running, queued, exponent);
        lastFrameTime = now;
        return exponent;
    }

    // The done segment only moves back when the total grew
    private double highestKeepingDoneShare(int width, long done, long running, long queued, double low, double high) {
        if (doneShare(width, done, running, queued, high) >= lastDoneShare) {
            return high;
        }
        if (doneShare(width, done, running, queued, low) < lastDoneShare) {
            return low;
        }
        for (int i = 0; i < SEARCH_STEPS; i++) {
            double middle = (low + high) / 2;
            if (doneShare(width, done, running, queued, middle) >= lastDoneShare) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static double doneShare(int width, long done, long running, long queued, double exponent) {
        return cells(width, done, running, queued, exponent).done / width;
    }

    private static double weight(long count, double exponent) {
        return count > 0 ? Math.pow((double) count, exponent) : 0;
    }

    static final class Cells {
        final double done;
        final double running;

        Cells(double done, double running) {
            this.done = done;
            this.running = running;
        }
    }
}
