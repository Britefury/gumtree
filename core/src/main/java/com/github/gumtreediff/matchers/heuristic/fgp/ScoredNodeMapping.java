package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.matchers.Mapping;
import com.github.gumtreediff.matchers.MappingStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

/**
 * Created by Geoff on 17/05/2016.
 */
public class ScoredNodeMapping {
    private final int OP_DEL = 0x1;
    private final int OP_INS = 0x2;
    private final int OP_UPD = 0x4;
    private final int OP_MOV = 0x8;


    public class ScoredMapping {
        FGPNode a, b;
        boolean registered;

        public ScoredMapping(FGPNode a, FGPNode b) {
            this.a = a;
            this.b = b;
            registered = false;
        }

        public void register() {
            if (!registered) {
                aIdToMapping[a.node.getId()] = this;
                bIdToMapping[b.node.getId()] = this;
                boolean valueChanged = !a.node.getLabel().equals(b.node.getLabel());
                boolean moved = wouldLinkMoveNode(a, b);
                removeAOps(a, OP_DEL);
                removeBOps(b, OP_INS);
                addBOps(b, (valueChanged ? OP_UPD : 0) | (moved ? OP_MOV : 0));
                for (ScoredMapping mapping: mappingsBetweenChildrenOf(a, b)) {
                    removeBOps(mapping.b, OP_MOV);
                }
                registered = true;
            }
        }

        public void unregister() {
            if (registered) {
                aIdToMapping[a.node.getId()] = null;
                bIdToMapping[b.node.getId()] = null;
                boolean valueChanged = !a.node.getLabel().equals(b.node.getLabel());
                boolean moved = wouldLinkMoveNode(a, b);
                addAOps(a, OP_DEL);
                addBOps(b, OP_INS);
                removeBOps(b, (valueChanged ? OP_UPD : 0) | (moved ? OP_MOV : 0));
                for (ScoredMapping mapping: mappingsBetweenChildrenOf(a, b)) {
                    addBOps(mapping.b, OP_MOV);
                }
                registered = false;
            }
        }
    }

    protected ScoredMapping aIdToMapping[], bIdToMapping[];
    protected FGPNode.NodeMapping nodesA, nodesB;
    protected boolean aFixed[], bFixed[];
    protected int aOps[], bOps[];
    protected int numInserts, numDeletes;
    protected int updateCost, moveCost;

    public ScoredNodeMapping(FGPNode treeA, FGPNode treeB, FingerprintMatchHelper helper) {
        int nA = treeA.node.getSize();
        int nB = treeB.node.getSize();
        aIdToMapping = new ScoredMapping[nA];
        bIdToMapping = new ScoredMapping[nB];
        aFixed = new boolean[nA];
        bFixed = new boolean[nB];
        aOps = new int[nA];
        bOps = new int[nB];
        nodesA = helper.mappingA;
        nodesB = helper.mappingB;

        Arrays.fill(aOps, OP_DEL);
        Arrays.fill(bOps, OP_INS);

        // Initial delete cost; delete all nodes from a
        numDeletes = nA;
        // Initial insert cost; insert all nodes from b
        numInserts = nB;
        // No updates as of yet
        updateCost = 0;
        // Initial links cost; the number of parent->child links in both trees
        moveCost = 0;
    }



    private void updateCost(int dOps, boolean add) {
        int offset = add ? 1 : -1;
        if ((dOps & OP_DEL) != 0) {
            numDeletes += offset;
        }
        if ((dOps & OP_INS) != 0) {
            numInserts += offset;
        }
        if ((dOps & OP_UPD) != 0) {
            updateCost += offset;
        }
        if ((dOps & OP_MOV) != 0) {
            moveCost += offset;
        }
    }

    private String opsToString(int ops) {
        StringBuilder b = new StringBuilder();
        if ((ops & OP_DEL) != 0) {
            b.append("del,");
        }
        if ((ops & OP_INS) != 0) {
            b.append("ins,");
        }
        if ((ops & OP_UPD) != 0) {
            b.append("upd,");
        }
        if ((ops & OP_MOV) != 0) {
            b.append("mov,");
        }
        return b.toString();
    }


    private void addAOps(FGPNode a, int ops) {
        if (ops != 0) {
            int aId = a.node.getId();
            int x = aOps[aId];
            int dOps = ops & ~x;
            aOps[aId] |= ops;
            updateCost(dOps, true);
        }
    }

    private void removeAOps(FGPNode a, int ops) {
        if (ops != 0) {
            int aId = a.node.getId();
            int x = aOps[aId];
            int dOps = ops & x;
            aOps[aId] &= ~ops;
            updateCost(dOps, false);
        }
    }


    private void addBOps(FGPNode b, int ops) {
        if (ops != 0) {
            int bId = b.node.getId();
            int x = bOps[bId];
            int dOps = ops & ~x;
            bOps[bId] |= ops;
            updateCost(dOps, true);
        }
    }

    private void removeBOps(FGPNode b, int ops) {
        if (ops != 0) {
            int bId = b.node.getId();
            int x = bOps[bId];
            int dOps = ops & x;
            bOps[bId] &= ~ops;
            updateCost(dOps, false);
        }
    }


    public void addMappings(MappingStore mappings, boolean fixed) {
        for (Mapping m: mappings) {
            link(nodesA.getByID(m.first.getId()), nodesB.getByID(m.second.getId()), fixed);
        }
    }

    public int getNumMoves() {
        int numMoves = 0;
        for (ScoredMapping m: aIdToMapping) {
            if (m != null) {
                FGPNode a = m.a, b = m.b;
                if (a.parent != null && b.parent != null) {
                    if (!areNodesLinked(a.parent, b.parent)) {
                        numMoves++;
                    }
                }
            }
        }
        return numMoves;
    }

    public double getCost() {
        return numDeletes + numInserts + updateCost + moveCost * 3.0;
    }

    public String getCostState() {
        return String.format("Del %d, ins %d, upd %d, mov %d", numDeletes, numInserts, updateCost, moveCost);
    }

//    public void randomiseMapping(ArrayList<FGPNode> nodesA, ArrayList<FGPNode> nodesB,
//                                 int nSwapsPerNode, int nTestsPerSwap) {
//        System.err.println("Initial state: " + getCostState());
//
//        Random rng = new Random(12345);
//
//        ArrayList<FGPNode> nodesToRandomise = new ArrayList<>();
//        for (FGPNode b: nodesB) {
//            if ((bOps[b.node.getId()] & OP_MOV) != 0) {
//                nodesToRandomise.add(b);
//            }
//        }
//
//        int nNodes = nodesToRandomise.size();
//        for (int i = 0; i < nNodes * nSwapsPerNode; i++) {
//            int nodeId = rng.nextInt(nNodes);
//
//            FGPNode b = nodesToRandomise.get(nodeId);
//            FGPNode bestA = null;
//            double bestDeltaCost = 0.0;
//            for (int j = 0; j < nTestsPerSwap; j++) {
//                int aNdx = rng.nextInt(nodesA.size());
//                FGPNode a = nodesA.get(aNdx);
//                FGPNode existingA = getAForB(b);
//                if (existingA != null && existingA.parent != null && existingA.parent == a.parent) {
//                    // Do not re-order
//                }
//                else {
//                    double delta = computeCompleteLinkDeltaCost(a, b);
//                    if (delta < bestDeltaCost) {
//                        bestA = a;
//                        bestDeltaCost = delta;
//                    }
//                }
//            }
//
//            if (bestA != null) {
//                String oldBOps = opsToString(bOps[b.node.getId()]);
//                link(bestA, b, false);
//                String newBOps = opsToString(bOps[b.node.getId()]);
//                System.err.println("Remap A to B: (" + oldBOps + ") to (" + newBOps  + ")");
//            }
//        }
//    }

    public void randomiseMapping(ArrayList<FGPNode> nodesA, ArrayList<FGPNode> nodesB,
                                 int nSwapsPerNode, int nTestsPerSwap) {
//        System.err.println("Initial state: " + getCostState());

        Random rng = new Random(12345);
        int nA = nodesA.size(), nB = nodesB.size(), nNodes = nA + nB;
        for (int i = 0; i < nNodes * nSwapsPerNode; i++) {
            int nodeId = rng.nextInt(nNodes);
            if (nodeId < nA) {
                FGPNode a = nodesA.get(nodeId);
                FGPNode bestB = null;
                double bestDeltaCost = 0.0;
                for (int j = 0; j < nTestsPerSwap; j++) {
                    int bNdx = rng.nextInt(nodesB.size());
                    FGPNode b = nodesB.get(bNdx);
                    FGPNode existingB = getBForA(a);
                    if (existingB != null && existingB.parent != null && existingB.parent == b.parent) {
                        // Do not re-order
                    }
                    else {
                        double delta = computeCompleteLinkDeltaCost(a, b);
                        if (delta < bestDeltaCost) {
                            bestB = b;
                            bestDeltaCost = delta;
                        }
                    }
                }

                if (bestB != null) {
//                    String oldBOps = opsToString(bOps[bestB.node.getId()]);
                    link(a, bestB, false);
//                    String newBOps = opsToString(bOps[bestB.node.getId()]);
//                    System.err.println("Remap A to B: (" + oldBOps + ") to (" + newBOps  + ")");
                }
            }
            else {
                FGPNode b = nodesB.get(nodeId - nA);
                FGPNode bestA = null;
                double bestDeltaCost = 0.0;
                for (int j = 0; j < nTestsPerSwap; j++) {
                    int aNdx = rng.nextInt(nodesA.size());
                    FGPNode a = nodesA.get(aNdx);
                    FGPNode existingA = getAForB(b);
                    if (existingA != null && existingA.parent != null && existingA.parent == a.parent) {
                        // Do not re-order
                    }
                    else {
                        double delta = computeCompleteLinkDeltaCost(a, b);
                        if (delta < bestDeltaCost) {
                            bestA = a;
                            bestDeltaCost = delta;
                        }
                    }
                }

                if (bestA != null) {
//                    String oldBOps = opsToString(bOps[b.node.getId()]);
                    link(bestA, b, false);
//                    String newBOps = opsToString(bOps[b.node.getId()]);
//                    System.err.println("Remap B to A: (" + oldBOps + ") to (" + newBOps  + ")");
                }
            }
        }
    }

    public void link(FGPNode a, FGPNode b, boolean fixed) {
        // Get the node IDs
        int aId = a.node.getId(), bId = b.node.getId();

        // Do not permit fixed nodes to be remapped
        if (aFixed[aId] || bFixed[bId]) {
            throw new RuntimeException("Cannot remap fixed nodes");
        }

        // Unlink node `a` if it has already been linked
        if (aIdToMapping[aId] != null) {
            aIdToMapping[aId].unregister();
        }

        // Unlink node `b` if it has already been linked
        if (bIdToMapping[bId] != null) {
            bIdToMapping[bId].unregister();
        }

        // New mapping
        ScoredMapping m = new ScoredMapping(a, b);
        m.register();

        // Set fixed flags
        aFixed[aId] = fixed;
        bFixed[bId] = fixed;
    }

    private double computeCompleteLinkDeltaCost(FGPNode a, FGPNode b) {
        int aId = a.node.getId(), bId = b.node.getId();

        // Compute the direct cost of establishing this link
        double deltaCost = computeLinkDeltaCost(a, b);

        // Compute the cost of unlinking `a`
        if (aIdToMapping[aId] != null) {
            deltaCost -= computeLinkDeltaCost(a, aIdToMapping[aId].b);
        }

        // Compute the cost of unlinking `b`
        if (bIdToMapping[bId] != null) {
            deltaCost -= computeLinkDeltaCost(bIdToMapping[bId].a, b);
        }

        return deltaCost;
    }

    private double computeLinkDeltaCost(FGPNode a, FGPNode b) {
        // Initial delta cost of -2 due the removal of a delete and insert operation
        double deltaCost = -2.0;
        if (!a.node.getLabel().equals(b.node.getLabel())) {
            // Labels don't match; update cost of 1.0
            deltaCost += 1.0;
        }
        deltaCost += computeMoveCostDelta(a, b) * 3.0;
        return deltaCost;
    }

    private int computeMoveCostDelta(FGPNode a, FGPNode b) {
        int deltaCost = 0;
        boolean aHasParent = a.parent != null;
        boolean bHasParent = b.parent != null;
        if (aHasParent != bHasParent) {
            deltaCost += 1;
        }
        else if (aHasParent && bHasParent) {
            // Both `a` and `b` have parent nodes
            if (!areNodesLinked(a.parent, b.parent)) {
                // Parent nodes of `a` and `b` are already linked;
                // the link is retained by linking `a` and `b`
                deltaCost += 1;
            }

//            FGPNode adjA[] = getAdjacentNodes(a);
//            FGPNode adjB[] = getAdjacentNodes(b);
//            if (adjA[0] != null && adjB[0] != null) {
//                if (!areNodesLinked(adjA[0], adjB[0])) {
//                    deltaCost += 1;
//                }
//            }
//            if (adjA[1] != null && adjB[1] != null) {
//                if (!areNodesLinked(adjA[1], adjB[1])) {
//                    deltaCost += 1;
//                }
//            }
        }
        for (FGPNode childA: a.children) {
            FGPNode childB = getBForA(childA);
            if (childB != null) {
                if (childB.parent == b) {
                    // A child of `a` is linked to a child of `b`, so
                    // a link is retained
                    deltaCost -= 1;
                }
            }
        }
        return deltaCost;
    }

    private boolean wouldLinkMoveNode(FGPNode a, FGPNode b) {
        boolean aHasParent = a.parent != null;
        boolean bHasParent = b.parent != null;
        if (aHasParent != bHasParent) {
            return true;
        }
        else if (aHasParent && bHasParent) {
            // Both `a` and `b` have parent nodes
            if (!areNodesLinked(a.parent, b.parent)) {
                // Parent nodes of `a` and `b` are already linked;
                // the link is retained by linking `a` and `b`
                return true;
            }
        }
        return false;
    }

    private Iterable<ScoredMapping> mappingsBetweenChildrenOf(FGPNode a, FGPNode b) {
        return new Iterable<ScoredMapping>() {
            @Override
            public Iterator<ScoredMapping> iterator() {
                return new Iterator<ScoredMapping>() {
                    // Declare `nextMapping` variable first as it is mutated by
                    // the step method
                    ScoredMapping nextMapping = null;
                    int nextChildIndex = step(0);

                    @Override
                    public boolean hasNext() {
                        return nextMapping != null;
                    }

                    @Override
                    public ScoredMapping next() {
                        ScoredMapping m = nextMapping;
                        nextChildIndex = step(nextChildIndex + 1);
                        return m;
                    }

                    public int step(int startAt) {
                        int i = startAt;
                        while (i < a.children.length) {
                            FGPNode childA = a.children[i];
                            ScoredMapping mapping = getMappingForA(childA);
                            if (mapping != null) {
                                if (mapping.b.parent == b) {
                                    // A child of `a` is linked to a child of `b`, so
                                    // a link is retained
                                    nextMapping = mapping;
                                    return i;
                                }
                            }

                            i++;
                        }

                        nextMapping = null;
                        return -1;
                    }
                };
            }
        };
    };

    private boolean areNodesLinked(FGPNode a, FGPNode b) {
        ScoredMapping mappingA = aIdToMapping[a.node.getId()];
        ScoredMapping mappingB = bIdToMapping[b.node.getId()];
        return mappingA != null && mappingB != null && mappingA == mappingB;
    }

    private FGPNode getBForA(FGPNode a) {
        ScoredMapping mappingA = aIdToMapping[a.node.getId()];
        return mappingA != null ? mappingA.b : null;
    }

    private FGPNode getAForB(FGPNode b) {
        ScoredMapping mappingB = bIdToMapping[b.node.getId()];
        return mappingB != null ? mappingB.a : null;
    }

    private ScoredMapping getMappingForA(FGPNode a) {
        return aIdToMapping[a.node.getId()];
    }

    private ScoredMapping getMappingForB(FGPNode b) {
        return bIdToMapping[b.node.getId()];
    }

    private FGPNode[] getAdjacentNodes(FGPNode x) {
        if (x.parent == null) {
            return new FGPNode[] {null, null};
        }
        else {
            int i = java.util.Arrays.asList(x.parent.children).indexOf(x);
            int p = i - 1, n = i + 1;
            FGPNode prev = p >= 0 && p < x.parent.children.length ? x.parent.children[p] : null;
            FGPNode next = n < x.parent.children.length ? x.parent.children[n] : null;
            return new FGPNode[] {prev, next};
        }
    }
}
