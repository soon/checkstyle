////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2017 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle;


import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FileContents;
import com.puppycrawl.tools.checkstyle.api.LocalizedMessage;
import com.puppycrawl.tools.checkstyle.utils.TokenUtils;

/**
 * Responsible for walking an abstract syntax tree and notifying interested
 * checks at each each node.
 *
 * @author Oliver Burn
 */
public final class TreeWalker extends AbstractTreeWalker {
    private SortedSet<LocalizedMessage> doNotifyEnd(DetailAST rootAST, Set<AbstractCheck> checks) {
        SortedSet<LocalizedMessage> messages = new TreeSet<>();
        for (AbstractCheck check : checks) {
            check.finishTree(rootAST);
            messages.addAll(check.getMessages());
        }
        return messages;
    }

    private void doVisitToken(DetailAST ast, Collection<AbstractCheck> visitors) {
        for (AbstractCheck check : visitors) {
            check.visitToken(ast);
        }
    }

    private void doLeaveToken(DetailAST ast, Collection<AbstractCheck> visitors) {
        for (AbstractCheck visitor : visitors) {
            visitor.leaveToken(ast);
        }
    }

    @Override
    protected void walk(DetailAST ast, FileContents contents, AstState astState) {
        long before = System.currentTimeMillis();
        notifyBegin(ast, contents, astState);

        // empty files are not flagged by javac, will yield ast == null
        if (ast != null) {
            processIter(ast, astState);
        }
        notifyEnd(ast, astState);
        long after = System.currentTimeMillis();
        System.err.println("walk: " + (after - before));
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

        for (AbstractCheck check : checks) {
            check.setFileContents(contents);
            check.clearMessages();
            check.beginTree(rootAST);
        }

        long after = System.currentTimeMillis();
//        System.err.println("begin: " + (after - before));
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

        SortedSet<LocalizedMessage> producedMessages = doNotifyEnd(rootAST, checks);
        messages.addAll(producedMessages);
    }
}
