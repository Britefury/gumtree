package com.github.gumtreediff.matchers.heuristic.fgp;

import org.omg.PortableInterceptor.INACTIVE;
import org.omg.PortableInterceptor.Interceptor;

import java.util.*;

/**
 * Created by Geoff on 06/04/2016.
 */
public class FeatureVector {
    private int indices[], values[];
    private int sum;

    public FeatureVector() {
        indices = new int[0];
        values = new int[0];
        sum = 0;
    }

    public FeatureVector(FeatureVector b) {
        this.indices = new int[b.indices.length];
        this.values = new int[b.values.length];
        System.arraycopy(b.indices, 0, this.indices, 0, b.indices.length);
        System.arraycopy(b.values, 0, this.values, 0, b.values.length);
        this.sum = b.sum;
    }

    public FeatureVector(List<Integer> indices, List<Integer> values) {
        if (indices.size() != values.size()) {
            throw new RuntimeException("indices.size() (" + indices.size() + ") !=, values.size() (" + values.size() + ")");
        }
        this.indices = new int[indices.size()];
        this.values = new int[values.size()];
        for (int i = 0; i < this.indices.length; i++) {
            this.indices[i] = indices.get(i);
            this.values[i] = values.get(i);
        }
        updateSum();
    }

    public FeatureVector(Map<Integer, Integer> hist) {
        ArrayList<Integer> indices = new ArrayList<>();
        indices.addAll(hist.keySet());
        this.indices = new int[indices.size()];
        for (int i = 0; i < this.indices.length; i++) {
            this.indices[i] = indices.get(i);
        }
        Arrays.sort(this.indices);
        this.values = new int[indices.size()];
        for (int i = 0; i < this.indices.length; i++) {
            values[i] = hist.get(this.indices[i]);
        }
        updateSum();
    }

    private FeatureVector(int indices[], int values[]) {
        this.indices = indices;
        this.values = values;
        updateSum();
    }

    private FeatureVector(int indices[], int values[], int sum) {
        this.indices = indices;
        this.values = values;
        this.sum = sum;
    }


    private void updateSum() {
        sum = 0;
        for (int i = 0; i < this.values.length; i++) {
            sum += this.values[i];
        }
    }


    public int get(int i) {
        int index = Arrays.binarySearch(indices, i);
        if (index >= 0 && index < values.length) {
            return values[index];
        }
        else {
            return 0;
        }
    }

    public void set(int i, int x) {
        int index = Arrays.binarySearch(indices, i);
        if (index >= 0) {
            int d = x - values[index];
            values[index] = x;
            sum += d;
        }
        else {
            index = -(index + 1);

            int len = indices.length + 1;
            int ndx[] = new int[len];
            int val[] = new int[len];
            System.arraycopy(this.indices, 0, ndx, 0, index);
            System.arraycopy(this.values, 0, val, 0, index);
            System.arraycopy(this.indices, index, ndx, index+1, indices.length-index);
            System.arraycopy(this.values, index, val, index+1, indices.length-index);
            this.indices = ndx;
            this.values = val;
            this.indices[index] = i;
            this.values[index] = x;

            sum += x;
        }
    }

    public int getSum() {
        return sum;
    }


    public Iterable<int[]> pairIter(FeatureVector b) {
        return new Iterable<int[]>() {
            @Override
            public Iterator<int[]> iterator() {
                return new Iterator<int[]>() {
                    int i = 0, j = 0;
                    int x = i < indices.length ? indices[i] : -1;
                    int y = j < b.indices.length ? b.indices[j] : -1;

                    @Override
                    public boolean hasNext() {
                        return i < indices.length || j < b.indices.length;
                    }

                    @Override
                    public int[] next() {
                        int result[] = null;
                        if (x != -1 && (x < y || y == -1)) {
                            result = new int[] {x, values[i], 0};
                            i++;
                            x = i < indices.length ? indices[i] : -1;
                            return result;
                        }
                        else if (y != -1 && (y < x || x == -1)) {
                            result = new int[] {y, 0, b.values[j]};
                            j++;
                            y = j < b.indices.length ? b.indices[j] : -1;
                            return result;
                        }
                        else if (x != -1 && y != -1 && x == y) {
                            // x == y
                            result = new int[] {x, values[i], b.values[j]};
                            i++;
                            j++;
                            x = i < indices.length ? indices[i] : -1;
                            y = j < b.indices.length ? b.indices[j] : -1;
                            return result;
                        }
                        throw new NoSuchElementException();
                    }
                };
            }
        };
    }


    public double jaccardSimilarity(FeatureVector b) {
        double intersection = 0.0, union = 0.0;
        for (int iab[]: pairIter(b)) {
            intersection += Math.min(iab[1], iab[2]);
            union += Math.max(iab[1], iab[2]);
        }
        if (union == 0.0) {
            if (intersection != 0.0) {
                throw new RuntimeException("intersection != 0 && union == 0; cannot divide");
            }
            return 0.0;
        }
        else {
            return intersection / union;
        }
    }


    public double[] jaccardSimilarityParts(FeatureVector b) {
        double intersection = 0.0, union = 0.0;
        for (int iab[]: pairIter(b)) {
            intersection += Math.min(iab[1], iab[2]);
            union += Math.max(iab[1], iab[2]);
        }
        if (union == 0.0) {
            if (intersection != 0.0) {
                throw new RuntimeException("intersection != 0 && union == 0");
            }
            return new double[] {0.0, 0.0};
        }
        else {
            return new double[] {intersection, union};
        }
    }


    public FeatureVector add(FeatureVector b) {
        ArrayList<Integer> ndx = new ArrayList<>();
        ArrayList<Integer> val = new ArrayList<>();
        for (int iab[]: pairIter(b)) {
            ndx.add(iab[0]);
            val.add(iab[1] + iab[2]);
        }
        return new FeatureVector(ndx, val);
    }

    public FeatureVector sub(FeatureVector b) {
        ArrayList<Integer> ndx = new ArrayList<>();
        ArrayList<Integer> val = new ArrayList<>();
        for (int iab[]: pairIter(b)) {
            int v = iab[1] - iab[2];
            if (v != 0) {
                ndx.add(iab[0]);
                val.add(v);
            }
        }
        return new FeatureVector(ndx, val);
    }

    public FeatureVector mul(FeatureVector b) {
        ArrayList<Integer> ndx = new ArrayList<>();
        ArrayList<Integer> val = new ArrayList<>();
        for (int iab[]: pairIter(b)) {
            int v = iab[1] * iab[2];
            if (v != 0) {
                ndx.add(iab[0]);
                val.add(v);
            }
        }
        return new FeatureVector(ndx, val);
    }

    public FeatureVector mul(int b) {
        if (b == 0) {
            return new FeatureVector();
        }
        else {
            int val[] = new int[values.length];
            for (int i = 0; i < values.length; i++) {
                val[i] = this.values[i] * b;
            }
            return new FeatureVector(indices, val, sum * b);
        }
    }

    public FeatureVector intersect(FeatureVector b) {
        ArrayList<Integer> ndx = new ArrayList<>();
        ArrayList<Integer> val = new ArrayList<>();
        for (int iab[]: pairIter(b)) {
            int v = Math.min(iab[1], iab[2]);
            if (v != 0) {
                ndx.add(iab[0]);
                val.add(v);
            }
        }
        return new FeatureVector(ndx, val);
    }

    public FeatureVector union(FeatureVector b) {
        ArrayList<Integer> ndx = new ArrayList<>();
        ArrayList<Integer> val = new ArrayList<>();
        for (int iab[]: pairIter(b)) {
            int v = Math.max(iab[1], iab[2]);
            if (v != 0) {
                ndx.add(iab[0]);
                val.add(v);
            }
        }
        return new FeatureVector(ndx, val);
    }

    public FeatureVector abs() {
        int val[] = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            val[i] = Math.abs(this.values[i]);
        }
        return new FeatureVector(indices, val);
    }

    public FeatureVector negated() {
        int val[] = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            val[i] = -this.values[i];
        }
        return new FeatureVector(indices, val);
    }


    public boolean equals(Object x) {
        if (x instanceof FeatureVector) {
            FeatureVector fx = (FeatureVector)x;
            return Arrays.equals(indices, fx.indices) && Arrays.equals(values, fx.values);
        }
        else {
            return false;
        }
    }

    public int hashCode() {
        return Arrays.asList(indices).hashCode() ^ Arrays.asList(values).hashCode();
    }


    public String toString() {
        StringBuilder x = new StringBuilder();
        x.append("FeatureVector(");
        for (int i = 0; i < indices.length; i++) {
            x.append(indices[i]);
            x.append(":");
            x.append(values[i]);
            x.append(",");
        }
        x.append(")");
        return x.toString();
    }
}
