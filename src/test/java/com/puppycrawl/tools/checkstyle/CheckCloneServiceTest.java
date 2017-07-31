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

import static com.puppycrawl.tools.checkstyle.internal.TestUtils.assertUtilsClassHasPrivateConstructor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractFileSetCheck;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.api.FileSetCheck;
import com.puppycrawl.tools.checkstyle.api.FileText;
import com.puppycrawl.tools.checkstyle.utils.CommonUtils;

public class CheckCloneServiceTest extends BaseCheckTestSupport {
    private static final Logger LOG =
            Logger.getLogger(CheckCloneService.class.getName()).getParent();
    private static final Handler[] HANDLERS = LOG.getHandlers();
    private static final Level ORIGINAL_LOG_LEVEL = LOG.getLevel();

    @SuppressWarnings("unchecked")
    private static List<FileSetCheck> getFileSetChecks(Checker checker) {
        return (List<FileSetCheck>) Whitebox.getInternalState(checker, "fileSetChecks");
    }

    @SuppressWarnings("unchecked")
    private static AbstractCheck getFirstTreeWalkerCheck(Checker checker) {
        final List<FileSetCheck> fileSetChecks = getFileSetChecks(checker);
        final TreeWalker treeWalker = fileSetChecks.stream()
                .filter(x -> x instanceof TreeWalker).findFirst().map(x -> (TreeWalker) x)
                .orElseThrow(() -> new IllegalArgumentException(
                        "The checker does not contain TreeWalker"));

        final Collection<AbstractCheck> checks =
                (Collection<AbstractCheck>) Whitebox.getInternalState(treeWalker, "ordinaryChecks");

        return checks.stream().findFirst().orElseThrow(() -> new IllegalArgumentException(
                "The tree walker does not contain checks"));
    }

    private static DefaultConfiguration createCheckerConfigWithoutWrappingWithFileSetCheck(
            Configuration config) {
        final DefaultConfiguration dc = new DefaultConfiguration("root");
        dc.addAttribute("charset", "UTF-8");
        dc.addChild(config);
        return dc;
    }

    private static TestLogHandler createTestLogHandler(Level level) {
        final TestLogHandler handler = new TestLogHandler();
        handler.setLevel(level);
        handler.setFilter(new Filter() {
            private final String packageName = CheckCloneService.class.getPackage().getName();

            @Override
            public boolean isLoggable(LogRecord record) {
                return record.getLoggerName().startsWith(packageName);
            }
        });
        LOG.addHandler(handler);
        LOG.setLevel(level);
        return handler;
    }

    @Before
    public void setUp() {
        // restore original logging level and HANDLERS to prevent bleeding into other tests

        LOG.setLevel(ORIGINAL_LOG_LEVEL);

        for (Handler handler : LOG.getHandlers()) {
            boolean found = false;

            for (Handler savedHandler : HANDLERS) {
                if (handler == savedHandler) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                LOG.removeHandler(handler);
            }
        }
    }

    @Test
    public void testIsProperUtilsClass() throws ReflectiveOperationException {
        assertUtilsClassHasPrivateConstructor(CheckCloneService.class, true);
    }

    @Test
    public void testCloneFileSetCheckMarkedAsOneCheckInstancePerThread() throws Exception {
        final DefaultConfiguration fileSetConfiguration =
                createCheckConfig(TestOneInstancePerThreadFileSetCheck.class);
        fileSetConfiguration.addAttribute("strProperty", "just a test");
        fileSetConfiguration.addAttribute("intProperty", "42");
        final Checker checker = forceCreateChecker(fileSetConfiguration);
        final List<FileSetCheck> fileSetChecks = getFileSetChecks(checker);
        final List<FileSetCheck> fileSetCheckClones =
                CheckCloneService.cloneFileSetChecks(fileSetChecks);

        final TestOneInstancePerThreadFileSetCheck original =
                (TestOneInstancePerThreadFileSetCheck) fileSetChecks.get(0);
        final TestOneInstancePerThreadFileSetCheck clone =
                (TestOneInstancePerThreadFileSetCheck) fileSetCheckClones.get(0);

        assertNotSame("The clone check must not be same as the original check", original, clone);
        assertSame("The clone and original check must have same class",
                original.getClass(), clone.getClass());
        assertEquals("The clone must have a valid strProperty value",
                "just a test", clone.getStrProperty());
        assertEquals("The clone must have a valid intProperty value",
                42, clone.getIntProperty());
        assertEquals("The clone must refer to original check",
                original, clone.getOriginalFileSetCheck());
    }

    @Test
    public void testCloneFileSetCheckMarkedAsOneCheckInstancePerApplication() throws Exception {
        final DefaultConfiguration fileSetConfiguration =
                createCheckConfig(TestOneInstancePerApplicationFileSetCheck.class);
        fileSetConfiguration.addAttribute("strProperty", "just a test");
        fileSetConfiguration.addAttribute("intProperty", "42");
        final Checker checker = forceCreateChecker(fileSetConfiguration);
        final List<FileSetCheck> fileSetChecks = getFileSetChecks(checker);
        final List<FileSetCheck> fileSetCheckClones =
                CheckCloneService.cloneFileSetChecks(fileSetChecks);
        final TestOneInstancePerApplicationFileSetCheck original =
                (TestOneInstancePerApplicationFileSetCheck) fileSetChecks.get(0);
        final TestOneInstancePerApplicationFileSetCheck clone =
                (TestOneInstancePerApplicationFileSetCheck) fileSetCheckClones.get(0);

        assertSame("The check must not be cloned", original, clone);
    }

    @Test
    public void testCloneUnmarkedFileSetCheckWithLogDisabled() throws Exception {
        final TestLogHandler handler = createTestLogHandler(Level.OFF);

        final DefaultConfiguration fileSetConfiguration =
                createCheckConfig(TestUnmarkedFileSetCheck.class);
        fileSetConfiguration.addAttribute("strProperty", "just a test");
        fileSetConfiguration.addAttribute("intProperty", "42");
        final Checker checker = forceCreateChecker(fileSetConfiguration);
        final List<FileSetCheck> fileSetChecks = getFileSetChecks(checker);
        final List<FileSetCheck> fileSetCheckClones =
                CheckCloneService.cloneFileSetChecks(fileSetChecks);
        final TestUnmarkedFileSetCheck original =
                (TestUnmarkedFileSetCheck) fileSetChecks.get(0);
        final TestUnmarkedFileSetCheck clone =
                (TestUnmarkedFileSetCheck) fileSetCheckClones.get(0);

        assertEquals("The CheckCloneService must not log anything if the debug is disabled",
                0, handler.getLogs().size());
        assertSame("The check must not be cloned", original, clone);
    }

    @Test
    public void testCloneUnmarkedFileSetCheckWithLogEnabled() throws Exception {
        final TestLogHandler handler = createTestLogHandler(Level.FINE);

        final DefaultConfiguration fileSetConfiguration =
                createCheckConfig(TestUnmarkedFileSetCheck.class);
        fileSetConfiguration.addAttribute("strProperty", "just a test");
        fileSetConfiguration.addAttribute("intProperty", "42");
        final Checker checker = forceCreateChecker(fileSetConfiguration);
        final List<FileSetCheck> fileSetChecks = getFileSetChecks(checker);
        final List<FileSetCheck> fileSetCheckClones =
                CheckCloneService.cloneFileSetChecks(fileSetChecks);
        final TestUnmarkedFileSetCheck original =
                (TestUnmarkedFileSetCheck) fileSetChecks.get(0);
        final TestUnmarkedFileSetCheck clone =
                (TestUnmarkedFileSetCheck) fileSetCheckClones.get(0);

        assertEquals("There should be exactly one log message", 1, handler.getLogs().size());
        final LogRecord logEntry = handler.getLogs().get(0);

        assertEquals("The check clone service must log a warning for a check without a marker",
                String.format(Locale.ROOT, "Check TestUnmarkedFileSetCheck should implement "
                        + "either OneCheckInstancePerThread or OneCheckInstancePerApplication%n"),
                logEntry.getMessage());
        assertSame("The check must not be cloned", original, clone);
    }

    @Test
    public void testCloneCheckMarkedAsOneCheckInstancePerThread() throws Exception {
        final DefaultConfiguration fileSetConfiguration =
                createCheckConfig(TestOneInstancePerThreadCheck.class);
        fileSetConfiguration.addAttribute("strProperty", "just a test");
        fileSetConfiguration.addAttribute("intProperty", "42");
        final Checker checker = createChecker(fileSetConfiguration);
        final TestOneInstancePerThreadCheck original =
                (TestOneInstancePerThreadCheck) getFirstTreeWalkerCheck(checker);
        final TestOneInstancePerThreadCheck clone =
                (TestOneInstancePerThreadCheck) CheckCloneService.cloneCheck(original);

        assertNotSame("The clone check must not be same as the original check", original, clone);
        assertSame("The clone and original check must have same class",
                original.getClass(), clone.getClass());
        assertEquals("The clone must have a valid strProperty value",
                "just a test", clone.getStrProperty());
        assertEquals("The clone must have a valid intProperty value",
                42, clone.getIntProperty());
    }

    @Test
    public void testCloneCheckMarkedAsOneCheckInstancePerApplication() throws Exception {
        final DefaultConfiguration fileSetConfiguration =
                createCheckConfig(TestOneInstancePerApplicationCheck.class);
        fileSetConfiguration.addAttribute("strProperty", "just a test");
        fileSetConfiguration.addAttribute("intProperty", "42");
        final Checker checker = createChecker(fileSetConfiguration);
        final TestOneInstancePerApplicationCheck original =
                (TestOneInstancePerApplicationCheck) getFirstTreeWalkerCheck(checker);
        final TestOneInstancePerApplicationCheck clone =
                (TestOneInstancePerApplicationCheck) CheckCloneService.cloneCheck(original);

        assertSame("The check must not be cloned", original, clone);
    }

    @Test
    public void testCloneUnmarkedCheckWithDebugLogDisabled() throws Exception {
        final TestLogHandler handler = createTestLogHandler(Level.OFF);

        final DefaultConfiguration fileSetConfiguration =
                createCheckConfig(TestUnmarkedCheck.class);
        fileSetConfiguration.addAttribute("strProperty", "just a test");
        fileSetConfiguration.addAttribute("intProperty", "42");
        final Checker checker = createChecker(fileSetConfiguration);
        final TestUnmarkedCheck original = (TestUnmarkedCheck) getFirstTreeWalkerCheck(checker);
        final TestUnmarkedCheck clone = (TestUnmarkedCheck) CheckCloneService.cloneCheck(original);

        assertEquals("The CheckCloneService must not log anything if the debug is disabled",
                0, handler.getLogs().size());
        assertSame("The check must not be cloned", original, clone);
    }

    @Test
    public void testCloneUnmarkedCheckWithDebugLogEnabled() throws Exception {
        final TestLogHandler handler = createTestLogHandler(Level.FINE);

        final DefaultConfiguration fileSetConfiguration =
                createCheckConfig(TestUnmarkedCheck.class);
        fileSetConfiguration.addAttribute("strProperty", "just a test");
        fileSetConfiguration.addAttribute("intProperty", "42");
        final Checker checker = createChecker(fileSetConfiguration);
        final TestUnmarkedCheck original = (TestUnmarkedCheck) getFirstTreeWalkerCheck(checker);
        final TestUnmarkedCheck clone = (TestUnmarkedCheck) CheckCloneService.cloneCheck(original);

        assertEquals("There must be exactly one log message", 1, handler.getLogs().size());
        final LogRecord logEntry = handler.getLogs().get(0);

        assertEquals("The check clone service must log a warning for a check without a marker",
                String.format(Locale.ROOT, "Check TestUnmarkedCheck should implement "
                        + "either OneCheckInstancePerThread or OneCheckInstancePerApplication%n"),
                logEntry.getMessage());
        assertSame("The check must not be cloned", original, clone);
    }

    @Test
    public void testExceptionWhileCloningCheck() throws Exception {
        final DefaultConfiguration fileSetConfiguration =
                createCheckConfig(TestOneInstancePerThreadCheck.class);
        final Checker checker = createChecker(fileSetConfiguration);
        final TestOneInstancePerThreadCheck original =
                (TestOneInstancePerThreadCheck) getFirstTreeWalkerCheck(checker);
        final DefaultConfiguration originalConfig =
                (DefaultConfiguration) original.getConfiguration();
        final Field nameField = originalConfig.getClass().getDeclaredField("name");
        nameField.setAccessible(true);
        nameField.set(originalConfig, "invalid module name");

        try {
            CheckCloneService.cloneCheck(original);
            fail("An exception is expected");
        }
        catch (IllegalStateException ex) {
            assertEquals("The exception must contain a valid message",
                    "An unexpected exception raised while cloning check", ex.getMessage());
            assertTrue("The cause exception must contain the 'invalid module name', "
                            + "because it was mentioned in the config",
                    ex.getCause().getMessage().startsWith(
                            "Unable to instantiate 'invalid module name' class"));
        }
    }

    private Checker forceCreateChecker(Configuration checkConfig)
            throws Exception {
        final DefaultConfiguration dc =
                createCheckerConfigWithoutWrappingWithFileSetCheck(checkConfig);
        final Checker checker = new Checker();
        checker.setModuleClassLoader(Thread.currentThread().getContextClassLoader());
        checker.configure(dc);
        return checker;
    }

    public static final class TestLogHandler extends Handler {
        private final List<LogRecord> logs = new ArrayList<>();

        @Override
        public void publish(LogRecord logRecord) {
            if (isLoggable(logRecord)) {
                logs.add(logRecord);
            }
        }

        @Override
        public void flush() {
            // nothing to flush
        }

        @Override
        public void close() {
            // nothing to close
        }

        public List<LogRecord> getLogs() {
            return Collections.unmodifiableList(logs);
        }
    }

    public static final class TestOneInstancePerApplicationCheck extends AbstractCheck
            implements OneCheckInstancePerApplication {
        private String strProperty;
        private int intProperty;

        public String getStrProperty() {
            return strProperty;
        }

        public void setStrProperty(String strProperty) {
            this.strProperty = strProperty;
        }

        public int getIntProperty() {
            return intProperty;
        }

        public void setIntProperty(int intProperty) {
            this.intProperty = intProperty;
        }

        @Override
        public int[] getDefaultTokens() {
            return CommonUtils.EMPTY_INT_ARRAY;
        }

        @Override
        public int[] getAcceptableTokens() {
            return CommonUtils.EMPTY_INT_ARRAY;
        }

        @Override
        public int[] getRequiredTokens() {
            return CommonUtils.EMPTY_INT_ARRAY;
        }
    }

    public static final class TestOneInstancePerApplicationFileSetCheck extends AbstractFileSetCheck
            implements OneCheckInstancePerApplication {
        private String strProperty;
        private int intProperty;

        public String getStrProperty() {
            return strProperty;
        }

        public void setStrProperty(String strProperty) {
            this.strProperty = strProperty;
        }

        public int getIntProperty() {
            return intProperty;
        }

        public void setIntProperty(int intProperty) {
            this.intProperty = intProperty;
        }

        @Override
        protected void processFiltered(File file, FileText fileText) {
            // do noting
        }
    }

    public static final class TestOneInstancePerThreadCheck extends AbstractCheck
            implements OneCheckInstancePerThread {
        private String strProperty;
        private int intProperty;

        public String getStrProperty() {
            return strProperty;
        }

        public void setStrProperty(String strProperty) {
            this.strProperty = strProperty;
        }

        public int getIntProperty() {
            return intProperty;
        }

        public void setIntProperty(int intProperty) {
            this.intProperty = intProperty;
        }

        @Override
        public int[] getDefaultTokens() {
            return CommonUtils.EMPTY_INT_ARRAY;
        }

        @Override
        public int[] getAcceptableTokens() {
            return CommonUtils.EMPTY_INT_ARRAY;
        }

        @Override
        public int[] getRequiredTokens() {
            return CommonUtils.EMPTY_INT_ARRAY;
        }
    }

    public static final class TestOneInstancePerThreadFileSetCheck extends AbstractFileSetCheck
            implements OneCheckInstancePerThread {
        private String strProperty;
        private int intProperty;
        private FileSetCheck originalFileSetCheck;

        public String getStrProperty() {
            return strProperty;
        }

        public void setStrProperty(String strProperty) {
            this.strProperty = strProperty;
        }

        public int getIntProperty() {
            return intProperty;
        }

        public void setIntProperty(int intProperty) {
            this.intProperty = intProperty;
        }

        public FileSetCheck getOriginalFileSetCheck() {
            return originalFileSetCheck;
        }

        @Override
        protected void processFiltered(File file, FileText fileText) {
            // do noting
        }

        @Override
        public void finishCloning(FileSetCheck original) {
            super.finishCloning(original);
            originalFileSetCheck = original;
        }
    }

    public static final class TestUnmarkedCheck extends AbstractCheck {
        private String strProperty;
        private int intProperty;

        public String getStrProperty() {
            return strProperty;
        }

        public void setStrProperty(String strProperty) {
            this.strProperty = strProperty;
        }

        public int getIntProperty() {
            return intProperty;
        }

        public void setIntProperty(int intProperty) {
            this.intProperty = intProperty;
        }

        @Override
        public int[] getDefaultTokens() {
            return CommonUtils.EMPTY_INT_ARRAY;
        }

        @Override
        public int[] getAcceptableTokens() {
            return CommonUtils.EMPTY_INT_ARRAY;
        }

        @Override
        public int[] getRequiredTokens() {
            return CommonUtils.EMPTY_INT_ARRAY;
        }
    }

    public static final class TestUnmarkedFileSetCheck extends AbstractFileSetCheck {
        private String strProperty;
        private int intProperty;

        public String getStrProperty() {
            return strProperty;
        }

        public void setStrProperty(String strProperty) {
            this.strProperty = strProperty;
        }

        public int getIntProperty() {
            return intProperty;
        }

        public void setIntProperty(int intProperty) {
            this.intProperty = intProperty;
        }

        @Override
        protected void processFiltered(File file, FileText fileText) {
            // do noting
        }
    }
}
