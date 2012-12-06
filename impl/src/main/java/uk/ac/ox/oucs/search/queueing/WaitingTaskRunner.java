package uk.ac.ox.oucs.search.queueing;

import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ox.oucs.search.indexing.Task;
import uk.ac.ox.oucs.search.indexing.TaskHandler;
import uk.ac.ox.oucs.search.indexing.exception.NestedTaskHandlingException;
import uk.ac.ox.oucs.search.indexing.exception.TaskHandlingException;
import uk.ac.ox.oucs.search.indexing.exception.TemporaryTaskHandlingException;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Task runner putting Tasks on hold if a TemporaryTaskHandlingException has been caught.
 * <p>
 * Assuming that every {@link Task} should be successfully executed or completely fail, a {@link TemporaryTaskHandlingException}
 * means that the {@link TaskHandler} can't process new tasks.
 * </p>
 * <p>
 * This TaskRunner will put every thread that attempt to run a task on hold each time a TemporaryTaskHandlingException is caught.<br />
 * The waiting time is doubled each time a Task fails with a TemporaryTaskHandlingException until it reaches the {@link #maximumWaitingTime}.<br />
 * The waiting time is reset each time a task is successfully executed.
 * </p>
 *
 * @author Colin Hebert
 */
public abstract class WaitingTaskRunner implements TaskRunner {
    public static final int BASE_WAITING_TIME = 1000;
    private static final Logger logger = LoggerFactory.getLogger(WaitingTaskRunner.class);
    private final ReentrantLock taskRunnerLock = new ReentrantLock();
    /**
     * Maximum wait
     * Set to 5 minutes by default
     */
    private int maximumWaitingTime = 5 * 60 * BASE_WAITING_TIME;
    private int waitingTime = BASE_WAITING_TIME;
    private TaskHandler taskHandler;
    private SecurityService securityService;
    private IndexQueueing indexQueueing;

    public void runTask(Task task) {
        try {
            //Stop for a while because some tasks failed and should be run again.
            synchronized (taskRunnerLock) {
                while (taskRunnerLock.isLocked()){
                    logger.debug("Indexing thread locked due to a temporary failure of the system.");
                    taskRunnerLock.wait();
                    logger.debug("Indexing thread unlocked, ready to process new taks.");
                }
            }

            //Unlock permissions so every resource is accessible
            unlockPermissions();

            try {
                taskHandler.executeTask(task);
                //The task was successful, reset the waiting time
                waitingTime = BASE_WAITING_TIME;
            } catch (NestedTaskHandlingException e) {
                logger.warn("Some exceptions happened during the execution of '" + task + "'.", e);
                unfoldNestedTaskException(e);
            } catch (TemporaryTaskHandlingException e) {
                taskRunnerLock.tryLock();
                logger.warn("The task '" + task + "' couldn't be executed, try again later.", e);
                indexQueueing.addTaskToQueue(task);
            } catch (Exception e) {
                logger.error("Couldn't execute task '" + task + "'.", e);
            }

            // A TemporaryTaskException occurred, stop everything for a while (so the search server can recover)
            if (taskRunnerLock.isHeldByCurrentThread()) {
                logger.warn("A temporary exception has been caught, put the indexing system to sleep for " + waitingTime + "ms.");
                Thread.sleep(waitingTime);
                //Multiply the waiting time by two
                if (waitingTime <= maximumWaitingTime)
                    waitingTime <<= 1;
            }
        } catch (InterruptedException e) {
            logger.error("Thread interrupted while trying to do '" + task + "'.", e);
            indexQueueing.addTaskToQueue(task);
        } finally {
            //Clean up the localThread after each task
            ThreadLocalManager threadLocalManager = (ThreadLocalManager) ComponentManager.get(ThreadLocalManager.class);
            threadLocalManager.clear();

            // A TemporaryTaskException occurred and the waiting time is now passed (or an exception killed it)
            // unlock everything and get back to work
            if (taskRunnerLock.isHeldByCurrentThread()) {
                logger.debug("Wait finished, restart all the indexing threads.");
                synchronized (taskRunnerLock) {
                    taskRunnerLock.notifyAll();
                    taskRunnerLock.unlock();
                }
            }
        }
    }

    private void unfoldNestedTaskException(NestedTaskHandlingException e) {
        for (TaskHandlingException t : e.getTaskHandlingExceptions()) {
            if (t instanceof TemporaryTaskHandlingException) {
                taskRunnerLock.tryLock();
                TemporaryTaskHandlingException tthe = (TemporaryTaskHandlingException) t;
                logger.warn("A task failed '" + tthe.getNewTask() + "' will be tried again later.", t);
                indexQueueing.addTaskToQueue(tthe.getNewTask());
            } else {
                logger.error("An exception occured during the task execution.", t);
            }
        }
    }

    private void unlockPermissions() {
        securityService.pushAdvisor(new SecurityAdvisor() {
            @Override
            public SecurityAdvice isAllowed(String userId, String function, String reference) {
                return SecurityAdvice.ALLOWED;
            }
        });
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void setTaskHandler(TaskHandler taskHandler) {
        this.taskHandler = taskHandler;
    }

    public void setIndexQueueing(IndexQueueing indexQueueing) {
        this.indexQueueing = indexQueueing;
    }
}
