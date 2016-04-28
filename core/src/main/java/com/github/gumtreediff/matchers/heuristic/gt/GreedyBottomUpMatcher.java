/*
 * This file is part of GumTree.
 *
 * GumTree is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GumTree is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GumTree.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2011-2015 Jean-Rémy Falleri <jr.falleri@gmail.com>
 * Copyright 2011-2015 Floréal Morandat <florealm@gmail.com>
 */

package com.github.gumtreediff.matchers.heuristic.gt;

import com.github.gumtreediff.matchers.Mapping;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.heuristic.fgp.FingerprintMatchHelper;
import com.github.gumtreediff.matchers.optimal.zs.ZsMatcher;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeMap;
import com.github.gumtreediff.tree.TreeUtils;

import java.util.*;

/**
 * Match the nodes using a bottom-up approach. It browse the nodes of the source and destination trees
 * using a post-order traversal, testing if the two selected trees might be mapped. The two trees are mapped 
 * if they are mappable and have a dice coefficient greater than SIM_THRESHOLD. Whenever two trees are mapped
 * a exact ZS algorithm is applied to look to possibly forgotten nodes.
 */
public class GreedyBottomUpMatcher extends Matcher {

    //    private static final double SIM_THRESHOLD = Double.parseDouble(System.getProperty("gumtree.match.bu.sim", "0.3"));
    private static double SIM_THRESHOLD;
    private static boolean SIM_COMBINE_FG_MATCHES;

    private static final int SIZE_THRESHOLD = Integer.parseInt(System.getProperty("gumtree.match.bu.size", "1000"));

    static {
        String simThresh = "0.3";
        Map<String, String> env = System.getenv();
        Object simThreshVal = env.get("BUSIM");
        if (simThreshVal != null) {
            simThresh = (String)simThreshVal;
        }
        try {
            SIM_THRESHOLD = Double.parseDouble(System.getProperty("gumtree.match.bu.sim", simThresh));
        }
        catch (NumberFormatException e) {
            SIM_THRESHOLD = 0.3;
        }
        if (simThreshVal != null) {
            System.err.println("Setting SIM_THRESHOLD from BUSIM to " + SIM_THRESHOLD);
        }

        String combineFgMatches = "0";
        Object combineFgMatchesVal = env.get("COMBINEFGMATCHES");
        if (combineFgMatchesVal != null) {
            combineFgMatches = (String)combineFgMatchesVal;
        }
        if (combineFgMatches.toLowerCase().equals("true")) {
            SIM_COMBINE_FG_MATCHES = true;
        }
        else {
            int x;
            try {
                x = Integer.parseInt(System.getProperty("gumtree.match.bu.combine_fg_matches", combineFgMatches));
            }
            catch (NumberFormatException e) {
                x = 0;
            }
            SIM_COMBINE_FG_MATCHES = x != 0;
        }
        if (combineFgMatchesVal != null) {
            System.err.println("Setting SIM_COMBINE_FG_MATCHES from COMBINEFGMATCHES to " + SIM_COMBINE_FG_MATCHES);
        }
    }

    private TreeMap srcIds;

    private TreeMap dstIds;

    public GreedyBottomUpMatcher(ITree src, ITree dst, MappingStore store) {
        super(src, dst, store);
    }

    FingerprintMatchHelper helper;

    public void setHelper(FingerprintMatchHelper h) {
        this.helper = h;
    }

    public void match() {
        srcIds = new TreeMap(src);
        dstIds = new TreeMap(dst);

        for (ITree t: src.postOrder())  {
            if (t.isRoot()) {
                addMapping(t, this.dst);
                lastChanceMatch(t, this.dst);
                break;
            } else if (!(t.isMatched() || t.isLeaf())) {
                List<ITree> candidates = getDstCandidates(t);
                ITree best = null;
                double max = -1D;

                for (ITree cand: candidates) {
                    double sim;
                    if (helper != null) {
                        if (SIM_COMBINE_FG_MATCHES) {
                            sim = helper.scoreMatch(t, cand, mappings) / 100.0;
                        }
                        else {
                            sim = helper.scoreMatch(t, cand) / 100.0;
                        }
                    }
                    else {
                        sim = jaccardSimilarity(t, cand);
                    }
//                    double sim = jaccardSimilarity(t, cand);
                    if (sim > max && sim >= SIM_THRESHOLD) {
//                        if (best == null) {
//                            System.err.println("GreedyBottomUpMatcher.match(): match " + t.getHash() + " -> " + cand.getHash() + ", sim=" + sim + ", jacc=" + jaccardSimilarity(t, cand));
//                            if (helper != null) {
//                                if (SIM_COMBINE_FG_MATCHES) {
//                                    helper.logMatch(t, cand, mappings);
//                                }
//                                else {
//                                    helper.logMatch(t, cand);
//                                }
//                            }
//                        }
//                        else {
//                            System.err.println("GreedyBottomUpMatcher.match(): **re-match** " + t.getHash() + " -> " + cand.getHash() + ", sim=" + sim + ", jacc=" + jaccardSimilarity(t, cand));
//                            if (helper != null) {
//                                if (SIM_COMBINE_FG_MATCHES) {
//                                    helper.logMatch(t, cand, mappings);
//                                }
//                                else {
//                                    helper.logMatch(t, cand);
//                                }
//                            }
//                        }
                        max = sim;
                        best = cand;
                    }
                }

                if (best != null) {
                    lastChanceMatch(t, best);
                    addMapping(t, best);
                }
            }
        }
        clean();
    }

    private List<ITree> getDstCandidates(ITree src) {
        List<ITree> seeds = new ArrayList<>();
        for (ITree c: src.getDescendants()) {
            ITree m = mappings.getDst(c);
            if (m != null) seeds.add(m);
        }
        List<ITree> candidates = new ArrayList<>();
        Set<ITree> visited = new HashSet<>();
        for (ITree seed: seeds) {
            while (seed.getParent() != null) {
                ITree parent = seed.getParent();
                if (visited.contains(parent))
                    break;
                visited.add(parent);
                if (parent.getType() == src.getType() && !parent.isMatched() && !parent.isRoot())
                    candidates.add(parent);
                seed = parent;
            }
        }

        return candidates;
    }

    //FIXME checks if it is better or not to remove the already found mappings.
    private void lastChanceMatch(ITree src, ITree dst) {
        ITree cSrc = src.deepCopy();
        ITree cDst = dst.deepCopy();
        TreeUtils.removeMatched(cSrc);
        TreeUtils.removeMatched(cDst);

        if (cSrc.getSize() < SIZE_THRESHOLD || cDst.getSize() < SIZE_THRESHOLD) {
            Matcher m = new ZsMatcher(cSrc, cDst, new MappingStore());
            m.match();
            for (Mapping candidate: m.getMappings()) {
                ITree left = srcIds.getTree(candidate.getFirst().getId());
                ITree right = dstIds.getTree(candidate.getSecond().getId());

                if (left.getId() == src.getId() || right.getId() == dst.getId()) {
                    //System.err.println("Trying to map already mapped source node.");
                    continue;
                } else if (!left.isMatchable(right)) {
                    //System.err.println("Trying to map not compatible nodes.");
                    continue;
                } else if (left.getParent().getType() != right.getParent().getType()) {
                    //System.err.println("Trying to map nodes with incompatible parents");
                    continue;
                } else addMapping(left, right);
            }
        }

        for (ITree t : src.getTrees())
            t.setMatched(true);
        for (ITree t : dst.getTrees())
            t.setMatched(true);
    }
}
