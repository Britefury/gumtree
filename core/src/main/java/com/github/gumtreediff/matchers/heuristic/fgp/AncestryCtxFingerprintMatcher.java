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
@Register(id = "fg-ctx")
public class AncestryCtxFingerprintMatcher extends AbstractFingerprintMatcher {
    private static int BOTTOM_UP_HEIGHT_THRESHOLD = 2;

    private static double LOCAL_SIM_THRESHOLD = 0.2;
    private static double NON_LOCALITY_SCALING = 1.0;
    private static double NON_LOCALITY_BALANCE_EXP = 0.0;


    static {
        try {
            LOCAL_SIM_THRESHOLD = Double.parseDouble(System.getProperty("gumtree.match.fg.local_sim", "0.2"));
        } catch (NumberFormatException e) {
            LOCAL_SIM_THRESHOLD = 0.2;
        }

        try {
            NON_LOCALITY_SCALING = Double.parseDouble(System.getProperty("gumtree.match.fg.nonlocalscale", "1.0"));
        } catch (NumberFormatException e) {
            NON_LOCALITY_SCALING = 1.0;
        }

        try {
            NON_LOCALITY_BALANCE_EXP = Double.parseDouble(System.getProperty("gumtree.match.fg.nonlocalbalanceexp", "0.0"));
        } catch (NumberFormatException e) {
            NON_LOCALITY_BALANCE_EXP = 0.0;
        }
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

    public AncestryCtxFingerprintMatcher(ITree src, ITree dst, MappingStore store) {
        super(src, dst, store);
    }

    @Override
    public void match() {
        long t1 = System.nanoTime();

        FingerprintMatchHelper matchHelper = new FingerprintMatchHelper(src, dst,
                NON_LOCALITY_SCALING, NON_LOCALITY_BALANCE_EXP);

        int nTA = matchHelper.fgpTreeA.subtreeSize;
        int nTB = matchHelper.fgpTreeB.subtreeSize;

        long t2 = System.nanoTime();
        double fgTime = (t2 - t1) * 1.0e-9;

        findExactSubtreeMatches(matchHelper.fgpTreeA, matchHelper.fgpTreeB, 1);

//        int nTopDown = mappings.asSet().size();

        long t3 = System.nanoTime();
        double topDownTime = (t3 - t2) * 1.0e-9;

        bottomUpMatch(matchHelper, matchHelper.fgpTreeA, matchHelper.fgpTreeB);

        long t4 = System.nanoTime();
        double bottomUpTime = (t4 - t3) * 1.0e-9;

//        System.err.println("Fingerprint generation " + nTA + " x " + nTB + " nodes: " + fgTime + "s, top down " + topDownTime + "s, bottom up " + bottomUpTime + "s");
    }



    private void bottomUpMatch(FingerprintMatchHelper matchHelper, FGPNode treeA, FGPNode treeB) {
        long t1 = System.nanoTime();
        ArrayList<FGPNode> nodesA = nodesInUnmatchedSubtrees(treeA, BOTTOM_UP_HEIGHT_THRESHOLD);
        ArrayList<FGPNode> nodesB = nodesInUnmatchedSubtrees(treeB, BOTTOM_UP_HEIGHT_THRESHOLD);


        FGPMatchTable matchTable = new FGPMatchTable(nodesA, nodesB);



        long t2 = System.nanoTime();

        // Gather all matches whose upper bound is less than the threshold
        ArrayList<ScoredMatch> matchesByUpperBound = new ArrayList<>();
        for (FGPNode a: nodesA) {
            for (FGPNode b: nodesB) {
                double localSimUpperBound = matchTable.localSimilarityUpperBound(a, b);
                if (localSimUpperBound > LOCAL_SIM_THRESHOLD) {
                    double ctxSimUpperBound = matchTable.inContextSimilarityUpperBound(a, b);
                    matchesByUpperBound.add(new ScoredMatch(ctxSimUpperBound, a, b));
                }
            }
        }

        long t3 = System.nanoTime();

        // Convert to heap
        PriorityQueue<ScoredMatch> matchesByUpperBoundHeap = new PriorityQueue<>(matchesByUpperBound);
        PriorityQueue<ScoredMatch> matchesByScoreHeap = new PriorityQueue<>();

        ArrayList<MatchByHeight> matchesByHeight = new ArrayList<>();

        CtxFingerprintMatchStats matchStats = new CtxFingerprintMatchStats();
        double prevScore = Double.MIN_VALUE;

        long t4 = System.nanoTime();

        while (!matchesByUpperBoundHeap.isEmpty() || !matchesByScoreHeap.isEmpty()) {
            // Remove any matches from `matchesByScoreHeap` is greater than the highest upper bound available
            // as determined by the entry at the head of `matchesByUpperBoundHeap`, since no match can be
            // moved from `matchesByUpperBoundHeap` that will beat it.
            while (!matchesByScoreHeap.isEmpty() &&
                    (matchesByUpperBoundHeap.isEmpty() || matchesByScoreHeap.peek().score > matchesByUpperBoundHeap.peek().score)) {
                ScoredMatch potentialMatch = matchesByScoreHeap.poll();
                if (prevScore > Double.MIN_VALUE) {
                    double absDelta = prevScore - potentialMatch.score;
                    double maxScore = prevScore;
                    double relDelta = absDelta / maxScore;
                    matchStats.add(relDelta);
                }

                prevScore = potentialMatch.score;

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
                    double localSim = matchTable.localSimilarity(upperBound.a, upperBound.b);
                    if (localSim >= LOCAL_SIM_THRESHOLD) {
                        double contextSim = matchTable.inContextSimilarity(upperBound.a, upperBound.b);
                        ScoredMatch scored = new ScoredMatch(contextSim, upperBound.a, upperBound.b);
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
            lastChanceMatch(matchHelper, null, match.a.node, match.b.node);
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
    }}
