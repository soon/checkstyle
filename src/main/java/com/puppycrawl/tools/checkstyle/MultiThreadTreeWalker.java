package com.puppycrawl.tools.checkstyle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FileContents;
import com.puppycrawl.tools.checkstyle.api.LocalizedMessage;
import com.puppycrawl.tools.checkstyle.utils.TokenUtils;

public final class MultiThreadTreeWalker extends AbstractTreeWalker {
    private ExecutorService executor;

    private final Map<AbstractCheck, List<AbstractAstAction>> checkToVisitingContextMap = new HashMap<>();

    @Override
    public void init() {
        super.init();

        if (executor == null) {
            final DefaultConfiguration configuration = (DefaultConfiguration) getConfiguration();
            final ThreadModeSettings threadModeSettings =
                    configuration.getThreadModeSettings();
            final int threadsNumber = threadModeSettings.getTreeWalkerThreadsNumber();
            executor = Executors.newFixedThreadPool(threadsNumber);
        }
    }

    private void doNotifyEnd(DetailAST rootAST, Set<AbstractCheck> checks) {
        final FinishTreeAction action = new FinishTreeAction(rootAST);
        for (AbstractCheck check : checks) {
            registerAction(check, action);
        }
    }

    private void doVisitToken(DetailAST ast, Collection<AbstractCheck> visitors) {
        final VisitTokenAction action = new VisitTokenAction(ast);
        for (AbstractCheck check : visitors) {
            registerAction(check, action);
        }
    }

    private void doLeaveToken(DetailAST ast, Collection<AbstractCheck> visitors) {
        final LeaveTokenAction action = new LeaveTokenAction(ast);
        for (AbstractCheck visitor : visitors) {
            registerAction(visitor, action);
        }
    }

    @Override
    protected void walk(DetailAST ast, FileContents contents, AstState astState) {
        long before = System.currentTimeMillis();
        checkToVisitingContextMap.clear();
        notifyBegin(ast, contents, astState);

        // empty files are not flagged by javac, will yield ast == null
        if (ast != null) {
            processIter(ast, astState);
        }
        notifyEnd(ast, astState);

        SortedSet<LocalizedMessage> messages = executeAllChecksActions();
        this.messages.addAll(messages);

        long after = System.currentTimeMillis();
        System.err.println("walk: " + (after - before));
    }

    private SortedSet<LocalizedMessage> executeAllChecksActions() {
        List<Future<CheckerExecutionResult>> actionsCallables =
                checkToVisitingContextMap.entrySet().stream()
                        .map(x -> createRunActionsCallable(x.getKey(), x.getValue()))
                        .map(executor::submit)
                        .collect(Collectors.toList());

        SortedSet<LocalizedMessage> resultMessages = new TreeSet<>();
        for (Future<CheckerExecutionResult> actionsCallable : actionsCallables) {
            SortedSet<LocalizedMessage> taskMessages;
            try {
                taskMessages = finishTask(actionsCallable);
            }
            catch (Exception ex) {
                throw new RuntimeException("Unable fo finish task", ex);
            }

            resultMessages.addAll(taskMessages);
        }

        return resultMessages;
    }

    private SortedSet<LocalizedMessage> finishTask(Future<CheckerExecutionResult> actionsCallable)
            throws Exception {
        CheckerExecutionResult result = actionsCallable.get();
        if (result.getThrownException() != null) {
            throw new RuntimeException("An exception thrown during task execution",
                    result.getThrownException());
        }
        return result.getMessages();
    }

    private Callable<CheckerExecutionResult> createRunActionsCallable(AbstractCheck check, List<AbstractAstAction> actions) {
        return () -> {
            try {
                final SortedSet<LocalizedMessage> messages = runActions(check, actions);
                return new CheckerExecutionResult(messages);
            } catch (Exception ex) {
                return new CheckerExecutionResult(ex);
            }
        };
    }

    private SortedSet<LocalizedMessage> runActions(AbstractCheck check, List<AbstractAstAction> actions) {
        for (AbstractAstAction action : actions) {
            action.apply(check);
        }

        final FinishTreeAction finishTreeAction = (FinishTreeAction) actions.get(actions.size() - 1);
        return finishTreeAction.getMessages();
    }

    private void notifyBegin(DetailAST rootAST, FileContents contents,
            AstState astState) {
        long before = System.currentTimeMillis();
        final Set<AbstractCheck> checks;

        if (astState == AstState.WITH_COMMENTS) {
            checks = commentChecks;
        }
        else {
            checks = ordinaryChecks;
        }

        final BeginTreeAction action = new BeginTreeAction(rootAST, contents);
        for (AbstractCheck check : checks) {
            registerAction(check, action);
        }

        long after = System.currentTimeMillis();
//        System.err.println("begin: " + (after - before));
    }

    private void registerAction(AbstractCheck check, AbstractAstAction action) {
        checkToVisitingContextMap.computeIfAbsent(check, ck -> new ArrayList<>()).add(action);
    }

    /**
     * Processes a node calling interested checks at each node.
     * Uses iterative algorithm.
     * @param root the root of tree for process
     * @param astState state of AST.
     */
    private void processIter(DetailAST root, AstState astState) {
        DetailAST curNode = root;
        while (curNode != null) {
            notifyVisit(curNode, astState);
            DetailAST toVisit = curNode.getFirstChild();
            while (curNode != null && toVisit == null) {
                notifyLeave(curNode, astState);
                toVisit = curNode.getNextSibling();
                if (toVisit == null) {
                    curNode = curNode.getParent();
                }
            }
            curNode = toVisit;
        }
    }

    /**
     * Notify checks that visiting a node.
     * @param ast the node to notify for.
     * @param astState state of AST.
     */
    private void notifyVisit(DetailAST ast, AstState astState) {
        long before = System.currentTimeMillis();
        final Collection<AbstractCheck> visitors = getListOfChecks(ast, astState);

        if (visitors != null) {
            doVisitToken(ast, visitors);
        }
        long after = System.currentTimeMillis();
//        System.err.println("notify visit: " + (after - before));
    }

    /**
     * Notify checks that leaving a node.
     * @param ast
     *        the node to notify for
     * @param astState state of AST.
     */
    private void notifyLeave(DetailAST ast, AstState astState) {
        long before = System.currentTimeMillis();
        final Collection<AbstractCheck> visitors = getListOfChecks(ast, astState);

        if (visitors != null) {
            doLeaveToken(ast, visitors);
        }
        long after = System.currentTimeMillis();
//        System.err.println("leave: " + (after - before));
    }

    /**
     * Method returns list of checks.
     *
     * @param ast
     *            the node to notify for
     * @param astState
     *            state of AST.
     * @return list of visitors
     */
    private Collection<AbstractCheck> getListOfChecks(DetailAST ast, AstState astState) {
        Collection<AbstractCheck> visitors = null;
        final String tokenType = TokenUtils.getTokenName(ast.getType());

        if (astState == AstState.WITH_COMMENTS) {
            if (tokenToCommentChecks.containsKey(tokenType)) {
                visitors = tokenToCommentChecks.get(tokenType);
            }
        }
        else {
            if (tokenToOrdinaryChecks.containsKey(tokenType)) {
                visitors = tokenToOrdinaryChecks.get(tokenType);
            }
        }
        return visitors;
    }

    /**
     * Notify checks that we have finished walking a tree.
     * @param rootAST the root of the tree.
     * @param astState state of AST.
     */
    private void notifyEnd(DetailAST rootAST, AstState astState) {
        final Set<AbstractCheck> checks;

        if (astState == AstState.WITH_COMMENTS) {
            checks = commentChecks;
        }
        else {
            checks = ordinaryChecks;
        }

        doNotifyEnd(rootAST, checks);
    }

    /**
     * A checker execution result. Contains all information about executed task -
     * a succesful execution result or thrown exception.
     * @author Andrew Kuchev.
     */
    private static final class CheckerExecutionResult {
        /** The exception, thrown while processing the task. */
        private final Exception thrownException;

        /** The exception, thrown while processing the task. */
        private final SortedSet<LocalizedMessage> messages;


        public CheckerExecutionResult(Exception thrownException) {
            this(thrownException, null);
        }

        public CheckerExecutionResult(SortedSet<LocalizedMessage> messages) {
            this(null, messages);
        }

        private CheckerExecutionResult(Exception thrownException,
                SortedSet<LocalizedMessage> messages) {
            this.thrownException = thrownException;
            this.messages = messages;
        }

        /**
         * Returns thrown exception.
         * @return thrown exception.
         */
        public Exception getThrownException() {
            return thrownException;
        }

        public SortedSet<LocalizedMessage> getMessages() {
            return messages;
        }
    }

    private abstract static class AbstractAstAction {
        protected final DetailAST ast;

        protected AbstractAstAction(DetailAST ast) {
            this.ast = ast;
        }

        public abstract void apply(AbstractCheck check);
    }

    private static final class BeginTreeAction extends AbstractAstAction {
        private final FileContents contents;

        protected BeginTreeAction(DetailAST ast, FileContents contents) {
            super(ast);
            this.contents = contents;
        }

        @Override
        public void apply(AbstractCheck check) {
            check.setFileContents(contents);
            check.clearMessages();
            check.beginTree(ast);
        }
    }

    private static final class VisitTokenAction extends AbstractAstAction {

        protected VisitTokenAction(DetailAST ast) {
            super(ast);
        }

        @Override
        public void apply(AbstractCheck check) {
            check.visitToken(ast);
        }
    }

    private static final class LeaveTokenAction extends AbstractAstAction {

        public LeaveTokenAction(DetailAST ast) {
            super(ast);
        }

        @Override
        public void apply(AbstractCheck check) {
            check.leaveToken(ast);
        }
    }

    private static final class FinishTreeAction extends AbstractAstAction {
        private SortedSet<LocalizedMessage> messages;

        protected FinishTreeAction(DetailAST ast) {
            super(ast);
        }

        @Override
        public void apply(AbstractCheck check) {
            check.finishTree(ast);
            messages = check.getMessages();
        }

        public SortedSet<LocalizedMessage> getMessages() {
            return messages;
        }
    }
}
