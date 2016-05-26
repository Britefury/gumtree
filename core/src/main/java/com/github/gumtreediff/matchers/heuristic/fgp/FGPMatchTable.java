package com.github.gumtreediff.matchers.heuristic.fgp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Geoff on 24/05/2016.
 */
public class FGPMatchTable {
    private final int SIMTYPE_JACCARD = 0;
    private final int SIMTYPE_UPPERBOUND = 1;
    private final int SIMTYPE_LOCAL_JACCARD = 2;

    private FGPNode a[], b[];
    private int nA, nB;
    private double scores[];


    protected FGPMatchTable(List<FGPNode> a, List<FGPNode> b) {
        nA = a.size();
        nB = b.size();

        this.a = a.toArray(new FGPNode[nA]);
        this.b = b.toArray(new FGPNode[nB]);

        for (int i = 0; i < nA; i++) {
            a.get(i).setMatchId(i);
        }

        for (int i = 0; i < nB; i++) {
            b.get(i).setMatchId(i);
        }

        scores = new double[nA * nB * 3];
        Arrays.fill(scores, -1.0);
    }


    private int index(FGPNode x, FGPNode y, int simType) {
        return (x.getMatchId() * nB + y.getMatchId()) * 3 + simType;
    }

    private double computeContextSimilarity(FGPNode x, FGPNode y, int simType) {
        double contextSim = 1.0;
        if (x.distFromRoot == y.distFromRoot) {
            if (x.parent != null || y.parent != null) {
                contextSim = computeSimilarity(x.parent, y.parent, simType);
            }
        }
        else if (x.distFromRoot > y.distFromRoot) {
            if (y.parent == null) {
                contextSim = computeSimilarity(x.parent, y, simType);
            }
            else {
                contextSim = Math.max(computeSimilarity(x.parent, y, simType),
                        computeSimilarity(x.parent, y.parent, simType));
            }
        }
        else if (x.distFromRoot < y.distFromRoot) {
            if (x.parent == null) {
                contextSim = computeSimilarity(x, y.parent, simType);
            }
            else {
                contextSim = Math.max(computeSimilarity(x, y.parent, simType),
                        computeSimilarity(x.parent, y.parent, simType));
            }
        }
        return contextSim;
    }

    private double computeSimilarity(FGPNode x, FGPNode y, int simType) {
        int ndx = index(x, y, simType);
        double score = scores[ndx];
        if (score >= 0.0) {
            return score;
        }
        if (simType == SIMTYPE_LOCAL_JACCARD) {
            score = x.nodeFeatures.jaccardSimilarity(y.nodeFeatures);
            scores[ndx] = score;
            return score;
        }
        else {
            double localSim = 0.0;
            if (simType == SIMTYPE_JACCARD) {
                localSim = computeSimilarity(x, y, SIMTYPE_LOCAL_JACCARD);
            }
            else if (simType == SIMTYPE_UPPERBOUND) {
                localSim = x.nodeFeatures.jaccardSimilarityUpperBound(y.nodeFeatures);
            }

            double contextSim = computeContextSimilarity(x, y, simType);
            score = contextSim * localSim;
            scores[ndx] = score;
            return score;
        }
    }


    public double localSimilarityUpperBound(FGPNode x, FGPNode y) {
        return x.nodeFeatures.jaccardSimilarityUpperBound(y.nodeFeatures);
    }

    public double localSimilarity(FGPNode x, FGPNode y) {
        return computeSimilarity(x, y, SIMTYPE_LOCAL_JACCARD);
    }

    public double inContextSimilarityUpperBound(FGPNode x, FGPNode y) {
        return computeSimilarity(x, y, SIMTYPE_UPPERBOUND);
    }

    public double inContextSimilarity(FGPNode x, FGPNode y) {
        return computeSimilarity(x, y, SIMTYPE_JACCARD);
    }

    public double contextSimilarityUpperBound(FGPNode x, FGPNode y) {
        return computeContextSimilarity(x, y, SIMTYPE_UPPERBOUND);
    }

    public double contextSimilarity(FGPNode x, FGPNode y) {
        return computeContextSimilarity(x, y, SIMTYPE_JACCARD);
    }
}
