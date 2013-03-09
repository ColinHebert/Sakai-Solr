package org.sakaiproject.search.solr.indexing;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.search.indexing.DefaultTask;
import org.sakaiproject.search.indexing.Task;
import org.sakaiproject.search.indexing.TaskHandler;
import org.sakaiproject.search.indexing.TaskMatcher;
import org.sakaiproject.search.indexing.exception.TaskHandlingException;
import org.sakaiproject.search.queueing.IndexQueueing;

import java.util.LinkedList;
import java.util.Queue;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.mockito.Mockito.*;

/**
 * Checks if the splitting system works as expected.
 *
 * @author Colin Hebert
 */
public class SolrSplitterProcessesTest {
    @Mock
    private TaskHandler mockTaskHandler;
    @Mock
    private IndexQueueing mockIndexQueueing;
    @Mock
    private SolrTools mockSolrTools;
    private Queue<String> indexableSites = new LinkedList<String>();
    private SolrSplitterProcesses solrSplitterProcesses;
    @Rule
    private ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        solrSplitterProcesses = new SolrSplitterProcesses();
        solrSplitterProcesses.setActualTaskHandler(mockTaskHandler);
        solrSplitterProcesses.setIndexQueueing(mockIndexQueueing);
        solrSplitterProcesses.setSolrTools(mockSolrTools);

        indexableSites.offer("test1");
        indexableSites.offer("test2");
        indexableSites.offer("test3");
    }

    /**
     * Attempts to execute a simple task.
     * <p>
     * The task should be relayed automatically to the {@link #mockTaskHandler}.
     * </p>
     */
    @Test
    public void testSimpleTask() {
        Task task = mock(Task.class);
        solrSplitterProcesses.executeTask(task);

        verify(mockTaskHandler).executeTask(task);
    }

    /**
     * Tests if exceptions caught while delegating simple tasks are wrapped in a {@link TaskHandlingException}.
     */
    @Test
    public void testRuntimeExceptionDuringSimpleTask() {
        Task task = mock(Task.class);
        RuntimeException toBeThrown = mock(RuntimeException.class);
        doThrow(toBeThrown).when(mockTaskHandler).executeTask(any(Task.class));

        thrown.expect(TaskHandlingException.class);
        thrown.expectCause(sameInstance(toBeThrown));

        solrSplitterProcesses.executeTask(task);
    }

    /**
     * Tests if {@link TaskHandlingException} caught while delegating simple tasks are passing through.
     */
    @Test
    public void testTaskHandlingExceptionDuringSimpleTask() {
        Task task = mock(Task.class);
        TaskHandlingException toBeThrown = mock(TaskHandlingException.class);
        doThrow(toBeThrown).when(mockTaskHandler).executeTask(any(Task.class));

        thrown.expect(TaskHandlingException.class);
        thrown.expect(sameInstance(toBeThrown));

        solrSplitterProcesses.executeTask(task);
    }

    /**
     * Checks if the required tasks were generated by the splitter for an {@link DefaultTask.Type#INDEX_ALL}.
     * <p>
     * The expected tasks are:
     * <ul>
     * <li>One task per site to reindex</li>
     * <li>One task to remove deprecated documents</li>
     * <li>One task to optimise the index</li>
     * </ul>
     * </p>
     */
    @Test
    public void testIndexAllTask() {
        Task task = mock(Task.class);
        when(task.getType()).thenReturn(DefaultTask.Type.INDEX_ALL.getTypeName());
        when(mockSolrTools.getIndexableSites()).thenReturn(indexableSites);
        int indexableSitesSize = indexableSites.size();
        int numberOfTasks = indexableSitesSize + 2;
        solrSplitterProcesses.executeTask(task);

        verify(mockIndexQueueing, times(numberOfTasks)).addTaskToQueue(any(Task.class));
        verify(mockIndexQueueing).addTaskToQueue(
                argThat(new TaskMatcher(SolrTask.Type.REMOVE_ALL_DOCUMENTS.getTypeName())));
        verify(mockIndexQueueing).addTaskToQueue(
                argThat(new TaskMatcher(SolrTask.Type.OPTIMISE_INDEX.getTypeName())));
        verify(mockIndexQueueing, times(indexableSitesSize)).addTaskToQueue(
                argThat(new TaskMatcher(DefaultTask.Type.INDEX_SITE.getTypeName())));
    }

    /**
     * Checks if the required tasks were generated by the splitter for an {@link DefaultTask.Type#REFRESH_ALL}.
     * <p>
     * The expected tasks are:
     * <ul>
     * <li>One task per site affected</li>
     * <li>One task to remove deprecated documents</li>
     * <li>One task to optimise the index</li>
     * </ul>
     * </p>
     */
    @Test
    public void testRefreshAllTask() {
        Task task = mock(Task.class);
        when(task.getType()).thenReturn(DefaultTask.Type.REFRESH_ALL.getTypeName());
        when(mockSolrTools.getIndexableSites()).thenReturn(indexableSites);
        int indexableSitesSize = indexableSites.size();
        int numberOfTasks = indexableSitesSize + 2;
        solrSplitterProcesses.executeTask(task);

        verify(mockIndexQueueing, times(numberOfTasks)).addTaskToQueue(any(Task.class));
        verify(mockIndexQueueing).addTaskToQueue(
                argThat(new TaskMatcher(SolrTask.Type.REMOVE_ALL_DOCUMENTS.getTypeName())));
        verify(mockIndexQueueing).addTaskToQueue(
                argThat(new TaskMatcher(SolrTask.Type.OPTIMISE_INDEX.getTypeName())));
        verify(mockIndexQueueing, times(indexableSitesSize)).addTaskToQueue(
                argThat(new TaskMatcher(DefaultTask.Type.REFRESH_SITE.getTypeName())));
    }
}