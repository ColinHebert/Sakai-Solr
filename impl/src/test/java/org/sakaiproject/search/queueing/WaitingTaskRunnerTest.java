package org.sakaiproject.search.queueing;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.search.indexing.Task;
import org.sakaiproject.search.indexing.TaskHandler;
import org.sakaiproject.search.indexing.exception.NestedTaskHandlingException;
import org.sakaiproject.search.indexing.exception.TaskHandlingException;
import org.sakaiproject.search.indexing.exception.TemporaryTaskHandlingException;
import org.sakaiproject.thread_local.api.ThreadLocalManager;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Colin Hebert
 */
public class WaitingTaskRunnerTest {
    private WaitingTaskRunner waitingTaskRunner;
    @Mock
    private SecurityService mockSecurityService;
    @Mock
    private TaskHandler mockTaskHandler;
    @Mock
    private IndexQueueing mockIndexQueueing;
    @Mock
    private ThreadLocalManager threadLocalManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        waitingTaskRunner = new WaitingTaskRunner() {
        };
        waitingTaskRunner.setIndexQueueing(mockIndexQueueing);
        waitingTaskRunner.setTaskHandler(mockTaskHandler);
        waitingTaskRunner.setSecurityService(mockSecurityService);
        waitingTaskRunner.setThreadLocalManager(threadLocalManager);
    }

    @Test
    public void testTemporaryExceptionQueueNewTask() {
        Task task = mock(Task.class);
        doThrow(new TemporaryTaskHandlingException(task)).when(mockTaskHandler).executeTask(any(Task.class));

        waitingTaskRunner.runTask(mock(Task.class));

        verify(mockIndexQueueing).addTaskToQueue(task);
    }

    @Test
    public void testExceptionDontQueueNewTask() {
        doThrow(new TaskHandlingException()).when(mockTaskHandler).executeTask(any(Task.class));

        waitingTaskRunner.runTask(mock(Task.class));

        verify(mockIndexQueueing, never()).addTaskToQueue(any(Task.class));
    }

    @Test
    public void testNestedExceptionWithTempExceptionQueueNewTask() {
        doThrow(createNestedException(1, 0)).when(mockTaskHandler).executeTask(any(Task.class));

        waitingTaskRunner.runTask(mock(Task.class));

        verify(mockIndexQueueing).addTaskToQueue(any(Task.class));
    }

    @Test
    public void testMultipleTemporaryExceptionUnlockOtherThreads() throws Exception {
        Task failingTask = mock(Task.class);
        doThrow(createNestedException(2, 0)).when(mockTaskHandler).executeTask(failingTask);

        executeTaskWithin(failingTask, 1000);
        executeTaskWithin(mock(Task.class), 1000);
    }

    private NestedTaskHandlingException createNestedException(int temporaryExceptionsCount, int exceptionsCount) {
        NestedTaskHandlingException nestedTaskHandlingException = new NestedTaskHandlingException();
        for (int i = 0; i < temporaryExceptionsCount; i++) {
            nestedTaskHandlingException.addTaskHandlingException(new TemporaryTaskHandlingException(mock(Task.class)));
        }
        for (int i = 0; i < exceptionsCount; i++) {
            nestedTaskHandlingException.addTaskHandlingException(new TaskHandlingException());
        }

        return nestedTaskHandlingException;
    }

    private void executeTaskWithin(Task task, long millis) throws InterruptedException {
        Thread separateTaskThread = createSeparateTaskThread(task);
        separateTaskThread.start();
        separateTaskThread.join(millis);
        if (separateTaskThread.isAlive())
            throw new IllegalThreadStateException("The task " + task + " should have been executed by now.");
    }

    private Thread createSeparateTaskThread(final Task task) {
        return new Thread() {
            @Override
            public void run() {
                waitingTaskRunner.runTask(task);
            }
        };
    }
}
