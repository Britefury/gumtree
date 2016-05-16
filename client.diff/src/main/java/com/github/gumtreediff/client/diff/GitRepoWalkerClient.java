package com.github.gumtreediff.client.diff;

import com.github.gumtreediff.actions.ActionGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Client;
import com.github.gumtreediff.client.Option;
import com.github.gumtreediff.client.Register;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import com.google.gson.stream.JsonWriter;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.*;
import java.util.List;

/**
 * Created by Geoff on 13/05/2016.
 */
@Register(name = "gitrepo", description = "Walk a Git repo and diff all java files",
        options = GitRepoWalkerClient.Options.class)
public class GitRepoWalkerClient extends Client {
    public static final String SYNTAX = "Syntax: gitrepo [options] repoPath";
    private final Options opts;

    public static class Options implements Option.Context {
        protected String branch = "master";
        protected String matcher = "gumtree";
        protected String jsonOutPath = null;
        protected String repoPath = "";

        @Override
        public Option[] values() {
            return new Option[] {
                    new Option("-m", "The qualified name of the class implementing the matcher.", 1) {
                        @Override
                        protected void process(String name, String[] args) {
                            matcher = args[0];
                        }
                    },
                    new Option("-branch", "Git branch to start from (default=master).", 1) {
                        @Override
                        protected void process(String name, String[] args) {
                            branch = args[0];
                        }
                    },
                    new Option("-jsonout", "Path of JSON file to which results are to be written", 1) {
                        @Override
                        protected void process(String name, String[] args) {
                            jsonOutPath = args[0];
                        }
                    },
                    new Option.Help(this) {
                        @Override
                        public void process(String name, String[] args) {
                            System.out.println(SYNTAX);
                            super.process(name, args);
                        }
                    }
            };
        }

        void dump(PrintStream out) {
            out.printf("GitRepo: repoPath=%s branch=%s matcher=%s\n", repoPath, branch, matcher);
        }
    }


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



    public GitRepoWalkerClient(String args[]) {
        super(args);

        opts = new Options();
        args = Option.processCommandLine(args, opts);

        if (args.length < 1)
            throw new Option.OptionException("arguments required." + SYNTAX, opts);

        opts.repoPath = args[0];

        if (Option.Verbose.verbose) {
            opts.dump(System.out);
        }
    }

    protected Matcher matchTrees(TreeContext src, TreeContext dst) {
        Matchers matchers = Matchers.getInstance();
        Matcher matcher = (opts.matcher == null)
                ? matchers.getMatcher(src.getRoot(), dst.getRoot())
                : matchers.getMatcher(opts.matcher, src.getRoot(), dst.getRoot());
        matcher.match();
        return matcher;
    }



    protected DiffResults diff(String contentA, String contentB) {
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
        Matcher matcher = matchTrees(tA, tB);
        MappingStore mappings = matcher.getMappings();
        ActionGenerator actionGen = new ActionGenerator(tA.getRoot(), tB.getRoot(), mappings);
        List<Action> actions = actionGen.generate();
        return new DiffResults(tA.getRoot(), tB.getRoot(), mappings, actions);
    }

    private void walkRepo(Repository repository, RevWalk walk, JsonWriter jsonOut) throws IOException {
        long t1 = System.nanoTime();
        int diffCount = 0;
        if (jsonOut != null) {
            jsonOut.beginObject();
            jsonOut.name("repo_path").value(opts.repoPath);
            jsonOut.name("results").beginArray();
        }
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
                            DiffResults dres = null;
                            try {
                                dres = diff(oldText, newText);
                            }
                            catch (Exception e) {
                            }
                            if (dres != null) {
                                diffCount++;
                                int nA = dres.getSizeA(), nB = dres.getSizeB();
//                            int nA = 0, nB = 0;
                                if (jsonOut != null) {
                                    jsonOut.beginObject();
                                    jsonOut.name("src_path").value(diff.getOldPath());
                                    jsonOut.name("dst_path").value(diff.getNewPath());
                                    jsonOut.name("src_commit").value(parent.getId().getName());
                                    jsonOut.name("dst_commit").value(commit.getId().getName());
                                    jsonOut.name("n_A").value(nA);
                                    jsonOut.name("n_B").value(nB);
                                    jsonOut.name("n_matches").value(dres.mapping.size());
                                    jsonOut.name("n_actions").value(dres.actions.size());
                                    jsonOut.endObject();
                                }
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
                            else {
                                if (ctype == DiffEntry.ChangeType.RENAME) {
                                    System.out.println(diff.getOldPath() + " -> " + diff.getNewPath() + " [ " +
                                            parent.getId().getName() + " -> " + commit.getId().getName() + " ]");
                                } else {
                                    System.out.println(diff.getOldPath() + " [ " +
                                            parent.getId().getName() + " -> " + commit.getId().getName() + " ]");
                                }
                            }
                        }
                    }
                }
            }
        }
        long t2 = System.nanoTime();
        double dt = (t2-t1) * 1.0e-9;
        System.out.println("" + diffCount + " diffs took " + dt + "s");
        if (jsonOut != null) {
            jsonOut.endArray();
            jsonOut.name("time_taken").value(dt);
            jsonOut.endObject();
        }
    }



    @Override
    public void run() throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = null;
        JsonWriter jsonOut = null;
        FileWriter outW = null;
        if (opts.jsonOutPath != null) {
            System.out.println("Writing JSON to " + opts.jsonOutPath);
            File outFile = new File(opts.jsonOutPath);
            outW = new FileWriter(outFile);
            jsonOut = new JsonWriter(outW);
        }
        try {
            repository = builder.setGitDir(new File(opts.repoPath))
                    .readEnvironment() // scan environment GIT_* variables
                    .findGitDir() // scan up the file system tree
                    .build();
            if (repository != null) {
                Ref branch = repository.findRef("refs/heads/" + opts.branch);
                RevWalk walk = new RevWalk(repository);
                RevCommit masterCommit = walk.lookupCommit(branch.getObjectId());
                walk.markStart(masterCommit);
                walkRepo(repository, walk, jsonOut);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (outW != null) {
            outW.close();
        }
    }
}
