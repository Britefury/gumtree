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
import com.github.gumtreediff.matchers.MultiMappingStore;
import com.github.gumtreediff.matchers.heuristic.fgp.FingerprintMatchHelper;
import com.github.gumtreediff.tree.ITree;

import java.util.*;

public class GreedySubtreeMatcher extends SubtreeMatcher {
    FingerprintMatchHelper helper;
    private int treeLeftA[], treeLeftB[], treeRightA[], treeRightB[];

    public void setHelper(FingerprintMatchHelper h) {
        this.helper = h;
    }

    public GreedySubtreeMatcher(ITree src, ITree dst, MappingStore store) {
        super(src, dst, store);
    }

    @Override
    public void match() {
        int nA = src.getSize();
        int nB = dst.getSize();

        treeLeftA = new int[nA];
        treeRightA = new int[nA];
        treeLeftB = new int[nB];
        treeRightB = new int[nB];

        updateTreeLeftRight(treeLeftA, treeRightA, 0, 0, src);
        updateTreeLeftRight(treeLeftB, treeRightB, 0, 0, dst);

        super.match();
    }

    private void updateTreeLeftRight(int treeLeft[], int treeRight[], int left, int right, ITree node) {
        int nodeId = node.getId();
        int size = node.getSize();
        treeLeft[nodeId] = left;
        treeRight[nodeId] = right;
        int cLeft = left;
        int cRight = right + size - 1;
        for (ITree ch: node.getChildren()) {
            int chSize = ch.getSize();
            cRight -= chSize;
            updateTreeLeftRight(treeLeft, treeRight, cLeft, cRight, ch);
            cLeft += chSize;
        }
    }

    public void filterMappings(MultiMappingStore multiMappings) {
        // Select unique mappings first and extract ambiguous mappings.
        List<Mapping> ambiguousList = new LinkedList<>();
        Set<ITree> ignored = new HashSet<>();
        for (ITree src: multiMappings.getSrcs()) {
            if (multiMappings.isSrcUnique(src))
                addFullMapping(src, multiMappings.getDst(src).iterator().next());
            else if (!ignored.contains(src)) {
                Set<ITree> adsts = multiMappings.getDst(src);
                Set<ITree> asrcs = multiMappings.getSrc(multiMappings.getDst(src).iterator().next());
                for (ITree asrc : asrcs)
                    for (ITree adst: adsts)
                        ambiguousList.add(new Mapping(asrc, adst));
                ignored.addAll(asrcs);
            }
        }

        // Rank the mappings by score.
        Set<ITree> srcIgnored = new HashSet<>();
        Set<ITree> dstIgnored = new HashSet<>();
        MappingComparator cmp = new MappingComparator(ambiguousList);
        Collections.sort(ambiguousList, cmp);

        // Select the best ambiguous mappings
        while (ambiguousList.size() > 0) {
            Mapping ambiguous = ambiguousList.remove(0);
            if (!(srcIgnored.contains(ambiguous.getFirst()) || dstIgnored.contains(ambiguous.getSecond()))) {
                addFullMapping(ambiguous.getFirst(), ambiguous.getSecond());
                srcIgnored.add(ambiguous.getFirst());
                dstIgnored.add(ambiguous.getSecond());
            }
        }
    }

    private class MappingComparator implements Comparator<Mapping> {

        private Map<Mapping, Double> simMap = new HashMap<>();

        public MappingComparator(List<Mapping> mappings) {
            for (Mapping mapping: mappings)
                simMap.put(mapping, sim(mapping.getFirst(), mapping.getSecond()));
        }

        public int compare(Mapping m1, Mapping m2) {
            return Double.compare(simMap.get(m2), simMap.get(m1));
        }

        private Map<ITree, List<ITree>> srcDescendants = new HashMap<>();

        private Map<ITree, Set<ITree>> dstDescendants = new HashMap<>();

        protected int numberOfCommonDescendants(ITree src, ITree dst) {
            if (!srcDescendants.containsKey(src))
                srcDescendants.put(src, src.getDescendants());
            if (!dstDescendants.containsKey(dst))
                dstDescendants.put(dst, new HashSet<>(dst.getDescendants()));

            int common = 0;

            for (ITree t: srcDescendants.get(src)) {
                ITree m = mappings.getDstForSrc(t);
                if (m != null && dstDescendants.get(dst).contains(m))
                    common++;
            }

            return common;
        }

        protected double sim(ITree src, ITree dst) {
            if (helper != null) {
                return helper.scoreMatchContext(src, dst);
            }
            else {
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
        }

        protected double jaccardSimilarity(ITree src, ITree dst) {
            double num = (double) numberOfCommonDescendants(src, dst);
            double den = (double) srcDescendants.get(src).size() + (double) dstDescendants.get(dst).size() - num;
            return num / den;
        }

    }
}
