package com.github.gumtreediff.test;

import com.github.gumtreediff.matchers.heuristic.fgp.FeatureVector;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Geoff on 14/04/2016.
 */
public class TestFeatureVector extends TestCase {
    public void testConstructorEmpty() {
        FeatureVector a = new FeatureVector();
        FeatureVector b = new FeatureVector();

        assertEquals(a, a);
        assertEquals(b, a);
        assertEquals(0.0, a.getSum());
    }

    public void testConstructorLists() {
        List<Integer> indices = Arrays.asList(new Integer[] {1, 2, 4, 8});
        List<Double> values = Arrays.asList(new Double[] {3.0, 5.0, 7.0, 9.0});
        FeatureVector a = new FeatureVector(indices, values);
        FeatureVector b = new FeatureVector(indices, values);

        assertEquals(b, a);
        assertEquals(0.0, a.get(0));
        assertEquals(3.0, a.get(1));
        assertEquals(5.0, a.get(2));
        assertEquals(0.0, a.get(3));
        assertEquals(7.0, a.get(4));
        assertEquals(0.0, a.get(5));
        assertEquals(0.0, a.get(6));
        assertEquals(0.0, a.get(7));
        assertEquals(9.0, a.get(8));
        assertEquals(0.0, a.get(9));
    }

    public void testConstructorMap() {
        HashMap<Integer, Double> map = new HashMap<>();
        map.put(1, 3.0);
        map.put(2, 5.0);
        map.put(4, 7.0);
        map.put(8, 9.0);
        FeatureVector a = new FeatureVector(map);
        FeatureVector b = new FeatureVector(map);

        assertEquals(b, a);
        assertEquals(0.0, a.get(0));
        assertEquals(3.0, a.get(1));
        assertEquals(5.0, a.get(2));
        assertEquals(0.0, a.get(3));
        assertEquals(7.0, a.get(4));
        assertEquals(0.0, a.get(5));
        assertEquals(0.0, a.get(6));
        assertEquals(0.0, a.get(7));
        assertEquals(9.0, a.get(8));
        assertEquals(0.0, a.get(9));
    }

    public void testAccessors() {
        FeatureVector a = new FeatureVector();
        FeatureVector b = new FeatureVector();

        assertEquals(0.0, a.get(0));
        assertEquals(0.0, a.get(1));
        assertEquals(0.0, a.getSum());
        assertEquals(a, b);

        a.set(0, 1);
        assertEquals(1.0, a.get(0));
        assertEquals(0.0, a.get(1));
        assertEquals(1.0, a.getSum());
        assertEquals(0.0, b.getSum());

        a.set(0, 2);
        assertEquals(2.0, a.get(0));
        assertEquals(0.0, a.get(1));
        assertEquals(2.0, a.getSum());
        assertEquals(0.0, b.getSum());
    }

    public void testPairIter1() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                                            Arrays.asList(new Double[] {3.0, 5.0, 7.0, 9.0}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                                            Arrays.asList(new Double[] {-3.0, -5.0, -7.0, -9.0}));
        int expected[][] = new int[][] {
                new int[] {0, 0, -3},
                new int[] {1, 3, 0},
                new int[] {2, 5, -5},
                new int[] {4, 7, -7},
                new int[] {8, 9, 0},
                new int[] {11, 0, -9},
        };
        int i = 0;
        for (FeatureVector.ValuePair iab: a.pairIter(b)) {
            assertEquals(iab.index, expected[i][0]);
            assertEquals(iab.a, (double)expected[i][1]);
            assertEquals(iab.b, (double)expected[i][2]);
            i++;
        }
    }

    public void testPairIter2() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                                            Arrays.asList(new Double[] {3.0, 5.0, 7.0, 9.0}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 11}),
                                            Arrays.asList(new Double[] {-3.0, -5.0, -7.0, -9.0}));
        int expected[][] = new int[][] {
                new int[] {1, 3, -3},
                new int[] {2, 5, -5},
                new int[] {4, 7, -7},
                new int[] {8, 9, 0},
                new int[] {11, 0, -9},
        };
        int i = 0;
        for (FeatureVector.ValuePair iab: a.pairIter(b)) {
            assertEquals(iab.index, expected[i][0]);
            assertEquals(iab.a, (double)expected[i][1]);
            assertEquals(iab.b, (double)expected[i][2]);
            i++;
        }
    }

    public void testPairIter3() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {3.0, 5.0, 7.0, 9.0}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 8}),
                Arrays.asList(new Double[] {-3.0, -5.0, -7.0, -9.0}));
        int expected[][] = new int[][] {
                new int[] {0, 0, -3},
                new int[] {1, 3, 0},
                new int[] {2, 5, -5},
                new int[] {4, 7, -7},
                new int[] {8, 9, -9},
        };
        int i = 0;
        for (FeatureVector.ValuePair iab: a.pairIter(b)) {
            assertEquals(iab.index, expected[i][0]);
            assertEquals(iab.a, (double)expected[i][1]);
            assertEquals(iab.b, (double)expected[i][2]);
            i++;
        }
    }

    public void testAdd() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {3.0, 5.0, 7.0, 9.0}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                Arrays.asList(new Double[] {13.0, 15.0, 17.0, 19.0}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {0, 1, 2, 4, 8, 11}),
                Arrays.asList(new Double[] {13.0, 3.0, 20.0, 24.0, 9.0, 19.0}));
        assertEquals(c, a.add(b));
    }

    public void testSub() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {3.0, 5.0, 7.0, 9.0}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                Arrays.asList(new Double[] {13.0, 15.0, 17.0, 19.0}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {0, 1, 2, 4, 8, 11}),
                Arrays.asList(new Double[] {13.0, -3.0, 10.0, 10.0, -9.0, 19.0}));
        assertEquals(c, b.sub(a));
    }

    public void testMul() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {3.0, 5.0, 7.0, 9.0}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                Arrays.asList(new Double[] {13.0, 15.0, 17.0, 19.0}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {2, 4}),
                Arrays.asList(new Double[] {75.0, 119.0}));
        assertEquals(c, a.scale(b));
    }

    public void testMulScalar() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {3.0, 5.0, 7.0, 9.0}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {6.0, 10.0, 14.0, 18.0}));
        assertEquals(c, a.scale(2));
        assertEquals(new FeatureVector(), a.scale(0));
    }

    public void testAbs() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {3.0, -5.0, 7.0, -9.0}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {3.0, 5.0, 7.0, 9.0}));
        assertEquals(c, a.abs());
    }

    public void testNegate() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {3.0, -5.0, 7.0, -9.0}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {-3.0, 5.0, -7.0, 9.0}));
        assertEquals(c, a.negated());
    }


    public void testIntersection() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {3.0, 15.0, 7.0, 19.0}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                Arrays.asList(new Double[] {13.0, 5.0, 17.0, 9.0}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {2, 4}),
                Arrays.asList(new Double[] {5.0, 7.0}));
        assertEquals(c, a.intersect(b));
    }

    public void testUnion() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {3.0, 15.0, 7.0, 19.0}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                Arrays.asList(new Double[] {13.0, 5.0, 17.0, 9.0}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {0, 1, 2, 4, 8, 11}),
                Arrays.asList(new Double[] {13.0, 3.0, 15.0, 17.0, 19.0, 9.0}));
        assertEquals(c, a.union(b));
    }

    public void testJaccardSimilarity() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Double[] {3.0, 15.0, 7.0, 19.0}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                Arrays.asList(new Double[] {13.0, 5.0, 17.0, 9.0}));
        assertEquals(12.0 / 76.0, a.jaccardSimilarity(b));
    }
}
