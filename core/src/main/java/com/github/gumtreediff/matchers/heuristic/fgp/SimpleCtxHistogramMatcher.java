package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.matchers.AbstractMatchStats;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Register;
import com.github.gumtreediff.tree.ITree;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.PriorityQueue;

/**
 * Created by Geoff on 24/05/2016.
 */
@Register(id = "hist-ctx")
public class SimpleCtxHistogramMatcher extends AbstractHistogramMatcher {
    private static int BOTTOM_UP_HEIGHT_THRESHOLD = 2;
    private static int SIZE_THRESHOLD = 500000;
    private static boolean GATHER_MATCH_STATS = false;


    static {
        try {
            GATHER_MATCH_STATS = Boolean.parseBoolean(System.getProperty("gumtree.match.gathermatchstats", "false"));
        } catch (NumberFormatException e) {
            GATHER_MATCH_STATS = false;
        }
    }

    @Override
    public void match() {
        long t1 = System.nanoTime();

        FingerprintMatchHelper matchHelper = new FingerprintMatchHelper(src, dst);

        int nTA = matchHelper.fgpTreeA.subtreeSize;
        int nTB = matchHelper.fgpTreeB.subtreeSize;

        long t2 = System.nanoTime();
        double fgTime = (t2 - t1) * 1.0e-9;

        findExactSubtreeMatches(matchHelper.fgpTreeA, matchHelper.fgpTreeB, 1);

//        int nTopDown = mappings.asSet().size();

        long t3 = System.nanoTime();
        double topDownTime = (t3 - t2) * 1.0e-9;

        findFuzzyMatches(matchHelper, matchHelper.fgpTreeA, matchHelper.fgpTreeB);

        long t4 = System.nanoTime();
        double bottomUpTime = (t4 - t3) * 1.0e-9;

//        System.err.println("Fingerprint generation " + nTA + " x " + nTB + " nodes: " + fgTime + "s, top down " + topDownTime + "s, bottom up " + bottomUpTime + "s");
    }

    private static class CtxFingerprintMatchStats extends AbstractMatchStats {
        ArrayList<Double> scoreDeltas = new ArrayList<>();

        @Override
        public void asJson(JsonWriter jsonOut) throws IOException {
            jsonOut.beginObject();

            jsonOut.name("score_relative_deltas").beginArray();
            for (double deltaScore: scoreDeltas) {
                jsonOut.value(deltaScore);
            }
            jsonOut.endArray();

            jsonOut.endObject();
        }

        @Override
        public String toString() {
            if (size() == 0) {
                return "";
            }
            else {
                double min = scoreDeltas.get(0);
                double max = scoreDeltas.get(0);
                for (double deltaScore: scoreDeltas) {
                    min = Math.min(min, deltaScore);
                    max = Math.max(max, deltaScore);
                }
                return "Score deltas: min=" + min + ", max=" + max;
            }
        }


        void add(double deltaScore) {
            scoreDeltas.add(deltaScore);
        }

        int size() {
            return scoreDeltas.size();
        }
    }




    public SimpleCtxHistogramMatcher(ITree src, ITree dst, MappingStore store) {
        super(src, dst, store);
    }


    protected void findFuzzyMatches(FingerprintMatchHelper matchHelper, FGPNode treeA, FGPNode treeB) {
        long t1 = System.nanoTime();
        ArrayList<FGPNode> nodesA = nodesInUnmatchedSubtrees(treeA, BOTTOM_UP_HEIGHT_THRESHOLD);
        ArrayList<FGPNode> nodesB = nodesInUnmatchedSubtrees(treeB, BOTTOM_UP_HEIGHT_THRESHOLD);


        // Get the unmached nodes in each side
        ArrayList<FGPNode> unmatchedNodesA = nodesInUnmatchedSubtrees(treeA, -1);
        ArrayList<FGPNode> unmatchedNodesB = nodesInUnmatchedSubtrees(treeB, -1);


        long t2 = System.nanoTime();

        // Gather all matches whose upper bound is less than the threshold
        ArrayList<ScoredMatch> matchesByUpperBound = new ArrayList<>();
        for (FGPNode a: nodesA) {
            for (FGPNode b: nodesB) {
                double scoreUpperBound = NodeHistogramTable.scoreMatchUpperBound(a, b);
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

        double prevScore = Double.MIN_VALUE;

        CtxFingerprintMatchStats matchStats = new CtxFingerprintMatchStats();

        while (!matchesByUpperBoundHeap.isEmpty() || !matchesByScoreHeap.isEmpty()) {
            // Remove any matches from `matchesByScoreHeap` is greater than the highest upper bound available
            // as determined by the entry at the head of `matchesByUpperBoundHeap`, since no match can be
            // moved from `matchesByUpperBoundHeap` that will beat it.
            while (!matchesByScoreHeap.isEmpty() &&
                    (matchesByUpperBoundHeap.isEmpty() || matchesByScoreHeap.peek().score > matchesByUpperBoundHeap.peek().score)) {
                ScoredMatch potentialMatch = matchesByScoreHeap.poll();
                if (GATHER_MATCH_STATS) {
                    if (prevScore > Double.MIN_VALUE) {
                        double absDelta = prevScore - potentialMatch.score;
                        double maxScore = prevScore;
                        double relDelta = absDelta / maxScore;
                        matchStats.add(relDelta);
                    }

                    prevScore = potentialMatch.score;
                }

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
                    double score = NodeHistogramTable.scoreMatch(upperBound.a, upperBound.b, null, null, null);
                    if (score >= SIM_THRESHOLD) {
                        ScoredMatch scored = new ScoredMatch(score, upperBound.a, upperBound.b);
                        matchesByScoreHeap.add(scored);
                    }
                }
            }
        }

        if (matchStats.size() > 0) {
            mappings.setMatchStats(matchStats);
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
            lastChanceMatch(matchHelper, match.a.node, match.b.node);
            addMapping(match.a.node, match.b.node);
        }

        long t7 = System.nanoTime();




        double gatherTime = (t2 - t1) * 1.0e-9;
        double scoreUpperBoundTime = (t3 - t2) * 1.0e-9;
        double heapTime = (t4 - t3) * 1.0e-9;
        double chooseBestMatchesTime = (t5 - t4) * 1.0e-9;
        double sortBestMatchesTime = (t6 - t5) * 1.0e-9;
        double mappingTime = (t7 - t6) * 1.0e-9;
//        System.err.println("findFuzzyMatches(): " + nodesA.size() + " x " + nodesB.size() + ", gather time=" + gatherTime + "s, score u-b time=" + scoreUpperBoundTime + "s, heap time=" + heapTime + "s, choose best matches time=" + chooseBestMatchesTime + "s, sort best matches time=" + sortBestMatchesTime + "s, mapping time=" + mappingTime + "s");
    }

}
