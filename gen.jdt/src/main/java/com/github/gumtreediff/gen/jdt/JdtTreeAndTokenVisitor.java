package com.github.gumtreediff.gen.jdt;

import com.github.gumtreediff.gen.jdt.cd.EntityType;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.dom.*;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Created by Geoff on 27/05/2016.
 */
public class JdtTreeAndTokenVisitor extends ASTVisitor {
    protected TreeContext context = new TreeContext();
    protected JdtToken tokens[];
    protected boolean tokensUsed[];
    private ASTNode lastVisited = null;
    private boolean simplify;

    private Deque<ITree> trees = new ArrayDeque<>();

    public JdtTreeAndTokenVisitor(JdtToken tokens[], boolean simplify) {
        super(true);
        this.tokens = tokens;
        tokensUsed = new boolean[tokens.length];
        Arrays.fill(tokensUsed, false);
        this.simplify = simplify;
    }

    public TreeContext getTreeContext() {
        return context;
    }

    protected void pushNode(ASTNode n, String label) {
        int type = n.getNodeType();
        String typeName = n.getClass().getSimpleName();
        push(type, typeName, label, n.getStartPosition(), n.getLength());
    }

    protected void pushFakeNode(EntityType n, int startPosition, int length) {
        int type = -n.ordinal(); // Fake types have negative types (but does it matter ?)
        String typeName = n.name();
        push(type, typeName, "", startPosition, length);
    }

    private void push(int type, String typeName, String label, int startPosition, int length) {
        ITree t = context.createTree(type, label, typeName);
        t.setPos(startPosition);
        t.setLength(length);

        if (trees.isEmpty())
            context.setRoot(t);
        else {
            ITree parent = trees.peek();
            t.setParentAndUpdateChildren(parent);
        }

        trees.push(t);
    }

    protected ITree getCurrentParent() {
        return trees.peek();
    }

    protected void popNode(ASTNode n) {
        ITree astTreeNode = trees.peek();

        JdtToken placeHolder = new JdtToken(-1, null, astTreeNode.getPos(), astTreeNode.getEndPos());

        int start = Arrays.binarySearch(tokens, placeHolder, startCmp);
        int end = Arrays.binarySearch(tokens, placeHolder, endCmp);
        if (start < 0) {
            start = -(start + 1);
        }
        if (end < 0) {
            end = Math.min(-(end + 1), tokens.length);
        }
        else {
            end++;
        }

        int nUsed = 0;

        for (int i = start; i < end; i++) {
            if (!tokensUsed[i]) {
                JdtToken token = tokens[i];
                int tokenTypeId = token.getTokenType() + 4096;

                String typeName = getTokenTypeToNameMap().get(token.getTokenType());
                ITree tokenNode = context.createTree(tokenTypeId, token.getSrcString(), typeName);
                tokenNode.setPos(token.getStart());
                tokenNode.setLength(token.getEnd() - token.getStart());
                tokenNode.setParentAndUpdateChildren(astTreeNode);
                tokensUsed[i] = true;
                nUsed++;
            }
            if (!(tokens[i].getStart() >= astTreeNode.getPos())) {
                System.err.println("token " + i + " start outside tree node bounds: " + tokens[i].getStart() + " < " + astTreeNode.getPos());
            }
            if (!(tokens[i].getEnd() <= astTreeNode.getEndPos())) {
                System.err.println("token " + i + " end outside tree node bounds: " + tokens[i].getEnd() + " > " + astTreeNode.getEndPos());
            }
        }

        if (nUsed > 0) {
            astTreeNode.getChildren().sort(treeOrderCmp);
        }

        trees.pop();
    }


    private static Comparator<JdtToken> startCmp = new Comparator<JdtToken>() {
        @Override
        public int compare(JdtToken o1, JdtToken o2) {
            return new Integer(o1.getStart()).compareTo(new Integer(o2.getStart()));
        }
    };

    private static Comparator<JdtToken> endCmp = new Comparator<JdtToken>() {
        @Override
        public int compare(JdtToken o1, JdtToken o2) {
            return new Integer(o1.getEnd()).compareTo(new Integer(o2.getEnd()));
        }
    };

    private static Comparator<ITree> treeOrderCmp = new Comparator<ITree>() {
        @Override
        public int compare(ITree o1, ITree o2) {
            return new Integer(o1.getPos()).compareTo(new Integer(o2.getPos()));
        }
    };



    private static HashMap<Integer, String> getTokenTypeToNameMap() {
        if (tokenTypeToNameMap == null) {
            tokenTypeToNameMap = new HashMap<Integer, String>();

            Class c = ITerminalSymbols.class;
            for (Field field: c.getFields()) {
                if (field.getName().startsWith("TokenName")) {
                    try {
                        int val = field.getInt(ITerminalSymbols.class);
                        tokenTypeToNameMap.put(val, field.getName());
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return tokenTypeToNameMap;
    }

    private static HashMap<Integer, String> tokenTypeToNameMap = null;




    @Override
    public void preVisit(ASTNode n) {
        lastVisited = n;
        if (shouldRetain(n)) {
            pushNode(n, getLabel(n));
        }
    }

    protected String getLabel(ASTNode n) {
        if (n instanceof Name) return ((Name) n).getFullyQualifiedName();
        if (n instanceof Type) return n.toString();
        if (n instanceof Modifier) return n.toString();
        if (n instanceof StringLiteral) return ((StringLiteral) n).getEscapedValue();
        if (n instanceof NumberLiteral) return ((NumberLiteral) n).getToken();
        if (n instanceof CharacterLiteral) return ((CharacterLiteral) n).getEscapedValue();
        if (n instanceof BooleanLiteral) return ((BooleanLiteral) n).toString();
        if (n instanceof InfixExpression) return ((InfixExpression) n).getOperator().toString();
        if (n instanceof PrefixExpression) return ((PrefixExpression) n).getOperator().toString();
        if (n instanceof PostfixExpression) return ((PostfixExpression) n).getOperator().toString();
        if (n instanceof Assignment) return ((Assignment) n).getOperator().toString();
        if (n instanceof TextElement) return n.toString();
        if (n instanceof TagElement) return ((TagElement) n).getTagName();

        return "";
    }

    @Override
    public boolean visit(TagElement e) {
        e.toString();
        return true;
    }

//    @Override
//    public boolean visit(QualifiedName name) {
//        return false;
//    }

    @Override
    public void postVisit(ASTNode n) {
        if (shouldRetain(n)) {
            popNode(n);
        }
        else {
            if (n == lastVisited) {
                // Leaf node; create it
                pushNode(n, getLabel(n));
                popNode(n);
            }
        }
    }


    private boolean shouldRetain(ASTNode n) {
        if (simplify) {
            ASTNode p = n.getParent();
            if (n instanceof Statement) {
                return true;
            }
            else if (n instanceof BodyDeclaration || n instanceof AnonymousClassDeclaration || n instanceof CatchClause ||
                    n instanceof CompilationUnit || n instanceof ImportDeclaration || n instanceof PackageDeclaration ||
                    n instanceof VariableDeclaration || n instanceof Type) {
                return true;
            }
            else if (p != null && (p instanceof ArrayCreation || p instanceof MethodInvocation || p instanceof ClassInstanceCreation ||
                    p instanceof LambdaExpression || p instanceof SuperMethodInvocation || p instanceof ParenthesizedExpression ||
                    p instanceof PostfixExpression || p instanceof PrefixExpression)) {
                return true;
            }
            return false;
        }
        else {
            return true;
        }
    }
}
