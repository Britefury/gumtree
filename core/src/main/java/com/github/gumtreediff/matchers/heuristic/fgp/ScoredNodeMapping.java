package com.github.gumtreediff.matchers.heuristic.fgp;

import com.github.gumtreediff.matchers.Mapping;
import com.github.gumtreediff.matchers.MappingStore;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by Geoff on 17/05/2016.
 */
public class ScoredNodeMapping {
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
                deleteCost -= 1.0;
                insertCost -= 1.0;
                if (!a.node.getLabel().equals(b.node.getLabel())) {
                    // Labels don't match; increase cost
                    updateCost += 1.0;
                }
                connectionsCost -= computeConnectionsRetained(a, b);
                registered = true;
            }
        }

        public void unregister() {
            if (registered) {
                aIdToMapping[a.node.getId()] = null;
                bIdToMapping[b.node.getId()] = null;
                deleteCost += 1.0;
                insertCost += 1.0;
                if (!a.node.getLabel().equals(b.node.getLabel())) {
                    // Labels don't match; decrease cost
                    updateCost -= 1.0;
                }
                connectionsCost += computeConnectionsRetained(a, b);
                registered = false;
            }
        }
    }

    protected ScoredMapping aIdToMapping[], bIdToMapping[];
    protected FGPNode.NodeMapping nodesA, nodesB;
    protected boolean aFixed[], bFixed[];
    protected double insertCost, deleteCost, updateCost, connectionsCost;

    public ScoredNodeMapping(FGPNode treeA, FGPNode treeB, FingerprintMatchHelper helper) {
        int nA = treeA.node.getSize();
        int nB = treeB.node.getSize();
        aIdToMapping = new ScoredMapping[nA];
        bIdToMapping = new ScoredMapping[nB];
        aFixed = new boolean[nA];
        bFixed = new boolean[nB];
        nodesA = helper.mappingA;
        nodesB = helper.mappingB;

        // Initial delete cost; delete all nodes from a
        deleteCost = nA;
        // Initial insert cost; insert all nodes from b
        insertCost = nB;
        // No updates as of yet
        updateCost = 0.0;
        // Initial links cost; the number of parent->child links in both trees
        connectionsCost = nA + nB - 2;
    }

    public void addMappings(MappingStore mappings, boolean fixed) {
        for (Mapping m: mappings) {
            link(nodesA.getByID(m.first.getId()), nodesB.getByID(m.second.getId()), fixed);
        }
    }

    public double getCost() {
        return deleteCost + insertCost + updateCost + connectionsCost;
    }

    public void randomiseMapping(ArrayList<FGPNode> nodesA, ArrayList<FGPNode> nodesB,
                                 int nSwapsPerNode, int nTestsPerSwap) {
        Random rng = new Random();
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
                    double delta = computeCompleteLinkDeltaCost(a, b);
                    if (delta < bestDeltaCost) {
                        bestB = b;
                        bestDeltaCost = delta;
                    }
                }

                if (bestB != null) {
                    link(a, bestB, false);
                }
            }
            else {
                FGPNode b = nodesB.get(nodeId - nA);
                FGPNode bestA = null;
                double bestDeltaCost = 0.0;
                for (int j = 0; j < nTestsPerSwap; j++) {
                    int aNdx = rng.nextInt(nodesA.size());
                    FGPNode a = nodesA.get(aNdx);
                    double delta = computeCompleteLinkDeltaCost(a, b);
                    if (delta < bestDeltaCost) {
                        bestA = a;
                        bestDeltaCost = delta;
                    }
                }

                if (bestA != null) {
                    link(bestA, b, false);
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
        deltaCost -= computeConnectionsRetained(a, b);
        return deltaCost;
    }

    private double computeConnectionsRetained(FGPNode a, FGPNode b) {
        int score = 0;
        boolean aHasParent = a.parent != null;
        boolean bHasParent = b.parent != null;
        if (aHasParent && bHasParent) {
            // Both `a` and `b` have parent nodes
            if (areNodesLinked(a.parent, b.parent)) {
                // Parent nodes of `a` and `b` are already linked;
                // the link is retained by linking `a` and `b`
                score += 2;
            }
        }
        for (FGPNode childA: a.children) {
            FGPNode childB = getBForA(childA);
            if (childB != null) {
                if (childB.parent == b) {
                    // A child of `a` is linked to a child of `b`, so
                    // a link is retained
                    score += 2;
                }
            }
        }
        return score;
    }

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
}
