package com.github.gumtreediff.test;

import com.github.gumtreediff.matchers.heuristic.fgp.FeatureVector;
import junit.framework.TestCase;

import java.util.ArrayList;
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
        assertEquals(0, a.getSum());
    }

    public void testConstructorLists() {
        List<Integer> indices = Arrays.asList(new Integer[] {1, 2, 4, 8});
        List<Integer> values = Arrays.asList(new Integer[] {3, 5, 7, 9});
        FeatureVector a = new FeatureVector(indices, values);
        FeatureVector b = new FeatureVector(indices, values);

        assertEquals(b, a);
        assertEquals(0, a.get(0));
        assertEquals(3, a.get(1));
        assertEquals(5, a.get(2));
        assertEquals(0, a.get(3));
        assertEquals(7, a.get(4));
        assertEquals(0, a.get(5));
        assertEquals(0, a.get(6));
        assertEquals(0, a.get(7));
        assertEquals(9, a.get(8));
        assertEquals(0, a.get(9));
    }

    public void testConstructorMap() {
        HashMap<Integer, Integer> map = new HashMap<>();
        map.put(1, 3);
        map.put(2, 5);
        map.put(4, 7);
        map.put(8, 9);
        FeatureVector a = new FeatureVector(map);
        FeatureVector b = new FeatureVector(map);

        assertEquals(b, a);
        assertEquals(0, a.get(0));
        assertEquals(3, a.get(1));
        assertEquals(5, a.get(2));
        assertEquals(0, a.get(3));
        assertEquals(7, a.get(4));
        assertEquals(0, a.get(5));
        assertEquals(0, a.get(6));
        assertEquals(0, a.get(7));
        assertEquals(9, a.get(8));
        assertEquals(0, a.get(9));
    }

    public void testAccessors() {
        FeatureVector a = new FeatureVector();
        FeatureVector b = new FeatureVector();

        assertEquals(0, a.get(0));
        assertEquals(0, a.get(1));
        assertEquals(0, a.getSum());
        assertEquals(a, b);

        a.set(0, 1);
        assertEquals(1, a.get(0));
        assertEquals(0, a.get(1));
        assertEquals(1, a.getSum());
        assertEquals(0, b.getSum());

        a.set(0, 2);
        assertEquals(2, a.get(0));
        assertEquals(0, a.get(1));
        assertEquals(2, a.getSum());
        assertEquals(0, b.getSum());
    }

    public void testPairIter1() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                                            Arrays.asList(new Integer[] {3, 5, 7, 9}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                                            Arrays.asList(new Integer[] {-3, -5, -7, -9}));
        int expected[][] = new int[][] {
                new int[] {0, 0, -3},
                new int[] {1, 3, 0},
                new int[] {2, 5, -5},
                new int[] {4, 7, -7},
                new int[] {8, 9, 0},
                new int[] {11, 0, -9},
        };
        int i = 0;
        for (int iab[]: a.pairIter(b)) {
            assertTrue(Arrays.equals(expected[i], iab));
            i++;
        }
    }

    public void testPairIter2() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                                            Arrays.asList(new Integer[] {3, 5, 7, 9}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 11}),
                                            Arrays.asList(new Integer[] {-3, -5, -7, -9}));
        int expected[][] = new int[][] {
                new int[] {1, 3, -3},
                new int[] {2, 5, -5},
                new int[] {4, 7, -7},
                new int[] {8, 9, 0},
                new int[] {11, 0, -9},
        };
        int i = 0;
        for (int iab[]: a.pairIter(b)) {
            assertTrue(Arrays.equals(expected[i], iab));
            i++;
        }
    }

    public void testPairIter3() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {3, 5, 7, 9}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 8}),
                Arrays.asList(new Integer[] {-3, -5, -7, -9}));
        int expected[][] = new int[][] {
                new int[] {0, 0, -3},
                new int[] {1, 3, 0},
                new int[] {2, 5, -5},
                new int[] {4, 7, -7},
                new int[] {8, 9, -9},
        };
        int i = 0;
        for (int iab[]: a.pairIter(b)) {
            assertTrue(Arrays.equals(expected[i], iab));
            i++;
        }
    }

    public void testAdd() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {3, 5, 7, 9}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                Arrays.asList(new Integer[] {13, 15, 17, 19}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {0, 1, 2, 4, 8, 11}),
                Arrays.asList(new Integer[] {13, 3, 20, 24, 9, 19}));
        assertEquals(c, a.add(b));
    }

    public void testSub() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {3, 5, 7, 9}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                Arrays.asList(new Integer[] {13, 15, 17, 19}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {0, 1, 2, 4, 8, 11}),
                Arrays.asList(new Integer[] {13, -3, 10, 10, -9, 19}));
        assertEquals(c, b.sub(a));
    }

    public void testMul() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {3, 5, 7, 9}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                Arrays.asList(new Integer[] {13, 15, 17, 19}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {2, 4}),
                Arrays.asList(new Integer[] {75, 119}));
        assertEquals(c, a.mul(b));
    }

    public void testMulScalar() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {3, 5, 7, 9}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {6, 10, 14, 18}));
        assertEquals(c, a.mul(2));
        assertEquals(new FeatureVector(), a.mul(0));
    }

    public void testAbs() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {3, -5, 7, -9}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {3, 5, 7, 9}));
        assertEquals(c, a.abs());
    }

    public void testNegate() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {3, -5, 7, -9}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {-3, 5, -7, 9}));
        assertEquals(c, a.negated());
    }


    public void testIntersection() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {3, 15, 7, 19}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                Arrays.asList(new Integer[] {13, 5, 17, 9}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {2, 4}),
                Arrays.asList(new Integer[] {5, 7}));
        assertEquals(c, a.intersect(b));
    }

    public void testUnion() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {3, 15, 7, 19}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                Arrays.asList(new Integer[] {13, 5, 17, 9}));
        FeatureVector c = new FeatureVector(Arrays.asList(new Integer[] {0, 1, 2, 4, 8, 11}),
                Arrays.asList(new Integer[] {13, 3, 15, 17, 19, 9}));
        assertEquals(c, a.union(b));
    }

    public void testJaccardSimilarity() {
        FeatureVector a = new FeatureVector(Arrays.asList(new Integer[] {1, 2, 4, 8}),
                Arrays.asList(new Integer[] {3, 15, 7, 19}));
        FeatureVector b = new FeatureVector(Arrays.asList(new Integer[] {0, 2, 4, 11}),
                Arrays.asList(new Integer[] {13, 5, 17, 9}));
        assertEquals(12.0 / 76.0, a.jaccardSimilarity(b));
    }
}
