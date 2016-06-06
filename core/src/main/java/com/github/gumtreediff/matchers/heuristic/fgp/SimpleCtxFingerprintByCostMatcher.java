package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Register;
import com.github.gumtreediff.tree.ITree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Created by Geoff on 06/06/2016.
 */
@Register(id = "fg-cost")
public class SimpleCtxFingerprintByCostMatcher extends AbstractFingerprintMatcher {
    private static int BOTTOM_UP_HEIGHT_THRESHOLD = 2;
    private static int SIZE_THRESHOLD = 500000;




    public SimpleCtxFingerprintByCostMatcher(ITree src, ITree dst, MappingStore store) {
        super(src, dst, store);
    }


    protected void bottomUpMatch(FingerprintMatchHelper matchHelper, ScoredNodeMapping mappingScorer, FGPNode treeA, FGPNode treeB) {
        long t1 = System.nanoTime();
        ArrayList<FGPNode> nodesA = nodesInUnmatchedSubtrees(treeA, BOTTOM_UP_HEIGHT_THRESHOLD);
        ArrayList<FGPNode> nodesB = nodesInUnmatchedSubtrees(treeB, BOTTOM_UP_HEIGHT_THRESHOLD);

        if (mappingScorer != null) {
            // Add the existing mappings as fixed
            mappingScorer.addMappings(mappings, true);
        }


        // Get the unmached nodes in each side
        ArrayList<FGPNode> unmatchedNodesA = nodesInUnmatchedSubtrees(treeA, -1);
        ArrayList<FGPNode> unmatchedNodesB = nodesInUnmatchedSubtrees(treeB, -1);


        long t2 = System.nanoTime();

        // Gather all matches whose upper bound is less than the threshold
        ArrayList<ScoredMatch> matchesByCostLowerBound = new ArrayList<>();
        for (FGPNode a: nodesA) {
            for (FGPNode b: nodesB) {
                double jaccardUpperBound = a.nodeFeatures.jaccardSimilarityUpperBound(b.nodeFeatures);
                if (jaccardUpperBound > SIM_THRESHOLD) {
                    double costLowerBound = FeatureVectorTable.costMatchLowerBound(a, b);
                    matchesByCostLowerBound.add(new ScoredMatch(costLowerBound, a, b));
                }
            }
        }

        long t3 = System.nanoTime();

        // Convert to heap
        CostMatchComparator matchComparator = new CostMatchComparator();
        PriorityQueue<ScoredMatch> matchesByCostLowerBoundHeap = new PriorityQueue<>(matchComparator);
        PriorityQueue<ScoredMatch> matchesByCostHeap = new PriorityQueue<>(matchComparator);
        matchesByCostLowerBoundHeap.addAll(matchesByCostLowerBound);

        ArrayList<MatchByHeight> matchesByHeight = new ArrayList<>();

        long t4 = System.nanoTime();

        while (!matchesByCostLowerBoundHeap.isEmpty() || !matchesByCostHeap.isEmpty()) {
            // Remove any matches from `matchesByCostHeap` whose cost is greater than the lowest lower bound available
            // as determined by the entry at the head of `matchesByCostLowerBoundHeap`, since no match can be
            // moved from `matchesByCostLowerBoundHeap` that will beat it.
            while (!matchesByCostHeap.isEmpty() &&
                    (matchesByCostLowerBoundHeap.isEmpty() || matchesByCostHeap.peek().score < matchesByCostLowerBoundHeap.peek().score)) {
                ScoredMatch potentialMatch = matchesByCostHeap.poll();
                if (!potentialMatch.a.matched && !potentialMatch.b.matched &&
                        potentialMatch.a.node.getType() == potentialMatch.b.node.getType()) {
                    potentialMatch.a.matched = potentialMatch.b.matched = true;
                    int heightScore = potentialMatch.a.node.getHeight() + potentialMatch.b.node.getHeight();
                    matchesByHeight.add(new MatchByHeight(heightScore, potentialMatch.a, potentialMatch.b));
                }
            }

            // Move matches from `matchesByCostLowerBoundHeap` to `matchesByCostHeap` until the lower-bound cost
            // of the match at the front of `matchesByCostLowerBoundHeap` is greater than the actual cost of the match
            // at the front of `matchesByCostHeap`
            while (!matchesByCostLowerBoundHeap.isEmpty() &&
                    (matchesByCostHeap.isEmpty() || matchesByCostHeap.peek().score >= matchesByCostLowerBoundHeap.peek().score)) {
                ScoredMatch lowerBound = matchesByCostLowerBoundHeap.poll();
                if (!lowerBound.a.matched && !lowerBound.b.matched &&
                        lowerBound.a.node.getType() == lowerBound.b.node.getType()) {
                    double cost = FeatureVectorTable.costMatch(lowerBound.a, lowerBound.b, null, null, null);
                    double sim = lowerBound.a.nodeFeatures.jaccardSimilarity(lowerBound.b.nodeFeatures);
                    if (sim >= SIM_THRESHOLD) {
                        ScoredMatch scored = new ScoredMatch(cost, lowerBound.a, lowerBound.b);
                        matchesByCostHeap.add(scored);
                    }
                }
            }
        }

        if (!matchesByCostLowerBoundHeap.isEmpty()) {
            throw new RuntimeException("Did not exhaust matchesByUpperBoundHeap");
        }
        if (!matchesByCostHeap.isEmpty()) {
            throw new RuntimeException("Did not exhaust matchesByScoreHeap");
        }


        long t5 = System.nanoTime();


        matchesByHeight.sort(new MatchByHeightComparator());

        long t6 = System.nanoTime();

        for (MatchByHeight match: matchesByHeight) {
            lastChanceMatch(matchHelper, mappingScorer, match.a.node, match.b.node);
            if (mappingScorer != null) {
                mappingScorer.link(match.a, match.b, false);
            }
            else {
                addMapping(match.a.node, match.b.node);
            }
        }

        long t7 = System.nanoTime();

        if (mappingScorer != null) {
            double costBefore = mappingScorer.getCost();
            String stateBefore = mappingScorer.getCostState();

            // Randomise the mapping between unfixed notes
            mappingScorer.randomiseMapping(unmatchedNodesA, unmatchedNodesB, 15, 35);

            double costAfter = mappingScorer.getCost();
            String stateAfter = mappingScorer.getCostState();

            // Copy over the randomised mappings
            for (FGPNode a: unmatchedNodesA) {
                ScoredNodeMapping.ScoredMapping mapping = mappingScorer.aIdToMapping[a.node.getId()];
                if (mapping != null) {
                    FGPNode b = mapping.b;
                    addMapping(a.node, b.node);
                }
            }

//            if (costAfter < costBefore) {
//                System.err.println("++++ bottomUpMatch: Randomisation changed cost from " + stateBefore + " to " + stateAfter);
//            }
//            else {
//                System.err.println("---- bottomUpMatch: Randomisation left cost unchanged");
//            }
        }





        double gatherTime = (t2 - t1) * 1.0e-9;
        double scoreUpperBoundTime = (t3 - t2) * 1.0e-9;
        double heapTime = (t4 - t3) * 1.0e-9;
        double chooseBestMatchesTime = (t5 - t4) * 1.0e-9;
        double sortBestMatchesTime = (t6 - t5) * 1.0e-9;
        double mappingTime = (t7 - t6) * 1.0e-9;
//        System.err.println("bottomUpMatch(): " + nodesA.size() + " x " + nodesB.size() + ", gather time=" + gatherTime + "s, score u-b time=" + scoreUpperBoundTime + "s, heap time=" + heapTime + "s, choose best matches time=" + chooseBestMatchesTime + "s, sort best matches time=" + sortBestMatchesTime + "s, mapping time=" + mappingTime + "s");
    }


    @Override
    public void match() {
        long t1 = System.nanoTime();

        FingerprintMatchHelper matchHelper = new FingerprintMatchHelper(src, dst);

        int nTA = matchHelper.fgpTreeA.subtreeSize;
        int nTB = matchHelper.fgpTreeB.subtreeSize;

        long t2 = System.nanoTime();
        double fgTime = (t2 - t1) * 1.0e-9;

        topDownMatch(matchHelper.fgpTreeA, matchHelper.fgpTreeB, 1);

//        int nTopDown = mappings.asSet().size();

        long t3 = System.nanoTime();
        double topDownTime = (t3 - t2) * 1.0e-9;

        bottomUpMatch(matchHelper, null, matchHelper.fgpTreeA, matchHelper.fgpTreeB);

        long t4 = System.nanoTime();
        double bottomUpTime = (t4 - t3) * 1.0e-9;

//        System.err.println("Fingerprint generation " + nTA + " x " + nTB + " nodes: " + fgTime + "s, top down " + topDownTime + "s, bottom up " + bottomUpTime + "s");
    }
}
