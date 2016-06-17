package com.github.gumtreediff.matchers.heuristic.fgp;

import org.omg.PortableInterceptor.INACTIVE;
import org.omg.PortableInterceptor.Interceptor;

import java.util.*;

/**
 * Created by Geoff on 06/04/2016.
 */
public class FeatureVector {
    private int indices[];
    private double values[];
    private double sum;

    public FeatureVector() {
        indices = new int[0];
        values = new double[0];
        sum = 0.0;
    }

    public FeatureVector(FeatureVector b) {
        this.indices = new int[b.indices.length];
        this.values = new double[b.values.length];
        System.arraycopy(b.indices, 0, this.indices, 0, b.indices.length);
        System.arraycopy(b.values, 0, this.values, 0, b.values.length);
        this.sum = b.sum;
    }

    public FeatureVector(List<Integer> indices, List<Double> values) {
        if (indices.size() != values.size()) {
            throw new RuntimeException("indices.size() (" + indices.size() + ") !=, values.size() (" + values.size() + ")");
        }
        this.indices = new int[indices.size()];
        this.values = new double[values.size()];
        for (int i = 0; i < this.indices.length; i++) {
            this.indices[i] = indices.get(i);
            this.values[i] = values.get(i);
        }
        updateSum();
    }

    public FeatureVector(Map<Integer, Double> hist) {
        ArrayList<Integer> indices = new ArrayList<>();
        indices.addAll(hist.keySet());
        this.indices = new int[indices.size()];
        for (int i = 0; i < this.indices.length; i++) {
            this.indices[i] = indices.get(i);
        }
        Arrays.sort(this.indices);
        this.values = new double[indices.size()];
        for (int i = 0; i < this.indices.length; i++) {
            values[i] = hist.get(this.indices[i]);
        }
        updateSum();
    }

    private FeatureVector(int indices[], double values[]) {
        this.indices = indices;
        this.values = values;
        updateSum();
    }

    private FeatureVector(int indices[], double values[], double sum) {
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


    public double get(int i) {
        int index = Arrays.binarySearch(indices, i);
        if (index >= 0 && index < values.length) {
            return values[index];
        }
        else {
            return 0;
        }
    }

    public void set(int i, double x) {
        int index = Arrays.binarySearch(indices, i);
        if (index >= 0) {
            double d = x - values[index];
            values[index] = x;
            sum += d;
        }
        else {
            index = -(index + 1);

            int len = indices.length + 1;
            int ndx[] = new int[len];
            double val[] = new double[len];
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

    public double getSum() {
        return sum;
    }


    /**
     * Create an iterable that extracts values from @this and @b. It generates ValuePair instances
     * that combine an index with an associated value from @this and @b
     *
     * @param b another feature vector
     * @return an iterable
     */
    public Iterable<ValuePair> pairIter(FeatureVector b) {
        return new Iterable<ValuePair>() {
            @Override
            public Iterator<ValuePair> iterator() {
                return new Iterator<ValuePair>() {
                    int i = 0, j = 0;
                    int x = i < indices.length ? indices[i] : -1;
                    int y = j < b.indices.length ? b.indices[j] : -1;

                    @Override
                    public boolean hasNext() {
                        return i < indices.length || j < b.indices.length;
                    }

                    @Override
                    public ValuePair next() {
                        ValuePair result = null;
                        if (x != -1 && (x < y || y == -1)) {
                            result = new ValuePair(x, values[i], 0.0);
                            i++;
                            x = i < indices.length ? indices[i] : -1;
                            return result;
                        }
                        else if (y != -1 && (y < x || x == -1)) {
                            result = new ValuePair(y, 0.0, b.values[j]);
                            j++;
                            y = j < b.indices.length ? b.indices[j] : -1;
                            return result;
                        }
                        else if (x != -1 && y != -1 && x == y) {
                            // x == y
                            result = new ValuePair(x, values[i], b.values[j]);
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
        for (ValuePair iab: pairIter(b)) {
            intersection += Math.min(iab.a, iab.b);
            union += Math.max(iab.a, iab.b);
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
        for (ValuePair iab: pairIter(b)) {
            intersection += Math.min(iab.a, iab.b);
            union += Math.max(iab.a, iab.b);
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


    public double jaccardSimilarityUpperBound(FeatureVector b) {
        double intersection = Math.min(sum, b.sum);
        double union = Math.max(sum, b.sum);
        if (union == 0.0) {
            if (intersection != 0.0) {
                throw new RuntimeException("intersection != 0 && union == 0");
            }
            return 0.0;
        }
        else {
            return intersection / union;
        }
    }


    public double cost(FeatureVector b) {
        double cost = 0.0;
        for (ValuePair iab: pairIter(b)) {
            double c = Math.max(iab.a, iab.b) - Math.min(iab.a, iab.b);
            cost += c;
        }
        return cost;
    }

    public double costLowerBound(FeatureVector b) {
        return Math.max(sum, b.sum) - Math.min(sum, b.sum);
    }

    public FeatureVector add(FeatureVector b) {
        ArrayList<Integer> ndx = new ArrayList<>();
        ArrayList<Double> val = new ArrayList<>();
        for (ValuePair iab: pairIter(b)) {
            ndx.add(iab.index);
            val.add(iab.a + iab.b);
        }
        return new FeatureVector(ndx, val);
    }

    public FeatureVector sub(FeatureVector b) {
        ArrayList<Integer> ndx = new ArrayList<>();
        ArrayList<Double> val = new ArrayList<>();
        for (ValuePair iab: pairIter(b)) {
            double v = iab.a - iab.b;
            if (v != 0) {
                ndx.add(iab.index);
                val.add(v);
            }
        }
        return new FeatureVector(ndx, val);
    }

    public FeatureVector scale(FeatureVector b) {
        ArrayList<Integer> ndx = new ArrayList<>();
        ArrayList<Double> val = new ArrayList<>();
        for (ValuePair iab: pairIter(b)) {
            double v = iab.a * iab.b;
            if (v != 0) {
                ndx.add(iab.index);
                val.add(v);
            }
        }
        return new FeatureVector(ndx, val);
    }

    public FeatureVector scale(double b) {
        if (b == 0.0) {
            return new FeatureVector();
        }
        else {
            double val[] = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                val[i] = this.values[i] * b;
            }
            return new FeatureVector(indices, val, sum * b);
        }
    }

    public FeatureVector intersect(FeatureVector b) {
        ArrayList<Integer> ndx = new ArrayList<>();
        ArrayList<Double> val = new ArrayList<>();
        for (ValuePair iab: pairIter(b)) {
            double v = Math.min(iab.a, iab.b);
            if (v != 0) {
                ndx.add(iab.index);
                val.add(v);
            }
        }
        return new FeatureVector(ndx, val);
    }

    public FeatureVector union(FeatureVector b) {
        ArrayList<Integer> ndx = new ArrayList<>();
        ArrayList<Double> val = new ArrayList<>();
        for (ValuePair iab: pairIter(b)) {
            double v = Math.max(iab.a, iab.b);
            if (v != 0) {
                ndx.add(iab.index);
                val.add(v);
            }
        }
        return new FeatureVector(ndx, val);
    }

    public FeatureVector abs() {
        double val[] = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            val[i] = Math.abs(this.values[i]);
        }
        return new FeatureVector(indices, val);
    }

    public FeatureVector negated() {
        double val[] = new double[values.length];
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


    public static class ValuePair {
        public int index;
        public double a, b;

        public ValuePair(int index, double a, double b) {
            this.index = index;
            this.a = a;
            this.b = b;
        }
    }
}
