/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.kogito.explainability.utils;

import java.util.Arrays;

import org.apache.commons.math3.distribution.TDistribution;

public class WeightedLinearRegressionResults {
    private final double[] coefficients;
    private final double intercept;
    private final int dof;
    private final double mse;
    private final double[] stdErrors;
    private final double[] pvalues;

    /**
     * Store the results of a weighted linear regression into a container class
     *
     * @param coefficients An array of the coefficients+intercept found by the weighted linear regression.
     * @param intercept Whether or not the weighted linear regression computed an intercept. If true, then the
     *        last value of the coefficients array is taken as the intercept. If false, the intercept is
     *        set to 0.
     * @param dof: The degress of freedom of the weighted linear regression
     * @param mse: The mean square error of the weighted linear regression
     * @param stdErrors: The standard errors of each coefficient of the weighted linear regression
     * @param pvalues: The pvalues of each coefficient of the weighted linear regression
     *
     */
    public WeightedLinearRegressionResults(double[][] coefficients, boolean intercept, int dof, double mse,
            double[] stdErrors, double[] pvalues) {
        //if intercept is true
        if (intercept) {
            double[] rawCoeffs = MatrixUtils.getCol(coefficients, 0);
            this.coefficients = java.util.Arrays
                    .stream(rawCoeffs, 0, rawCoeffs.length - 1)
                    .toArray();
            this.intercept = rawCoeffs[rawCoeffs.length - 1];
        } else {
            this.coefficients = MatrixUtils.getCol(coefficients, 0);
            this.intercept = 0.0;
        }

        this.dof = dof;
        this.mse = mse;
        this.stdErrors = stdErrors;
        this.pvalues = pvalues;

    }

    /**
     * Given a matrix of features, return the model's predictions over each datapoint.
     *
     * @param x: A feature matrix, where each row corresponds to a single datapoint.
     *
     * @return y: The values of the computed function for each datapoint x[i]
     */
    public double[] predict(double[][] x) throws IllegalArgumentException {
        if (x[0].length != this.coefficients.length) {
            throw new IllegalArgumentException(
                    String.format("Num feature mismatch: Number of columns in x (%d)", x[0].length) +
                            String.format(" must match number of coefficients (%d)", this.coefficients.length));
        }
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = this.intercept;
            for (int j = 0; j < this.coefficients.length; j++) {
                y[i] += x[i][j] * this.coefficients[j];
            }
        }
        return y;
    }

    /**
     * @return An array of the coefficients found by the weighted linear regression
     */
    public double[] getCoefficients() {
        return this.coefficients;
    }

    /**
     * @return The intercept found by the weighted linear regression
     */
    public double getIntercept() {
        return this.intercept;
    }

    /**
     * @return mse: The mean square error of the weighted linear regression.
     */
    public double getMSE() {
        return this.mse;
    }

    /**
     * @return stdError: The standard error of each coefficient of the weighted linear regression.
     */
    public double[] getStdErrors() {
        return this.stdErrors;
    }

    public double[] getPValues() {
        return this.pvalues;
    }

    public double[] getConf(double alpha) {
        TDistribution tdist = new TDistribution(this.dof);
        double q = tdist.inverseCumulativeProbability(1 - alpha / 2);
        return Arrays.stream(this.stdErrors).map(x -> q * x).toArray();
    }
}
