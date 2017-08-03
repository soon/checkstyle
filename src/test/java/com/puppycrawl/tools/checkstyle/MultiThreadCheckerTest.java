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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.puppycrawl.tools.checkstyle.api.AbstractFileSetCheck;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.FileSetCheck;
import com.puppycrawl.tools.checkstyle.api.FileText;

public class MultiThreadCheckerTest extends BaseCheckTestSupport {
    private static MultiThreadChecker createMultiThreadChecker(
            Class<? extends AbstractFileSetCheck> fileSetCheckClass,
            ThreadModeSettings threadModeSettings) throws Exception {

        final DefaultConfiguration checkConfig = createCheckConfig(fileSetCheckClass);
        final DefaultConfiguration mtCheckerConfig = new DefaultConfiguration(
                "root", threadModeSettings);
        mtCheckerConfig.addAttribute("charset", "UTF-8");
        mtCheckerConfig.addChild(checkConfig);

        final MultiThreadChecker checker = new MultiThreadChecker();
        checker.setModuleClassLoader(Thread.currentThread().getContextClassLoader());
        checker.configure(mtCheckerConfig);

        return checker;
    }

    @Before
    public void setUp() throws Exception {
        TestOneInstancePerThreadFileSetCheck.INSTANCES.clear();
        TestOneInstancePerApplicationFileSetCheck.INSTANCES.clear();
    }

    @Test
    public void testExecutorKindAndPoolSize() throws Exception {
        final DefaultConfiguration config =
                new DefaultConfiguration("root", new ThreadModeSettings(4, 2));
        final MultiThreadChecker checker = new MultiThreadChecker();
        checker.setModuleClassLoader(MultiThreadChecker.class.getClassLoader());
        checker.configure(config);
        final List<File> files = Collections.emptyList();
        checker.process(files);

        final Field executorField = checker.getClass().getDeclaredField("executor");
        executorField.setAccessible(true);
        final ThreadPoolExecutor executor = (ThreadPoolExecutor) executorField.get(checker);

        assertEquals(4, executor.getMaximumPoolSize());
    }

    @Test
    public void testFinishProcessingAndDestroyAreCalledForCheckClone() throws Exception {
        final MultiThreadChecker checker = createMultiThreadChecker(
                TestOneInstancePerThreadFileSetCheck.class, new ThreadModeSettings(1, 1));

        final List<File> files = Arrays.asList(new File("dummy1.java"), new File("dummy2.java"));
        checker.process(files);

        final List<TestOneInstancePerThreadFileSetCheck> clones =
                TestOneInstancePerThreadFileSetCheck.getClones();
        assertEquals(1, clones.size());
        final TestOneInstancePerThreadFileSetCheck clone = clones.get(0);
        assertEquals(1, clone.getFinishedProcessingCount());
        assertEquals(1, clone.getDestroyCount());
    }

    @Test
    public void testUsesSameInstanceForOneCheckInstancePerThreadFileSetCheck() throws Exception {
        final MultiThreadChecker checker = createMultiThreadChecker(
                TestOneInstancePerApplicationFileSetCheck.class, new ThreadModeSettings(2, 1));

        final List<File> files = Collections.singletonList(new File("dummy.java"));
        checker.process(files);

        assertEquals(1, TestOneInstancePerApplicationFileSetCheck.INSTANCES.size());
        final TestOneInstancePerApplicationFileSetCheck fileSetCheck =
                TestOneInstancePerApplicationFileSetCheck.INSTANCES.get(0);
        assertEquals(1, fileSetCheck.getFinishedProcessingCount());
        assertEquals(1, fileSetCheck.getDestroyCount());
    }

    @Test
    public void testFailingFileSetCheck() throws Exception {
        final MultiThreadChecker checker = createMultiThreadChecker(
                TestFailingFileSetCheck.class, new ThreadModeSettings(2, 1));

        final String filePath = getPath("InputMultiThreadChecker.java");
        final List<File> files = Collections.singletonList(new File(filePath));

        try {
            checker.process(files);
            fail("An exception is expected");
        }
        catch (CheckstyleException ex) {
            assertEquals("Exception was thrown while processing " + filePath,
                    ex.getLocalizedMessage());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public <T> void testInterruptedFuture() throws Exception {
        final Future<T> failingFuture = mock(Future.class);
        when(failingFuture.get()).thenThrow(new InterruptedException("Interrupted"));
        final ExecutorService failingExecutorService = mock(ExecutorService.class);
        final Callable<T> anyCallable = any();
        when(failingExecutorService.submit(anyCallable)).thenReturn(failingFuture);

        final MultiThreadChecker checker = createMultiThreadChecker(
                TestOneInstancePerApplicationFileSetCheck.class, new ThreadModeSettings(2, 1));
        final Field executorField = MultiThreadChecker.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        executorField.set(checker, failingExecutorService);

        final List<File> files = Collections.singletonList(
                new File(getPath("InputMultiThreadChecker.java")));
        try {
            checker.process(files);
            fail("An exception is expected");
        }
        catch (CheckstyleException ex) {
            assertEquals("Unable to execute checkstyle tasks", ex.getLocalizedMessage());
            assertTrue(ex.getCause() instanceof InterruptedException);
            assertEquals("Interrupted", ex.getCause().getLocalizedMessage());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public <T> void testExecutionExceptionFuture() throws Exception {
        final Future<T> failingFuture = mock(Future.class);
        when(failingFuture.get()).thenThrow(
                new ExecutionException(new RuntimeException("RuntimeException")));
        final ExecutorService failingExecutorService = mock(ExecutorService.class);
        final Callable<T> anyCallable = any();
        when(failingExecutorService.submit(anyCallable)).thenReturn(failingFuture);

        final MultiThreadChecker checker = createMultiThreadChecker(
                TestOneInstancePerApplicationFileSetCheck.class, new ThreadModeSettings(2, 1));
        final Field executorField = MultiThreadChecker.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        executorField.set(checker, failingExecutorService);

        final List<File> files = Collections.singletonList(
                new File(getPath("InputMultiThreadChecker.java")));
        try {
            checker.process(files);
            fail("An exception is expected");
        }
        catch (CheckstyleException ex) {
            assertEquals("Unable to execute checkstyle tasks", ex.getLocalizedMessage());
            assertTrue(ex.getCause() instanceof ExecutionException);
            assertTrue(ex.getCause().getCause() instanceof RuntimeException);
            assertEquals("RuntimeException", ex.getCause().getCause().getLocalizedMessage());
        }
    }

    public abstract static class AbstractTestFileSetCheckWithStats extends AbstractFileSetCheck {
        private int finishedProcessingCount;
        private int destroyCount;
        private AbstractTestFileSetCheckWithStats originalCheck;

        public final int getFinishedProcessingCount() {
            return finishedProcessingCount;
        }

        public final int getDestroyCount() {
            return destroyCount;
        }

        public final AbstractTestFileSetCheckWithStats getOriginalCheck() {
            return originalCheck;
        }

        @Override
        public void finishProcessing() {
            super.finishProcessing();
            finishedProcessingCount++;
        }

        @Override
        public void destroy() {
            super.destroy();
            destroyCount++;
        }

        public final boolean isClone() {
            return originalCheck != null;
        }

        @Override
        public void finishCloning(FileSetCheck originalFileSetCheck) {
            super.finishCloning(originalFileSetCheck);
            originalCheck = (AbstractTestFileSetCheckWithStats) originalFileSetCheck;
        }
    }

    public static final class TestOneInstancePerThreadFileSetCheck
            extends AbstractTestFileSetCheckWithStats
            implements OneCheckInstancePerThread {

        /**
         * Used to track clone instances.
         */
        public static final List<TestOneInstancePerThreadFileSetCheck> INSTANCES =
                Collections.synchronizedList(new ArrayList<>());

        public TestOneInstancePerThreadFileSetCheck() {
            INSTANCES.add(this);
        }

        public static List<TestOneInstancePerThreadFileSetCheck> getClones() {
            return INSTANCES.stream().filter(TestOneInstancePerThreadFileSetCheck::isClone)
                    .collect(Collectors.toList());
        }

        @Override
        protected void processFiltered(File file, FileText fileText) {
            // do nothing
        }
    }

    public static final class TestOneInstancePerApplicationFileSetCheck
            extends AbstractTestFileSetCheckWithStats
            implements OneCheckInstancePerApplication {

        /**
         * Used to track clone instances.
         */
        public static final List<TestOneInstancePerApplicationFileSetCheck> INSTANCES =
                Collections.synchronizedList(new ArrayList<>());

        public TestOneInstancePerApplicationFileSetCheck() {
            INSTANCES.add(this);
        }

        public static List<TestOneInstancePerApplicationFileSetCheck> getClones() {
            return INSTANCES.stream().filter(TestOneInstancePerApplicationFileSetCheck::isClone)
                    .collect(Collectors.toList());
        }

        @Override
        protected void processFiltered(File file, FileText fileText) {
            // do nothing
        }
    }

    public static final class TestFailingFileSetCheck
            extends AbstractFileSetCheck
            implements OneCheckInstancePerApplication {

        @Override
        protected void processFiltered(File file, FileText fileText) throws CheckstyleException {
            throw new CheckstyleException("Something went wrong...");
        }
    }
}
