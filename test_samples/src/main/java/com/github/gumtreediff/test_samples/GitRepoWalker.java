package com.github.gumtreediff.test_samples;

import com.github.gumtreediff.actions.ActionGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.heuristic.fgp.SimpleCtxFingerprintMatcher;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

/**
 * Created by Geoff on 11/05/2016.
 */
public class GitRepoWalker {
    public static class DiffResults {
        ITree a, b;
        MappingStore mapping;
        List<Action> actions;

        public DiffResults(ITree a, ITree b, MappingStore mapping, List<Action> actions) {
            this.a = a;
            this.b = b;
            this.mapping = mapping;
            this.actions = actions;
        }

        public int getSizeA() {
            return a.getSize();
        }

        public int getSizeB() {
            return b.getSize();
        }
    }

    protected static DiffResults diff(String contentA, String contentB) {
        Reader rA = new StringReader(contentA);
        Reader rB = new StringReader(contentB);
        JdtTreeGenerator gen = new JdtTreeGenerator();
        TreeContext tA = null;
        TreeContext tB = null;
        try {
            tA = gen.generate(rA);
            tB = gen.generate(rB);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        tA.validate();
        tB.validate();
        MappingStore mappings = new MappingStore();
        SimpleCtxFingerprintMatcher matcher = new SimpleCtxFingerprintMatcher(tA.getRoot(), tB.getRoot(), mappings);
        matcher.match();
        ActionGenerator actionGen = new ActionGenerator(tA.getRoot(), tB.getRoot(), mappings);
        List<Action> actions = actionGen.generate();
        return new DiffResults(tA.getRoot(), tB.getRoot(), mappings, actions);
    }

    public static void walkRepo(Repository repository, RevWalk walk) throws IOException {
        long t1 = System.nanoTime();
        int diffCount = 0;
        for (RevCommit commit: walk) {
            RevCommit parents[] = commit.getParents();
            for (RevCommit parent: parents) {

                DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
                df.setRepository(repository);
                df.setDetectRenames(true);

                List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
                for (DiffEntry diff : diffs) {
                    DiffEntry.ChangeType ctype = diff.getChangeType();
                    if (ctype == DiffEntry.ChangeType.MODIFY || ctype == DiffEntry.ChangeType.RENAME) {
                        if (diff.getNewPath().toLowerCase().endsWith(".java")) {
                            ObjectLoader oldLoader = repository.open(diff.getOldId().toObjectId());
                            ObjectLoader newLoader = repository.open(diff.getNewId().toObjectId());
                            String oldText = new String(oldLoader.getBytes(), "UTF-8");
                            String newText = new String(newLoader.getBytes(), "UTF-8");
                            DiffResults dres = diff(oldText, newText);
                            diffCount++;
                            int nA = dres.getSizeA(), nB = dres.getSizeB();
//                            int nA = 0, nB = 0;
                            if (ctype == DiffEntry.ChangeType.RENAME) {
                                System.out.println(diff.getOldPath() + " -> " + diff.getNewPath() + " [ " +
                                        parent.getId().getName() + " -> " + commit.getId().getName() +
                                        " ]: |A| = " + nA + ", |B| = " + nB + ", |matches| = " +
                                        dres.mapping.size() + ", |actions| = " + dres.actions.size());
                            } else {
                                System.out.println(diff.getOldPath() + " [ " +
                                        parent.getId().getName() + " -> " + commit.getId().getName() +
                                        " ]: |A| = " + nA + ", |B| = " + nB + ", |matches| = " +
                                        dres.mapping.size() + ", |actions| = " + dres.actions.size());
                            }
                        }
                    }
                }
            }
        }
        long t2 = System.nanoTime();
        double dt = (t2-t1) * 1.0e-9;
        System.out.println("" + diffCount + " diffs took " + dt + "s");
    }

    public static void main(String args[]) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = null;
        try {
            repository = builder.setGitDir(new File("/Users/Geoff/kcl/testrepos/BottomBar/.git/"))
                    .readEnvironment() // scan environment GIT_* variables
                    .findGitDir() // scan up the file system tree
                    .build();
            Ref masterBranch = repository.findRef("refs/heads/master");
            RevWalk walk = new RevWalk(repository);
            RevCommit masterCommit = walk.lookupCommit(masterBranch.getObjectId());
            walk.markStart(masterCommit);
            walkRepo(repository, walk);

        } catch (IOException e) {
            e.printStackTrace();
        }


        System.out.println("Done.");
    }
}
