package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.matchers.Mapping;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Register;
import com.github.gumtreediff.matchers.optimal.zs.ZsMatcher;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeUtils;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Created by Geoff on 06/04/2016.
 */
abstract class AbstractFingerprintMatcher extends Matcher {
    private static double SIM_THRESHOLD;

    private static int SIZE_THRESHOLD = 500000;
    private static int BOTTOM_UP_HEIGHT_THRESHOLD = 2;


    static {
        String simThresh = "0.3";
        Map<String, String> env = System.getenv();
        Object simThreshVal = env.get("FGSIM");
        if (simThreshVal != null) {
            simThresh = (String) simThreshVal;
        }
        try {
            SIM_THRESHOLD = Double.parseDouble(System.getProperty("gumtree.match.fg.sim", simThresh));
        } catch (NumberFormatException e) {
            SIM_THRESHOLD = 0.3;
        }
        if (simThreshVal != null) {
            System.err.println("Setting SIM_THRESHOLD from FGSIM to " + SIM_THRESHOLD);
        }
    }


    public AbstractFingerprintMatcher(ITree src, ITree dst, MappingStore store) {
        super(src, dst, store);
    }




    protected void topDownMatch(FGPNode treeA, FGPNode treeB, int minDepth) {
        DepthNodeQueue depthQA = new DepthNodeQueue(treeA);
        DepthNodeQueue depthQB = new DepthNodeQueue(treeB);

        while (depthQA.maxDepth > minDepth) {
            while (depthQA.maxDepth != depthQB.maxDepth) {
                if (depthQA.maxDepth > depthQB.maxDepth) {
                    ArrayList<FGPNode> nodes = depthQA.popNodesAtMaxDepth();
                    for (FGPNode node: nodes) {
                        depthQA.pushNodes(node.children);
                    }
                }
                else {
                    ArrayList<FGPNode> nodes = depthQB.popNodesAtMaxDepth();
                    for (FGPNode node: nodes) {
                        depthQB.pushNodes(node.children);
                    }
                }
            }
            if (depthQA.maxDepth <= minDepth) {
                break;
            }

            // Get the nodes from `treeA` and `treeB` that are at the current depth
            ArrayList<FGPNode> nodesA = depthQA.popNodesAtMaxDepth();
            ArrayList<FGPNode> nodesB = depthQB.popNodesAtMaxDepth();

            // The `NodeArrayPairByFG` class groups nodes by fingerprint index, then in separate arrays for
            // nodes from `treeA` and nodes from `treeB`
            NodeArrayPairByFG nodesByContentFg = new NodeArrayPairByFG();
            for (FGPNode node: nodesA) {
                nodesByContentFg.putInA(node.getContentFingerprintIndex(), node);
            }
            for (FGPNode node: nodesB) {
                nodesByContentFg.putInB(node.getContentFingerprintIndex(), node);
            }

            // This second map will receive nodes that are not matched by the first pass
            NodeArrayPairByFG nodesByShapeFg = new NodeArrayPairByFG();

            // FIRST PASS: Walk the nodes grouped by content fingerprint:
            // - where the match is unique (1 node from `treeA` and 1 from `treeB` with a given fingerprint)
            // - where the match is not unique, match in order decreasing match score
            // - put unmatched nodes in `nodesByShapeFg`, this time keyed by shape fingerprint rather than
            // content fingerprint
            for (NodeArrayPair pair: nodesByContentFg.arrayPairsByFG.values()) {
                if (pair.nodesA.size() == 1 && pair.nodesB.size() == 1) {
                    // Unique match: register
                    pair.nodesA.get(0).matched = true;
                    pair.nodesB.get(0).matched = true;
                    addFullMapping(pair.nodesA.get(0).node, pair.nodesB.get(0).node);
                }
                else if (pair.nodesA.size() > 0 && pair.nodesB.size() > 0) {
                    // Match nodes from `pair.nodesA` with nodes from `pair.nodesB`

                    // Rank matches by score
                    ArrayList<ScoredMatch> scoredMatches = new ArrayList<>();
                    for (FGPNode a: pair.nodesA) {
                        for (FGPNode b: pair.nodesB) {
                            scoredMatches.add(new ScoredMatch(FeatureVectorTable.scoreMatch(a.parent, b.parent, null, null, null), a, b));
                        }
                    }
                    scoredMatches.sort(new ScoreMatchComparator());

                    // Match pairs in order
                    for (ScoredMatch potentialMatch: scoredMatches) {
                        if (!potentialMatch.a.matched && !potentialMatch.b.matched) {
                            potentialMatch.a.matched = potentialMatch.b.matched = true;
                            addFullMapping(potentialMatch.a.node, potentialMatch.b.node);
                        }
                    }

                    // Put children of unmatched nodes back in the queue
                    for (FGPNode a: pair.nodesA) {
                        if (!a.matched) {
                            nodesByShapeFg.putInA(a.getShapeFingerprintIndex(), a);
                        }
                    }
                    for (FGPNode b: pair.nodesB) {
                        if (!b.matched) {
                            nodesByShapeFg.putInB(b.getShapeFingerprintIndex(), b);
                        }
                    }
                }
                else {
                    // Matches not possible; put children in queues
                    for (FGPNode a: pair.nodesA) {
                        nodesByShapeFg.putInA(a.getShapeFingerprintIndex(), a);
                    }
                    for (FGPNode b: pair.nodesB) {
                        nodesByShapeFg.putInB(b.getShapeFingerprintIndex(), b);
                    }
                }
            }

            // SECOND PASS: Walk the nodes grouped by shape fingerprint:
            // - where the match is unique (1 node from `treeA` and 1 from `treeB` with a given fingerprint)
            // - where the match is not unique, match in order decreasing match score
            // - take unmatched nodes, and 'open' them, placing their children in the nodes by depth queue
            for (NodeArrayPair pair: nodesByShapeFg.arrayPairsByFG.values()) {
                if (pair.nodesA.size() == 1 && pair.nodesB.size() == 1) {
                    // Unique match: register
                    pair.nodesA.get(0).matched = true;
                    pair.nodesB.get(0).matched = true;
                    addFullMapping(pair.nodesA.get(0).node, pair.nodesB.get(0).node);
                }
                else if (pair.nodesA.size() > 0 && pair.nodesB.size() > 0) {
                    // Match nodes from `pair.nodesA` with nodes from `pair.nodesB`

                    // Rank matches by score
                    ArrayList<ScoredMatch> scoredMatches = new ArrayList<>();
                    for (FGPNode a: pair.nodesA) {
                        for (FGPNode b: pair.nodesB) {
                            scoredMatches.add(new ScoredMatch(FeatureVectorTable.scoreMatchContext(a, b), a, b));
                        }
                    }
                    scoredMatches.sort(new ScoreMatchComparator());

                    // Match pairs in order
                    for (ScoredMatch potentialMatch: scoredMatches) {
                        if (!potentialMatch.a.matched && !potentialMatch.b.matched) {
                            potentialMatch.a.matched = potentialMatch.b.matched = true;
                            addFullMapping(potentialMatch.a.node, potentialMatch.b.node);
                        }
                    }

                    // Put children of unmatched nodes back in the queue
                    for (FGPNode a: pair.nodesA) {
                        if (!a.matched) {
                            depthQA.pushNodes(a.children);
                        }
                    }
                    for (FGPNode b: pair.nodesB) {
                        if (!b.matched) {
                            depthQB.pushNodes(b.children);
                        }
                    }
                }
                else {
                    // Matches not possible; put children in queues
                    for (FGPNode a: pair.nodesA) {
                        depthQA.pushNodes(a.children);
                    }
                    for (FGPNode b: pair.nodesB) {
                        depthQB.pushNodes(b.children);
                    }
                }
            }
        }
    }

    private ArrayList<FGPNode> nodesInUnmatchedSubtrees(FGPNode tree, int minHeight) {
        ArrayList<FGPNode> nodes = new ArrayList<>();
        ArrayDeque<FGPNode> queue = new ArrayDeque<>();
        queue.addLast(tree);
        while (!queue.isEmpty()) {
            FGPNode node = queue.removeFirst();
            if (!node.matched && node.node.getHeight() >= minHeight) {
                nodes.add(node);
                queue.addAll(Arrays.asList(node.children));
            }
        }
        return nodes;
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

            // Randomise the mapping between unfixed notes
            mappingScorer.randomiseMapping(unmatchedNodesA, unmatchedNodesB, 15, 35);

            double costAfter = mappingScorer.getCost();

            // Copy over the randomised mappings
            for (FGPNode a: unmatchedNodesA) {
                ScoredNodeMapping.ScoredMapping mapping = mappingScorer.aIdToMapping[a.node.getId()];
                if (mapping != null) {
                    FGPNode b = mapping.b;
                    addMapping(a.node, b.node);
                }
            }

            if (costAfter < costBefore) {
                System.err.println("++++ bottomUpMatch: Randomisation changed cost from " + costBefore + " to " + costAfter);
            }
            else {
                System.err.println("---- bottomUpMatch: Randomisation left cost unchanged");
            }
        }





        double gatherTime = (t2 - t1) * 1.0e-9;
        double scoreUpperBoundTime = (t3 - t2) * 1.0e-9;
        double heapTime = (t4 - t3) * 1.0e-9;
        double chooseBestMatchesTime = (t5 - t4) * 1.0e-9;
        double sortBestMatchesTime = (t6 - t5) * 1.0e-9;
        double mappingTime = (t7 - t6) * 1.0e-9;
//        System.err.println("bottomUpMatch(): " + nodesA.size() + " x " + nodesB.size() + ", gather time=" + gatherTime + "s, score u-b time=" + scoreUpperBoundTime + "s, heap time=" + heapTime + "s, choose best matches time=" + chooseBestMatchesTime + "s, sort best matches time=" + sortBestMatchesTime + "s, mapping time=" + mappingTime + "s");
    }


    private void lastChanceMatch(FingerprintMatchHelper matchHelper, ScoredNodeMapping mappingScorer, ITree src, ITree dst) {
        ITree cSrc = src.deepCopy();
        ITree cDst = dst.deepCopy();
        TreeUtils.removeMatched(cSrc);
        TreeUtils.removeMatched(cDst);

        if (cSrc.getSize() * cDst.getSize() < SIZE_THRESHOLD) {
            Matcher m = new ZsMatcher(cSrc, cDst, new MappingStore());
            m.match();
            for (Mapping candidate: m.getMappings()) {
                FGPNode fgpLeft = matchHelper.mappingA.getByID(candidate.getFirst().getId());
                FGPNode fgpRight = matchHelper.mappingB.getByID(candidate.getSecond().getId());
                ITree left = fgpLeft.node;
                ITree right = fgpRight.node;

                if (left.getId() == src.getId() || right.getId() == dst.getId()) {
                    //System.err.println("Trying to map already mapped source node.");
                    continue;
                } else if (!left.isMatchable(right)) {
                    //System.err.println("Trying to map not compatible nodes.");
                    continue;
                } else if (left.getParent().getType() != right.getParent().getType()) {
                    //System.err.println("Trying to map nodes with incompatible parents");
                    continue;
                } else {
                    if (mappingScorer != null) {
                        mappingScorer.link(fgpLeft, fgpRight, false);
                    }
                    else {
                        addMapping(left, right);
                    }
                    fgpLeft.matched = fgpRight.matched = true;
                }
            }
        }

        matchHelper.mappingA.get(src).matched = true;
        matchHelper.mappingB.get(dst).matched = true;

        for (ITree t : src.getTrees())
            t.setMatched(true);
        for (ITree t : dst.getTrees())
            t.setMatched(true);
    }


    private static class NodeArrayPair {
        ArrayList<FGPNode> nodesA = new ArrayList<>();
        ArrayList<FGPNode> nodesB = new ArrayList<>();
    }

    private static class NodeArrayPairByFG {
        private HashMap<Integer, NodeArrayPair> arrayPairsByFG = new HashMap<>();

        private NodeArrayPair pairFor(int fingerprintIndex) {
            NodeArrayPair pair = arrayPairsByFG.get(fingerprintIndex);
            if (pair == null) {
                pair = new NodeArrayPair();
                arrayPairsByFG.put(fingerprintIndex, pair);
            }
            return pair;
        }

        void putInA(int fingerprintIndex, FGPNode node) {
            pairFor(fingerprintIndex).nodesA.add(node);
        }

        void putInB(int fingerprintIndex, FGPNode node) {
            pairFor(fingerprintIndex).nodesB.add(node);
        }
    }

    private static class ScoredMatch implements Comparable<ScoredMatch> {
        private double score;
        private FGPNode a, b;

        public ScoredMatch(double score, FGPNode a, FGPNode b) {
            this.score = score;
            this.a = a;
            this.b = b;
        }

        @Override
        public int compareTo(ScoredMatch o) {
            return -Double.compare(score, o.score);
        }
    }

    private static class ScoreMatchComparator implements Comparator<ScoredMatch> {

        @Override
        public int compare(ScoredMatch o1, ScoredMatch o2) {
            return -Double.compare(o1.score, o2.score);
        }
    }


    private static class MatchByHeight {
        private int heightScore;
        private FGPNode a, b;

        public MatchByHeight(int heightScore, FGPNode a, FGPNode b) {
            this.heightScore = heightScore;
            this.a = a;
            this.b = b;
        }
    }

    private static class MatchByHeightComparator implements Comparator<MatchByHeight> {

        @Override
        public int compare(MatchByHeight o1, MatchByHeight o2) {
            return Integer.compare(o1.heightScore, o2.heightScore);
        }
    }



    protected int numberOfCommonDescendants(FGPNode src, FGPNode dst, FingerprintMatchHelper matchHelper) {
        int common = 0;

        for (FGPNode t: src.depthFirst()) {
            ITree m = mappings.getDstForSrc(t.node);
            if (m != null) {
                FGPNode fm = matchHelper.mappingB.get(m);
                if (fm.isInSubtreeRootedAt(dst)) {
                    common++;
                }
            }
        }

        return common;
    }

    /*protected double sim(ITree src, ITree dst) {
        // Jaccard similarity; measure of similarity of node subtree contents
        double jaccard = jaccardSimilarity(src.getParent(), dst.getParent());

        // Position similarity; measure of similarity of position of nodes within parents
        int aPosLeft = (src.isRoot()) ? 0 : src.getParent().getChildPosition(src);
        int bPosLeft = (dst.isRoot()) ? 0 : dst.getParent().getChildPosition(dst);
        int aPosRight = (src.isRoot()) ? 0 : src.getParent().getChildren().size() - aPosLeft - 1;
        int bPosRight = (dst.isRoot()) ? 0 : dst.getParent().getChildren().size() - bPosLeft - 1;
        double posLeftSim = Math.min(aPosLeft, bPosLeft) / (double)Math.max(Math.max(aPosLeft, bPosLeft), 1);
        double posRightSim = Math.min(aPosRight, bPosRight) / (double)Math.max(Math.max(aPosRight, bPosRight), 1);
        double pos = (posLeftSim + posRightSim) * 0.5;

        // Tree weight to left and right similarity; measure of similarity of position of nodes within trees
        int a = src.getId(), b = dst.getId();
        double leftSim = Math.min(treeLeftA[a], treeLeftB[b]) / (double)Math.max(Math.max(treeLeftA[a], treeLeftB[b]), 1);
        double rightSim = Math.min(treeRightA[a], treeRightB[b]) / (double)Math.max(Math.max(treeRightA[a], treeRightB[b]), 1);
        double weightPo = (leftSim + rightSim) * 0.5;

        // example |a| = 1000, |b| = 1100, |node| = 10
        // :: tla = 100, tlb = 110, tra = 890, trb = 980 -> min(100,110)/max(100,110) + min(890,980)/max(890,980)
        //                                                  100/110 + 890/980 = 1.81725417439703
        // :: tla = 100, tlb = 120, tra = 890, trb = 970 -> min(100,120)/max(100,120) + min(890,970)/max(890/970)
        //                                                  100/120 + 890/970 = 1.75085910652921

        double po = weightPo;
        return jaccard + pos * 0.1 + po * 0.01;
    }

    protected double jaccardSimilarity(FGPNode src, FGPNode dst, FingerprintMatchHelper matchHelper) {
        double num = (double) numberOfCommonDescendants(src, dst, matchHelper);
        double den = (double) src.subtreeSize + (double) dst.subtreeSize - num;
        return num / den;
    }*/
}
