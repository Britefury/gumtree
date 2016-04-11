package com.github.gumtreediff.matchers.heuristic.fgp;

import java.util.Arrays;

/**
 * Created by Geoff on 06/04/2016.
 */
public class FeatureVector {
    private int v[];
    private int sum;

    public FeatureVector() {
        v = new int[0];
        sum = 0;
    }

    public FeatureVector(FeatureVector b) {
        this.v = new int[b.v.length];
        System.arraycopy(b.v, 0, this.v, 0, b.v.length);
        this.sum = b.sum;
    }

    private FeatureVector(int v[]) {
        int size = v.length - 1;
        while (size >= 0) {
            if (v[size] != 0) {
                break;
            }
            size--;
        }
        if (size < v.length - 1) {
            this.v = new int[size+1];
            System.arraycopy(v, 0, this.v, 0, this.v.length);
        }
        else {
            this.v = v;
        }
        updateSum();
    }

    private FeatureVector(int v[], int sum) {
        this.v = v;
        this.sum = sum;
    }


    private void updateSum() {
        sum = 0;
        for (int i = 0; i < this.v.length; i++) {
            sum += this.v[i];
        }
    }


    public int get(int i) {
        if (i < v.length) {
            return this.v[i];
        }
        else {
            return 0;
        }
    }

    public void set(int i, int x) {
        if (i >= this.v.length) {
            int v[] = new int[i+1];
            System.arraycopy(this.v, 0, v, 0, this.v.length);
            this.v = v;
        }
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
        int vOut[] = new int[Math.max(v.length, b.v.length)];
        for (int i = 0; i < common; i++) {
            vOut[i] = this.v[i] + b.v[i];
        }

        if (common < v.length) {
            System.arraycopy(v, common, vOut, common, v.length - common);
        }
        else if (common < b.v.length) {
            System.arraycopy(b.v, common, vOut, common, b.v.length - common);
        }
        return new FeatureVector(vOut, sum + b.sum);
    }

    public void accum(FeatureVector b) {
        if (b.v.length > v.length) {
            int newV[] = new int[b.v.length];
            for (int i = 0; i < v.length; i++) {
                newV[i] = v[i] + b.v[i];
            }
            System.arraycopy(b.v, v.length, newV, v.length, b.v.length - v.length);
            v = newV;
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
        int vOut[] = new int[Math.max(v.length, b.v.length)];
        for (int i = 0; i < common; i++) {
            vOut[i] = this.v[i] - b.v[i];
        }

        if (common < v.length) {
            System.arraycopy(v, common, vOut, common, v.length - common);
        }
        else if (common < b.v.length) {
            for (int i = common; i < b.v.length; i++) {
                vOut[i] = -b.v[i];
            }
        }
        return new FeatureVector(vOut);
    }

    public FeatureVector mul(FeatureVector b) {
        int common = Math.min(v.length, b.v.length);
        int vOut[] = new int[Math.max(v.length, b.v.length)];
        for (int i = 0; i < common; i++) {
            vOut[i] = this.v[i] * b.v[i];
        }
        return new FeatureVector(vOut);
    }

    public FeatureVector mul(int b) {
        if (b == 0) {
            return new FeatureVector();
        }
        else {
            int vOut[] = new int[v.length];
            for (int i = 0; i < v.length; i++) {
                vOut[i] = this.v[i] * b;
            }
            return new FeatureVector(vOut, sum * b);
        }
    }

    public FeatureVector intersect(FeatureVector b) {
        int common = Math.min(v.length, b.v.length);
        int vOut[] = new int[Math.max(v.length, b.v.length)];
        for (int i = 0; i < common; i++) {
            vOut[i] = Math.min(this.v[i], b.v[i]);
        }
        return new FeatureVector(vOut);
    }

    public FeatureVector union(FeatureVector b) {
        int common = Math.min(v.length, b.v.length);
        int vOut[] = new int[Math.max(v.length, b.v.length)];
        for (int i = 0; i < common; i++) {
            vOut[i] = this.v[i] + b.v[i];
        }

        if (common < v.length) {
            System.arraycopy(v, common, vOut, common, v.length - common);
        }
        else if (common < b.v.length) {
            System.arraycopy(b.v, common, vOut, common, b.v.length - common);
        }
        return new FeatureVector(vOut);
    }

    public FeatureVector abs() {
        int vOut[] = new int[v.length];
        for (int i = 0; i < v.length; i++) {
            vOut[i] = Math.abs(v[i]);
        }
        return new FeatureVector(vOut);
    }

    public FeatureVector negated() {
        int vOut[] = new int[v.length];
        for (int i = 0; i < v.length; i++) {
            vOut[i] = -v[i];
        }
        return new FeatureVector(vOut, -sum);
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
