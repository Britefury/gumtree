package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.tree.ITree;

import java.util.ArrayList;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Created by Geoff on 24/05/2016.
 */
public abstract class AbstractSimpleCtxFingerprintMatcher extends AbstractFingerprintMatcher {
    private static int BOTTOM_UP_HEIGHT_THRESHOLD = 2;
    private static int SIZE_THRESHOLD = 500000;




    public AbstractSimpleCtxFingerprintMatcher(ITree src, ITree dst, MappingStore store) {
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
        ArrayList<ScoredMatch> matchesByUpperBound = new ArrayList<>();
        for (FGPNode a: nodesA) {
            for (FGPNode b: nodesB) {
                double scoreUpperBound = FeatureVectorTable.scoreMatchUpperBound(a, b);
                if (scoreUpperBound > SIM_THRESHOLD) {
                    matchesByUpperBound.add(new ScoredMatch(scoreUpperBound, a, b));
                }
            }
        }

        long t3 = System.nanoTime();

        // Convert to heap
        PriorityQueue<ScoredMatch> matchesByUpperBoundHeap = new PriorityQueue<>(matchesByUpperBound);
        PriorityQueue<ScoredMatch> matchesByScoreHeap = new PriorityQueue<>();

        ArrayList<MatchByHeight> matchesByHeight = new ArrayList<>();

        long t4 = System.nanoTime();

        while (!matchesByUpperBoundHeap.isEmpty() || !matchesByScoreHeap.isEmpty()) {
            // Remove any matches from `matchesByScoreHeap` is greater than the highest upper bound available
            // as determined by the entry at the head of `matchesByUpperBoundHeap`, since no match can be
            // moved from `matchesByUpperBoundHeap` that will beat it.
            while (!matchesByScoreHeap.isEmpty() &&
                    (matchesByUpperBoundHeap.isEmpty() || matchesByScoreHeap.peek().score > matchesByUpperBoundHeap.peek().score)) {
                ScoredMatch potentialMatch = matchesByScoreHeap.poll();
                if (!potentialMatch.a.matched && !potentialMatch.b.matched &&
                        potentialMatch.a.node.getType() == potentialMatch.b.node.getType()) {
                    potentialMatch.a.matched = potentialMatch.b.matched = true;
                    int heightScore = potentialMatch.a.node.getHeight() + potentialMatch.b.node.getHeight();
                    matchesByHeight.add(new MatchByHeight(heightScore, potentialMatch.a, potentialMatch.b));
                }
            }

            // Move matches from `matchesByUpperBoundHeap` to `matchesByScoreHeap` until the upper-bound score
            // of the match at the front of `matchesByUpperBoundHeap` is less than the actual score of the match
            // at the front of `matchesByScoreHeap`
            while (!matchesByUpperBoundHeap.isEmpty() &&
                    (matchesByScoreHeap.isEmpty() || matchesByScoreHeap.peek().score <= matchesByUpperBoundHeap.peek().score)) {
                ScoredMatch upperBound = matchesByUpperBoundHeap.poll();
                if (!upperBound.a.matched && !upperBound.b.matched &&
                        upperBound.a.node.getType() == upperBound.b.node.getType()) {
                    double score = FeatureVectorTable.scoreMatch(upperBound.a, upperBound.b, null, null, null);
                    if (score >= SIM_THRESHOLD) {
                        ScoredMatch scored = new ScoredMatch(score, upperBound.a, upperBound.b);
                        matchesByScoreHeap.add(scored);
                    }
                }
            }
        }

        if (!matchesByUpperBoundHeap.isEmpty()) {
            throw new RuntimeException("Did not exhaust matchesByUpperBoundHeap");
        }
        if (!matchesByScoreHeap.isEmpty()) {
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

}
