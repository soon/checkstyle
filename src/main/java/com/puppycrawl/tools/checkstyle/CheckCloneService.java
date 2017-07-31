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

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configurable;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.api.Contextualizable;
import com.puppycrawl.tools.checkstyle.api.FileSetCheck;

/**
 * A service for clonning check instances.
 * @author Andrew Kuchev.
 */
public final class CheckCloneService {
    /** Logger for CheckCloneService. */
    private static final Log LOG = LogFactory.getLog(CheckCloneService.class);

    /** A log pattern for case when a check does not implement appropriate interface. */
    private static final String CHECK_MUST_IMPLEMENT_INTERFACE_MSG =
            "Check %s should implement either OneCheckInstancePerThread "
                    + "or OneCheckInstancePerApplication%n";

    /** A private constructor is required for utility class. */
    private CheckCloneService() {
    }

    /**
     * Clones the given check.
     * @param check A check to be cloned.
     * @return Cloned check.
     * @noinspection InstanceofChain
     */
    // we cannot move this logic to the AbstractCheck subclasses,
    // because they should not know about PackageObjectFactory
    // and other configuration details
    public static AbstractCheck cloneCheck(AbstractCheck check) {
        final AbstractCheck clone;
        if (check instanceof OneCheckInstancePerApplication) {
            clone = check;
        }
        else if (check instanceof OneCheckInstancePerThread) {
            final ClassLoader classLoader = CheckCloneService.class.getClassLoader();
            final ModuleFactory factory = new PackageObjectFactory(
                    Checker.class.getPackage().getName(), classLoader);
            clone = doCloneCheck(check, factory);
            clone.init();
        }
        else {
            if (LOG.isDebugEnabled()) {
                final String checkClassName = check.getClass().getSimpleName();
                final String message = String.format(Locale.ROOT,
                        CHECK_MUST_IMPLEMENT_INTERFACE_MSG, checkClassName);
                LOG.debug(message);
            }
            clone = check;
        }

        return clone;
    }

    /**
     * Clones the given fileset check.
     * @param originalCheck A fileset check to clone.
     * @return Cloned fileset check.
     * @noinspection InstanceofChain
     */
    // we cannot move this logic to the FileSetCheck subclasses,
    // because they should not know about PackageObjectFactory
    // and other configuration details
    public static FileSetCheck cloneFileSetCheck(FileSetCheck originalCheck) {
        final FileSetCheck clone;

        if (originalCheck instanceof OneCheckInstancePerApplication) {
            clone = originalCheck;
        }
        else if (originalCheck instanceof OneCheckInstancePerThread) {
            final ClassLoader classLoader = CheckCloneService.class.getClassLoader();
            final ModuleFactory factory = new PackageObjectFactory(
                    Checker.class.getPackage().getName(), classLoader);
            clone = doCloneCheck(originalCheck, factory);
            clone.init();
            clone.finishCloning(originalCheck);
        }
        else {
            if (LOG.isDebugEnabled()) {
                final String checkClassName = originalCheck.getClass().getSimpleName();
                final String message = String.format(Locale.ROOT,
                        CHECK_MUST_IMPLEMENT_INTERFACE_MSG, checkClassName);
                LOG.debug(message);
            }
            clone = originalCheck;
        }

        return clone;
    }

    /**
     * Clones a list of fileset checks.
     * @param fileSetChecks A list of fileset checks to be cloned.
     * @return A list of cloned fileset checks.
     */
    public static List<FileSetCheck> cloneFileSetChecks(List<FileSetCheck> fileSetChecks) {
        return fileSetChecks.stream().map(CheckCloneService::cloneFileSetCheck)
                    .collect(Collectors.toList());
    }

    /**
     * Clones the check using the given module factory.
     * @param check A check to clone.
     * @param factory A factory for creating new checks.
     * @param <T> The generic check type.
     * @return The check clone.
     */
    private static <T extends Configurable & Contextualizable> T doCloneCheck(
            T check, ModuleFactory factory) {
        final Configuration config = check.getConfiguration();
        final String moduleName = config.getName();
        try {
            final Object module = factory.createModule(moduleName);
            final T clone = (T) module;
            clone.contextualize(check.getContext());
            clone.configure(config);
            return clone;
        }
        catch (CheckstyleException ex) {
            throw new IllegalStateException(
                    "An unexpected exception raised while cloning check", ex);
        }
    }
}
