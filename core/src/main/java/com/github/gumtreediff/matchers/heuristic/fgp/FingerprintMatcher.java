package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Register;
import com.github.gumtreediff.tree.ITree;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Created by Geoff on 06/04/2016.
 */
@Register(id = "fg")
public class FingerprintMatcher extends Matcher {
    public FingerprintMatcher(ITree src, ITree dst, MappingStore store) {
        super(src, dst, store);
    }

    @Override
    public void match() {
        long t1 = System.nanoTime();

        FingerprintMatchHelper matchHelper = new FingerprintMatchHelper(src, dst);

        int nTA = matchHelper.fgbTreeA.subtreeSize;
        int nTB = matchHelper.fgpTreeB.subtreeSize;

        long t2 = System.nanoTime();
        double fgTime = (t2 - t1) * 1.0e-9;

        topDownMatch(matchHelper.fgbTreeA, matchHelper.fgpTreeB, 0);

//        int nTopDown = mappings.asSet().size();

        long t3 = System.nanoTime();
        double topDownTime = (t3 - t2) * 1.0e-9;

        bottomUpMatch(matchHelper.fgbTreeA, matchHelper.fgpTreeB);

        long t4 = System.nanoTime();
        double bottomUpTime = (t4 - t3) * 1.0e-9;

        System.out.println("Fingerprint generation " + nTA + " x " + nTB + " nodes: " + fgTime + "s, top down " + topDownTime + "s, bottom up " + bottomUpTime + "s");
    }




    private void topDownMatch(FGPNode treeA, FGPNode treeB, int minDepth) {
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

            // Group nodes by fingerprint, keeping nodes from `a` and `b` in separate arrays,
            // using the `NodeArrayPair` class
            ArrayList<FGPNode> nodesA = depthQA.popNodesAtMaxDepth();
            ArrayList<FGPNode> nodesB = depthQB.popNodesAtMaxDepth();
            HashMap<Integer, NodeArrayPair> nodesByFg = new HashMap<>();
            for (FGPNode node: nodesA) {
                NodeArrayPair pair = nodesByFg.get(node.getFingerprintIndex());
                if (pair == null) {
                    pair = new NodeArrayPair();
                    nodesByFg.put(node.getFingerprintIndex(), pair);
                }
                pair.nodesA.add(node);
            }
            for (FGPNode node: nodesB) {
                NodeArrayPair pair = nodesByFg.get(node.getFingerprintIndex());
                if (pair == null) {
                    pair = new NodeArrayPair();
                    nodesByFg.put(node.getFingerprintIndex(), pair);
                }
                pair.nodesB.add(node);
            }

            // Walk the grouped nodes
            for (Map.Entry<Integer, NodeArrayPair> entry: nodesByFg.entrySet()) {
                int fg = entry.getKey();
                NodeArrayPair pair = entry.getValue();
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

    private ArrayList<FGPNode> nodesInUnmatchedSubtrees(FGPNode tree) {
        ArrayList<FGPNode> nodes = new ArrayList<>();
        ArrayDeque<FGPNode> queue = new ArrayDeque<>();
        queue.addLast(tree);
        while (!queue.isEmpty()) {
            FGPNode node = queue.removeFirst();
            if (!node.matched) {
                nodes.add(node);
                queue.addAll(Arrays.asList(node.children));
            }
        }
        return nodes;
    }

    private void bottomUpMatch(FGPNode treeA, FGPNode treeB) {
        ArrayList<FGPNode> nodesA = nodesInUnmatchedSubtrees(treeA);
        ArrayList<FGPNode> nodesB = nodesInUnmatchedSubtrees(treeB);

        System.out.println("Fingerprint bottom up match " + nodesA.size() + " x " + nodesB.size());

        ArrayList<ScoredMatch> scoredMatches = new ArrayList<>();
        for (FGPNode a: nodesA) {
            for (FGPNode b: nodesB) {
                scoredMatches.add(new ScoredMatch(FeatureVectorTable.scoreMatch(a, b), a, b));
            }
        }
        scoredMatches.sort(new ScoreMatchComparator());

        // Match pairs in order
        for (ScoredMatch potentialMatch: scoredMatches) {
            if (!potentialMatch.a.matched && !potentialMatch.b.matched) {
                potentialMatch.a.matched = potentialMatch.b.matched = true;
                addMapping(potentialMatch.a.node, potentialMatch.b.node);
            }
        }
    }


    private static class NodeArrayPair {
        ArrayList<FGPNode> nodesA = new ArrayList<>();
        ArrayList<FGPNode> nodesB = new ArrayList<>();
    }

    private static class ScoredMatch {
        private double score;
        private FGPNode a, b;

        public ScoredMatch(double score, FGPNode a, FGPNode b) {
            this.score = score;
            this.a = a;
            this.b = b;
        }
    }

    private static class ScoreMatchComparator implements Comparator<ScoredMatch> {

        @Override
        public int compare(ScoredMatch o1, ScoredMatch o2) {
            return -Double.compare(o1.score, o2.score);
        }
    }
}
