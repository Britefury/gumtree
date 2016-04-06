package com.github.gumtreediff.matchers.heuristic.fgp;

import java.util.Arrays;

/**
 * Created by Geoff on 06/04/2016.
 */
public class FeatureVector {
    private int v[];
    private int sum;

    public FeatureVector(int n) {
        v = new int[n];
        sum = 0;
    }

    public FeatureVector(int v[]) {
        this.v = new int[v.length];
        System.arraycopy(v, 0, this.v, 0, v.length);
        updateSum();
    }

    public FeatureVector(FeatureVector b) {
        this.v = new int[b.v.length];
        System.arraycopy(b.v, 0, this.v, 0, b.v.length);
        this.sum = b.sum;
    }


    private void updateSum() {
        sum = 0;
        for (int i = 0; i < this.v.length; i++) {
            sum += this.v[i];
        }
    }


    public int size() {
        return v.length;
    }

    public int get(int i) {
        return this.v[i];
    }

    public void set(int i, int x) {
        int d = x - v[i];
        v[i] = x;
        sum += d;
    }


    public double jaccardSimilarity(FeatureVector b) {
        double intersection = 0.0, union = 0.0;
        int common = Math.min(v.length, b.v.length);
        for (int i = 0; i < common; i++) {
            intersection += Math.min(v[i], b.v[i]);
            union += Math.max(v[i], b.v[i]);
        }
        for (int i = common; i < v.length; i++) {
            union += v[i];
        }
        for (int i = common; i < b.v.length; i++) {
            union += b.v[i];
        }
        return intersection / (union + 1.0e-9);
    }


    public FeatureVector add(FeatureVector b) {
        int common = Math.min(v.length, b.v.length);
        FeatureVector result = new FeatureVector(Math.max(v.length, b.v.length));
        for (int i = 0; i < common; i++) {
            result.v[i] = this.v[i] + b.v[i];
        }

        if (common < v.length) {
            System.arraycopy(v, common, result.v, common, v.length - common);
        }
        else if (common < b.v.length) {
            System.arraycopy(b.v, common, result.v, common, b.v.length - common);
        }
        result.sum = sum + b.sum;
        return result;
    }

    public void accum(FeatureVector b) {
        if (b.v.length > v.length) {
            int newV[] = new int[b.v.length];
            for (int i = 0; i < v.length; i++) {
                newV[i] = v[i] + b.v[i];
            }
            System.arraycopy(b.v, v.length, newV, v.length, b.v.length - v.length);
        }
        else {
            for (int i = 0; i < b.v.length; i++) {
                v[i] += b.v[i];
            }
        }
        sum += b.sum;
    }

    public FeatureVector sub(FeatureVector b) {
        int common = Math.min(v.length, b.v.length);
        FeatureVector result = new FeatureVector(Math.max(v.length, b.v.length));
        for (int i = 0; i < common; i++) {
            result.v[i] = this.v[i] - b.v[i];
        }

        if (common < v.length) {
            System.arraycopy(v, common, result.v, common, v.length - common);
        }
        else if (common < b.v.length) {
            for (int i = common; i < b.v.length; i++) {
                result.v[i] = -b.v[i];
            }
        }
        result.sum = sum - b.sum;
        return result;
    }

    public FeatureVector mul(FeatureVector b) {
        int common = Math.min(v.length, b.v.length);
        FeatureVector result = new FeatureVector(Math.max(v.length, b.v.length));
        for (int i = 0; i < common; i++) {
            result.v[i] = this.v[i] * b.v[i];
        }
        result.updateSum();
        return result;
    }

    public FeatureVector mul(int b) {
        FeatureVector result = new FeatureVector(v.length);
        for (int i = 0; i < v.length; i++) {
            result.v[i] = this.v[i] * b;
        }
        result.sum = sum * b;
        return result;
    }

    public FeatureVector intersect(FeatureVector b) {
        int common = Math.min(v.length, b.v.length);
        FeatureVector result = new FeatureVector(Math.max(v.length, b.v.length));
        for (int i = 0; i < common; i++) {
            result.v[i] = Math.min(this.v[i], b.v[i]);
        }
        result.updateSum();
        return result;
    }

    public FeatureVector union(FeatureVector b) {
        int common = Math.min(v.length, b.v.length);
        FeatureVector result = new FeatureVector(Math.max(v.length, b.v.length));
        for (int i = 0; i < common; i++) {
            result.v[i] = this.v[i] + b.v[i];
        }

        if (common < v.length) {
            System.arraycopy(v, common, result.v, common, v.length - common);
        }
        else if (common < b.v.length) {
            System.arraycopy(b.v, common, result.v, common, b.v.length - common);
        }
        result.sum = sum + b.sum;
        return result;
    }

    public FeatureVector abs() {
        FeatureVector result = new FeatureVector(v.length);
        for (int i = 0; i < v.length; i++) {
            result.v[i] = Math.abs(v[i]);
        }
        result.updateSum();
        return result;
    }

    public FeatureVector negated() {
        FeatureVector result = new FeatureVector(v.length);
        for (int i = 0; i < v.length; i++) {
            result.v[i] = -v[i];
        }
        result.sum = -sum;
        return result;
    }


    public boolean equals(Object x) {
        if (x instanceof FeatureVector) {
            FeatureVector fx = (FeatureVector)x;
            return Arrays.equals(v, fx.v);
        }
        else {
            return false;
        }
    }

    public int hashCode() {
        return Arrays.asList(v).hashCode();
    }


    public String toString() {
        StringBuilder x = new StringBuilder();
        x.append("FeatureVector(");
        for (int i = 0; i < v.length; i++) {
            x.append(v[i]);
            x.append(",");
        }
        x.append(")");
        return x.toString();
    }
}
