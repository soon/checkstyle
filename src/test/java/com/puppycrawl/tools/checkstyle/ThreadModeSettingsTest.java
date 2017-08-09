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

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Set;

import org.junit.Test;

import com.puppycrawl.tools.checkstyle.internal.CheckUtil;

public class ThreadModeSettingsTest {
    @Test
    public void testProperties() throws Exception {
        final ThreadModeSettings config = new ThreadModeSettings(1, 2);
        assertEquals("Invalid checker threads number", 1, config.getCheckerThreadsNumber());
        assertEquals("Invalid treewalker threads number", 2, config.getTreeWalkerThreadsNumber());
    }

    @Test
    public void testResolveCheckerInMultiThreadMode() throws Exception {
        final ThreadModeSettings configuration = new ThreadModeSettings(2, 2);

        final String moduleName = configuration.resolveName("Checker");
        assertEquals("The invalid MT checker module", "MultiThreadChecker", moduleName);
    }

    @Test
    public void testResolveCheckerInSingleThreadMode() throws Exception {
        final ThreadModeSettings singleThreadMode = ThreadModeSettings.SINGLE_THREAD_MODE_INSTANCE;

        assertEquals("Invalid name resolved", "Checker", singleThreadMode.resolveName("Checker"));
    }

    @Test
    public void testResolveTreeWalker() throws Exception {
        final ThreadModeSettings configuration = new ThreadModeSettings(2, 2);

        try {
            configuration.resolveName("TreeWalker");
        }
        catch (IllegalArgumentException ex) {
            assertEquals("Invalid exception message",
                    "Multi thread mode for TreeWalker module is not implemented",
                    ex.getMessage());
        }
    }

    @Test
    public void testResolveTreeWalkerInSingleThreadMode() throws Exception {
        final ThreadModeSettings singleThreadMode = ThreadModeSettings.SINGLE_THREAD_MODE_INSTANCE;
        final String actual = singleThreadMode.resolveName("TreeWalker");
        assertThat("Invalid name resolved: " + actual, actual, is("TreeWalker"));
    }

    @Test
    public void testResolveAnyOtherModule() throws Exception {
        final Set<Class<?>> allModules = CheckUtil.getCheckstyleModules();
        final ThreadModeSettings multiThreadModeSettings = new ThreadModeSettings(2, 2);
        final ThreadModeSettings singleThreadModeSettings =
                ThreadModeSettings.SINGLE_THREAD_MODE_INSTANCE;

        for (Class<?> module : allModules) {
            if (Checker.class.isAssignableFrom(module)
                    || TreeWalker.class.isAssignableFrom(module)) {
                // they're handled in other tests
                continue;
            }

            final String moduleName = module.getSimpleName();
            assertThat("Invalid name resolved",
                    singleThreadModeSettings.resolveName(moduleName), is(moduleName));
            assertThat("Invalid name resolved",
                    multiThreadModeSettings.resolveName(moduleName), is(moduleName));
        }
    }
}
