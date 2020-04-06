package com.bulletjournal.controller;

import com.bulletjournal.controller.models.*;
import com.bulletjournal.controller.utils.TestHelpers;
import com.bulletjournal.hierarchy.HierarchyProcessorProcessorTest;
import com.bulletjournal.ledger.FrequencyType;
import com.bulletjournal.ledger.LedgerSummary;
import com.bulletjournal.ledger.LedgerSummaryType;
import com.bulletjournal.notifications.Action;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Tests {@link ProjectController}
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ProjectControllerTest {
    private static final String ROOT_URL = "http://localhost:";
    private static String TIMEZONE = "America/Los_Angeles";
    private final String expectedOwner = "BulletJournal";

    private final String[] sampleUsers = {
            "Xavier",
            "bbs1024",
            "ccc",
            "Thinker",
            "Joker",
            "mqm",
            "hero",
            "bean",
            "xlf",
            "999999",
            "0518",
            "Scarlet",
            "lsx9981"};
    @LocalServerPort
    int randomServerPort;
    private TestRestTemplate restTemplate = new TestRestTemplate();

    @Before
    public void setup() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @Test
    public void testCRUD() throws Exception {
        String notificationsEtag = answerNotifications();
        String projectName = "P0";
        List<GroupsWithOwner> groups = createGroups(expectedOwner);
        Group group = groups.get(0).getGroups().get(0);
        int count = 6;
        for (String username : Arrays.asList(sampleUsers).subList(0, 3)) {
            group = addUserToGroup(group, username, ++count);
        }

        for (String username : Arrays.asList(sampleUsers).subList(0, 2)) {
            removeUserFromGroup(group, username, --count);
        }

        Project p1 = createProject(projectName, expectedOwner, group, ProjectType.TODO);
        p1 = updateProject(p1);

        // create other projects
        Project p2 = createProject("P2", expectedOwner, group, ProjectType.LEDGER);
        Project p3 = createProject("P3", expectedOwner, group, ProjectType.NOTE);
        Project p4 = createProject("P4", expectedOwner, group, ProjectType.TODO);
        Project p5 = createProject("P5", expectedOwner, group, ProjectType.NOTE);
        Project p6 = createProject("P6", expectedOwner, group, ProjectType.LEDGER);
        updateProjectRelations(p1, p2, p3, p4, p5, p6);
        deleteProject(p1);
        Project p7 = createProject("P7", expectedOwner, group, ProjectType.TODO);
        updateProjectRelations(p5, p6, p7);

        // test notification for adding and removing users
        Project p8 = createProject("P8", expectedOwner, group, ProjectType.TODO);
        p8 = updateProjectGroup(p8, groups.get(0).getGroups().get(0).getId());
        /**
         *  p5
         *   |
         *    -- p6
         *        |
         *        --p7
         *  p8
         */

        List<Label> labels = createLabels();
        createTasks(p7, p8, labels);
        createNotes(p5, labels);
        createTransactions(p6, labels);

        getNotifications(notificationsEtag);
    }

    private Project updateProjectGroup(Project p8, Long id) {
        UpdateProjectParams updateProjectParams = new UpdateProjectParams();
        updateProjectParams.setGroupId(id);
        ResponseEntity<Project> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.PROJECT_ROUTE,
                HttpMethod.PATCH,
                new HttpEntity<>(updateProjectParams),
                Project.class,
                p8.getId());
        p8 = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Default", p8.getGroup().getName());
        assertEquals(expectedOwner, p8.getOwner());
        assertEquals(98L, (long) p8.getGroup().getId());
        return p8;
    }

    private void findItemsByLabels(List<Label> labels, List<ProjectItems> expectedItems) {
        String url = ROOT_URL + randomServerPort + LabelController.ITEMS_ROUTE;
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("labels", labels.stream().map(l -> l.getId()).collect(Collectors.toList()));
        ResponseEntity<ProjectItems[]> response = this.restTemplate.exchange(
                uriBuilder.toUriString(),
                HttpMethod.GET,
                null,
                ProjectItems[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        List<ProjectItems> items = Arrays.asList(response.getBody());
        for (int i = 0; i < expectedItems.size(); i++) {
            assertEquals(expectedItems.get(i).getNotes(), items.get(i).getNotes());
            assertEquals(expectedItems.get(i).getTasks(), items.get(i).getTasks());
            assertEquals(expectedItems.get(i).getTransactions(), items.get(i).getTransactions());
        }
    }

    private List<Label> createLabels() {
        List<Label> labels = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            CreateLabelParams createLabelParams =
                    new CreateLabelParams("Label" + i, "Icon" + i);
            ResponseEntity<Label> response = this.restTemplate.exchange(
                    ROOT_URL + randomServerPort + LabelController.LABELS_ROUTE,
                    HttpMethod.POST,
                    new HttpEntity<>(createLabelParams),
                    Label.class);
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            labels.add(response.getBody());
        }

        ResponseEntity<Label[]> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + LabelController.LABELS_ROUTE,
                HttpMethod.GET,
                null,
                Label[].class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Label[] labelsCreated = response.getBody();
        assertEquals(7, labelsCreated.length);
        return labels;
    }

    private void createTransactions(Project p, List<Label> labels) {
        Transaction transaction1 = createTransaction(p, "transaction1", "2020-03-03");
        Transaction transaction2 = createTransaction(p, "transaction2", "2020-03-04");
        Transaction transaction3 = createTransaction(p, "transaction3", "2020-03-05");
        transaction1 = updateTransaction(transaction1);

        // Attach Labels to transactions
        ResponseEntity<Transaction> setLabelResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TransactionController.TRANSACTION_SET_LABELS_ROUTE,
                HttpMethod.PUT,
                new HttpEntity<>(labels.stream().map(l -> l.getId()).collect(Collectors.toList())),
                Transaction.class,
                transaction3.getId());
        assertEquals(HttpStatus.OK, setLabelResponse.getStatusCode());
        transaction3 = setLabelResponse.getBody();
        assertEquals(labels.size(), transaction3.getLabels().size());

        ProjectItems projectItems = new ProjectItems();
        projectItems.setTransactions(ImmutableList.of(transaction3));
        findItemsByLabels(labels, ImmutableList.of(projectItems));

        // Get transactions
        String url = UriComponentsBuilder.fromHttpUrl(
                ROOT_URL + randomServerPort + TransactionController.TRANSACTIONS_ROUTE)
                .queryParam("frequencyType", FrequencyType.MONTHLY.name())
                .queryParam("timezone", TIMEZONE)
                .queryParam("ledgerSummaryType", LedgerSummaryType.DEFAULT.name())
                .queryParam("startDate", "2020-01-01")
                .queryParam("endDate", "2020-11-01")
                .buildAndExpand(p.getId()).toUriString();
        ResponseEntity<LedgerSummary> transactionsResponse = this.restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                LedgerSummary.class);
        String etag1 = transactionsResponse.getHeaders().getETag();
        List<Transaction> transactions = transactionsResponse.getBody().getTransactions();

        transactionsResponse = this.restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                LedgerSummary.class);
        String etag2 = transactionsResponse.getHeaders().getETag();
        assertEquals(etag1, etag2);

        deleteTransaction(transaction2);
        transactionsResponse = this.restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                LedgerSummary.class);
        String etag3 = transactionsResponse.getHeaders().getETag();
        transactions = transactionsResponse.getBody().getTransactions();
        assertNotEquals(etag1, etag3);
        assertEquals(2, transactions.size());

        deleteTransactions(p, transaction1, transaction3);
    }

    private void createNotes(Project p, List<Label> labels) {
        Note note1 = createNote(p, "test111");
        Note note2 = createNote(p, "test2");
        Note note3 = createNote(p, "test3");
        updateNoteRelations(p, note1, note2, note3);
        updateNote(note1);

        // Attach Labels to notes
        ResponseEntity<Note> setLabelResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + NoteController.NOTE_SET_LABELS_ROUTE,
                HttpMethod.PUT,
                new HttpEntity<>(labels.stream().map(l -> l.getId()).collect(Collectors.toList())),
                Note.class,
                note2.getId());
        assertEquals(HttpStatus.OK, setLabelResponse.getStatusCode());
        note2 = setLabelResponse.getBody();
        assertEquals(labels.size(), note2.getLabels().size());

        ProjectItems projectItems = new ProjectItems();
        projectItems.setNotes(ImmutableList.of(note2));
        findItemsByLabels(labels, ImmutableList.of(projectItems));

        deleteNote(note1);
    }

    private void deleteTransactions(Project project, Transaction... transactions) {
        String url = UriComponentsBuilder.fromHttpUrl(
                ROOT_URL + randomServerPort + TransactionController.TRANSACTIONS_ROUTE)
                .queryParam("frequencyType", FrequencyType.MONTHLY.name())
                .queryParam("timezone", TIMEZONE)
                .queryParam("ledgerSummaryType", LedgerSummaryType.DEFAULT.name())
                .queryParam("startDate", "2020-01-01")
                .queryParam("endDate", "2020-11-01")
                .buildAndExpand(project.getId()).toUriString();
        ResponseEntity<LedgerSummary> getResponse = this.restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                LedgerSummary.class);
        List<Transaction> t = getResponse.getBody().getTransactions();
        int size = t.size();

        for (Transaction transaction : transactions) {
            t = deleteTransaction(transaction);
            assertEquals(--size, t.size());
        }
    }

    private List<Transaction> deleteTransaction(Transaction t) {

        ResponseEntity<Transaction> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TransactionController.TRANSACTION_ROUTE,
                HttpMethod.DELETE,
                null,
                Transaction.class,
                t.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String url = UriComponentsBuilder.fromHttpUrl(
                ROOT_URL + randomServerPort + TransactionController.TRANSACTIONS_ROUTE)
                .queryParam("frequencyType", FrequencyType.MONTHLY.name())
                .queryParam("timezone", TIMEZONE)
                .queryParam("ledgerSummaryType", LedgerSummaryType.DEFAULT.name())
                .queryParam("startDate", "2020-01-01")
                .queryParam("endDate", "2020-11-01")
                .buildAndExpand(t.getProjectId()).toUriString();
        ResponseEntity<LedgerSummary> getResponse = this.restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                LedgerSummary.class);
        List<Transaction> transactions = getResponse.getBody().getTransactions();
        assertNotNull(transactions);
        return transactions;
    }

    private Transaction updateTransaction(Transaction t) {
        String transactionName = "transaction4";
        UpdateTransactionParams update = new UpdateTransactionParams();
        update.setName(transactionName);
        ResponseEntity<Transaction> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TransactionController.TRANSACTION_ROUTE,
                HttpMethod.PATCH,
                new HttpEntity<>(update),
                Transaction.class,
                t.getId());
        t = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assert t != null;
        assertEquals(transactionName, t.getName());
        return t;
    }

    private Transaction createTransaction(Project project, String name, String date) {
        CreateTransactionParams transaction =
                new CreateTransactionParams(name, "BulletJournal", 1000.0,
                        date, null, TIMEZONE, 1);
        ResponseEntity<Transaction> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TransactionController.TRANSACTIONS_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(transaction),
                Transaction.class,
                project.getId());
        Transaction created = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(created);
        assertEquals(name, created.getName());
        assertEquals(project.getId(), created.getProjectId());
        return created;
    }

    private Note createNote(Project p, String noteName) {
        CreateNoteParams note = new CreateNoteParams(noteName);
        ResponseEntity<Note> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + NoteController.NOTES_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(note),
                Note.class,
                p.getId());
        Note created = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assert created != null;
        assertEquals(noteName, created.getName());
        assertEquals(p.getId(), created.getProjectId());
        return created;
    }

    private Note getNote(Note note) {
        ResponseEntity<Note> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + NoteController.NOTE_ROUTE,
                HttpMethod.GET,
                null,
                Note.class,
                note.getId());
        Note outputNote = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(note.getName(), outputNote.getName());
        return outputNote;
    }

    private void updateNote(Note n1) {
        // update project name from "P0" to "P1"
        String noteName = "test111";
        UpdateNoteParams updateNoteParams = new UpdateNoteParams();
        updateNoteParams.setName(noteName);
        ResponseEntity<Note[]> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + NoteController.NOTE_ROUTE,
                HttpMethod.PATCH,
                new HttpEntity<>(updateNoteParams),
                Note[].class,
                n1.getId());
        Note[] nList = response.getBody();
        Note changedNote = new Note();
        for (int i = 0; i < nList.length; i++) {
            if (nList[i].getName().equals(noteName)) {
                changedNote = nList[i];
            }
        }
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(noteName, changedNote.getName());
    }

    private void updateNoteRelations(Project project, Note note1, Note note2, Note note3) {
//        note1
//          |
//           --note2
//               |
//                --- note3
        note1.addSubNote(note2);
        note2.addSubNote(note3);
        ResponseEntity<Note[]> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + NoteController.NOTES_ROUTE,
                HttpMethod.PUT,
                new HttpEntity<>(ImmutableList.of(note1)),
                Note[].class,
                project.getId()
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Note[] notes = response.getBody();
        assertEquals(1, notes.length);
        assertEquals(note1, notes[0]);
        assertEquals(1, notes[0].getSubNotes().size());
        assertEquals(note2, notes[0].getSubNotes().get(0));
        assertEquals(1, notes[0].getSubNotes().get(0).getSubNotes().size());
        assertEquals(note3, notes[0].getSubNotes().get(0).getSubNotes().get(0));
    }

    private void deleteNote(Note note) {

        ResponseEntity<Note[]> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + NoteController.NOTE_ROUTE,
                HttpMethod.DELETE,
                null,
                Note[].class,
                note.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ResponseEntity<Note[]> getResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + NoteController.NOTES_ROUTE,
                HttpMethod.GET,
                null,
                Note[].class,
                note.getProjectId());
        Note[] notes = getResponse.getBody();
        assertEquals(0, notes.length);
    }

    private void createTasks(Project project, Project projectToMoveTo, List<Label> labels) {
        Task t1 = createTask(project, "t1");
        Task t2 = createTask(project, "t2");
        Task t3 = createTask(project, "t3");
        updateTaskRelations(project, t1, t2, t3);
        t1 = updateTask(t1, expectedOwner, "2020-02-27", null, null, null, t1.getName());

        // Attach Labels to tasks
        ResponseEntity<Task> setLabelResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASK_SET_LABELS_ROUTE,
                HttpMethod.PUT,
                new HttpEntity<>(labels.stream().map(l -> l.getId()).collect(Collectors.toList())),
                Task.class,
                t1.getId());
        assertEquals(HttpStatus.OK, setLabelResponse.getStatusCode());
        t1 = setLabelResponse.getBody();
        assertEquals(labels.size(), t1.getLabels().size());

        ProjectItems projectItems = new ProjectItems();
        projectItems.setTasks(ImmutableList.of(t1));
        findItemsByLabels(labels, ImmutableList.of(projectItems));

        // Get Tasks
        ResponseEntity<Task[]> tasksResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASKS_ROUTE,
                HttpMethod.GET,
                null,
                Task[].class,
                project.getId());
        String etag1 = tasksResponse.getHeaders().getETag();
        List<Task> tasks = Arrays.asList(tasksResponse.getBody());

        tasksResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASKS_ROUTE,
                HttpMethod.GET,
                null,
                Task[].class,
                project.getId());
        String etag2 = tasksResponse.getHeaders().getETag();
        List<Task> taskList = Arrays.asList(tasksResponse.getBody());
        assertEquals(tasks, taskList);
        assertEquals(etag1, etag2);
        moveTasks(project, projectToMoveTo, t1, t2, t3);

        // delete task
        deleteTask(t2);
        tasksResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASKS_ROUTE,
                HttpMethod.GET,
                null,
                Task[].class,
                project.getId());
        String etag3 = tasksResponse.getHeaders().getETag();
        tasks = Arrays.asList(tasksResponse.getBody());
        assertNotEquals(etag1, etag3);

        assertEquals(1, tasks.size());
        assertEquals(t1, tasks.get(0));
        assertEquals(0, tasks.get(0).getSubTasks().size());

        ResponseEntity<Task> completeTaskResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.COMPLETE_TASK_ROUTE,
                HttpMethod.POST,
                null,
                Task.class,
                t1.getId());

        assertEquals(HttpStatus.OK, completeTaskResponse.getStatusCode());
        assertNotNull(completeTaskResponse.getBody());
        Task completedTask = completeTaskResponse.getBody();
        assertEquals(t1.getName(), completedTask.getName());
        assertEquals(t1.getTimezone(), completedTask.getTimezone());
        assertEquals(t1.getAssignedTo(), completedTask.getAssignedTo());
        assertEquals(t1.getDueDate(), completedTask.getDueDate());
        assertEquals(t1.getDueTime(), completedTask.getDueTime());

        ResponseEntity<Task[]> completedTasksResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.COMPLETED_TASKS_ROUTE,
                HttpMethod.GET,
                null,
                Task[].class,
                project.getId());

        assertEquals(HttpStatus.OK, completedTasksResponse.getStatusCode());
        assertNotNull(completedTasksResponse.getBody());
        List<Task> completedTasks = Arrays.asList(completedTasksResponse.getBody());
        assertEquals(1, completedTasks.size());
        assertEquals(completedTask, completedTasks.get(0));

        // uncomplete task
        ResponseEntity<Task> uncompleteTaskResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.UNCOMPLETE_TASK_ROUTE,
                HttpMethod.POST,
                null,
                Task.class,
                completedTasks.get(0).getId());

        assertEquals(HttpStatus.OK, uncompleteTaskResponse.getStatusCode());
        assertNotNull(uncompleteTaskResponse.getBody());
        Task uncompletedTask = uncompleteTaskResponse.getBody();
        assertEquals(t1.getName(), uncompletedTask.getName());
        assertEquals(t1.getTimezone(), uncompletedTask.getTimezone());
        assertEquals(t1.getAssignedTo(), uncompletedTask.getAssignedTo());
        assertEquals(t1.getDueDate(), uncompletedTask.getDueDate());
        assertEquals(t1.getDueTime(), uncompletedTask.getDueTime());


        tasksResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASKS_ROUTE,
                HttpMethod.GET,
                null,
                Task[].class,
                project.getId());

        assertEquals(HttpStatus.OK, tasksResponse.getStatusCode());
        assertNotNull(tasksResponse.getBody());
        tasks = Arrays.asList(tasksResponse.getBody());
        assertFalse(tasks.contains(t1));
    }

    private void moveTasks(Project project, Project projectToMoveTo, Task t1, Task t2, Task t3) {
        ResponseEntity<Task[]> tasksResponse;
        List<Task> taskList;
        // move task t1
        ResponseEntity<?> moveResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.MOVE_TASK_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(new MoveProjectItemParams(projectToMoveTo.getId())),
                Void.class,
                t1.getId());
        assertEquals(HttpStatus.OK, moveResponse.getStatusCode());

        tasksResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASKS_ROUTE,
                HttpMethod.GET,
                null,
                Task[].class,
                project.getId());
        taskList = Arrays.asList(tasksResponse.getBody());
        assertEquals(0, taskList.size());

        tasksResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASKS_ROUTE,
                HttpMethod.GET,
                null,
                Task[].class,
                projectToMoveTo.getId());
        taskList = Arrays.asList(tasksResponse.getBody());
        assertEquals(1, taskList.size());
        assertEquals(1, taskList.get(0).getSubTasks().size());
        assertEquals(1, taskList.get(0).getSubTasks().get(0).getSubTasks().size());
        assertEquals(t1.getId(), taskList.get(0).getId());
        assertEquals(t2.getId(), taskList.get(0).getSubTasks().get(0).getId());
        assertEquals(t3.getId(), taskList.get(0).getSubTasks().get(0).getSubTasks().get(0).getId());

        // move task t1 back
        moveResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.MOVE_TASK_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(new MoveProjectItemParams(project.getId())),
                Void.class,
                t1.getId());
        assertEquals(HttpStatus.OK, moveResponse.getStatusCode());

        tasksResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASKS_ROUTE,
                HttpMethod.GET,
                null,
                Task[].class,
                projectToMoveTo.getId());
        taskList = Arrays.asList(tasksResponse.getBody());
        assertEquals(0, taskList.size());

        tasksResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASKS_ROUTE,
                HttpMethod.GET,
                null,
                Task[].class,
                project.getId());
        taskList = Arrays.asList(tasksResponse.getBody());
        assertEquals(1, taskList.size());
        assertEquals(1, taskList.get(0).getSubTasks().size());
        assertEquals(1, taskList.get(0).getSubTasks().get(0).getSubTasks().size());
        assertEquals(t1.getId(), taskList.get(0).getId());
        assertEquals(t2.getId(), taskList.get(0).getSubTasks().get(0).getId());
        assertEquals(t3.getId(), taskList.get(0).getSubTasks().get(0).getSubTasks().get(0).getId());
    }

    private void updateTaskRelations(Project project, Task task1, Task task2, Task task3) {
        //        task1
        //          |
        //           --task2
        //               |
        //                --- task3
        task1.addSubTask(task2);
        task2.addSubTask(task3);
        ResponseEntity<Task[]> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASKS_ROUTE,
                HttpMethod.PUT,
                new HttpEntity<>(ImmutableList.of(task1)),
                Task[].class,
                project.getId()
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Task[] tasks = response.getBody();
        assertEquals(1, tasks.length);
        assertEquals(task1, tasks[0]);
        assertEquals(1, tasks[0].getSubTasks().size());
        assertEquals(task2, tasks[0].getSubTasks().get(0));
        assertEquals(1, tasks[0].getSubTasks().get(0).getSubTasks().size());
        assertEquals(task3, tasks[0].getSubTasks().get(0).getSubTasks().get(0));
    }

    private Task createTask(Project project, String taskName) {
        CreateTaskParams task = new CreateTaskParams(taskName, expectedOwner, "2020-02-27",
                null, null, null, TIMEZONE, null);
        ResponseEntity<Task> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASKS_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(task),
                Task.class,
                project.getId());
        Task createdTask = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(taskName, createdTask.getName());
        assertEquals(project.getId(), createdTask.getProjectId());

        Content content = createTaskContent(createdTask);
        deleteTaskContent(createdTask, content);
        shareTask(createdTask);
        return createdTask;
    }

    private void shareTask(Task task) {
        String targetUser = sampleUsers[5];

        ShareProjectItemParams shareProjectItemParams = new ShareProjectItemParams(targetUser);
        ResponseEntity<String> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.SHARE_TASK_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(shareProjectItemParams),
                String.class,
                task.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ResponseEntity<Projects> getProjectsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.PROJECTS_ROUTE,
                HttpMethod.GET,
                TestHelpers.actAsOtherUser(null, targetUser),
                Projects.class);
        assertEquals(HttpStatus.OK, getProjectsResponse.getStatusCode());
        List<ProjectsWithOwner> sharedProjects = getProjectsResponse.getBody().getShared();
        assertEquals(2, sharedProjects.size());
        ProjectsWithOwner sharedProject = sharedProjects.get(0);
        assertEquals(targetUser, sharedProject.getOwner());
        assertEquals("https://1o24bbs.com/user_avatar/1o24bbs.com/mqm/75/1671_2.png",
                sharedProject.getOwnerAvatar());
        assertEquals(1, sharedProject.getProjects().size());
        Project p = sharedProject.getProjects().get(0);
        Group g = p.getGroup();
        assertEquals("Shared TODO", p.getName());
        assertEquals("Default", g.getName());
        assertEquals(ProjectType.TODO, p.getProjectType());
        assertEquals(targetUser, g.getOwner());

        // Get Tasks
        ResponseEntity<Task[]> tasksResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASKS_ROUTE,
                HttpMethod.GET,
                TestHelpers.actAsOtherUser(null, targetUser),
                Task[].class,
                p.getId());
        assertEquals(HttpStatus.OK, tasksResponse.getStatusCode());
        List<Task> tasks = Arrays.asList(tasksResponse.getBody());
        Task sharedTask = tasks.get(0);
        assertEquals(task, sharedTask);

        shareProjectItemParams = new ShareProjectItemParams();
        shareProjectItemParams.setGenerateLink(true);
        response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.SHARE_TASK_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(shareProjectItemParams),
                String.class,
                task.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(8, response.getBody().length());

        ResponseEntity<?> publicProjectItemResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + SystemController.PUBLIC_ITEM_ROUTE,
                HttpMethod.GET,
                TestHelpers.actAsOtherUser(null, sampleUsers[7]),
                Object.class,
                response.getBody());
        assertEquals(HttpStatus.OK, publicProjectItemResponse.getStatusCode());

        shareProjectItemParams = new ShareProjectItemParams();
        shareProjectItemParams.setGenerateLink(true);
        shareProjectItemParams.setTtl(20L);
        response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.SHARE_TASK_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(shareProjectItemParams),
                String.class,
                task.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(8, response.getBody().length());

        publicProjectItemResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + SystemController.PUBLIC_ITEM_ROUTE,
                HttpMethod.GET,
                TestHelpers.actAsOtherUser(null, sampleUsers[7]),
                Object.class,
                response.getBody());
        assertEquals(HttpStatus.OK, publicProjectItemResponse.getStatusCode());

        shareProjectItemParams = new ShareProjectItemParams();
        shareProjectItemParams.setGenerateLink(true);
        shareProjectItemParams.setTtl(-2L);
        response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.SHARE_TASK_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(shareProjectItemParams),
                String.class,
                task.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(8, response.getBody().length());

        publicProjectItemResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + SystemController.PUBLIC_ITEM_ROUTE,
                HttpMethod.GET,
                TestHelpers.actAsOtherUser(null, sampleUsers[7]),
                Object.class,
                response.getBody());
        assertEquals(HttpStatus.OK, publicProjectItemResponse.getStatusCode());
        assertNull(publicProjectItemResponse.getBody());
    }

    private void deleteTaskContent(Task task, Content content) {
        ResponseEntity<?> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.CONTENT_ROUTE,
                HttpMethod.DELETE,
                null,
                Void.class,
                task.getId(),
                content.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        getTaskContents(task, ImmutableList.of(), null);
    }

    private Content createTaskContent(Task task) {
        String text = "TEXT1";
        CreateContentParams createContentParams = new CreateContentParams(text);
        ResponseEntity<Content> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.ADD_CONTENT_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(createContentParams),
                Content.class,
                task.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Content content = response.getBody();
        assertEquals(expectedOwner, content.getOwner());
        assertEquals(text, content.getText());
        assertNotNull(content.getId());
        getTaskContents(task, ImmutableList.of(content), text);

        text = "TEXT2";
        UpdateContentParams updateContentParams = new UpdateContentParams(text);
        ResponseEntity<Content> updateResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.CONTENT_ROUTE,
                HttpMethod.PATCH,
                new HttpEntity<>(updateContentParams),
                Content.class,
                task.getId(),
                content.getId());
        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
        content = updateResponse.getBody();
        assertEquals(expectedOwner, content.getOwner());
        assertEquals(text, content.getText());
        assertNotNull(content.getId());
        getTaskContents(task, ImmutableList.of(content), text);

        return content;
    }

    private void getTaskContents(Task task, List<Content> expectedContents, String text) {
        ResponseEntity<Content[]> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.CONTENTS_ROUTE,
                HttpMethod.GET,
                null,
                Content[].class,
                task.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Content[] contents = response.getBody();
        assertEquals(expectedContents.size(), contents.length);
        for (int i = 0; i < contents.length; i++) {
            assertEquals(expectedOwner, contents[i].getOwner());
            assertEquals(text, contents[i].getText());
            assertNotNull(contents[i].getId());
        }
    }

    private Task updateTask(Task task, String assignedTo, String dueDate,
                            String dueTime, String name, ReminderSetting reminderSetting, String expectedName) {
        //update task parameter
        UpdateTaskParams updateTaskParams = new UpdateTaskParams(
                assignedTo, dueDate, dueTime, name, null, reminderSetting, TIMEZONE, null);
        ResponseEntity<Task[]> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASK_ROUTE,
                HttpMethod.PATCH,
                new HttpEntity<>(updateTaskParams),
                Task[].class,
                task.getId());
        Task updated = Arrays.asList(response.getBody()).stream()
                .filter(t -> task.getId().equals(t.getId())).findFirst().orElse(null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedOwner, updated.getAssignedTo());
        assertEquals(dueDate, updated.getDueDate());
        assertEquals(dueTime, updated.getDueTime());
        assertEquals(expectedName, updated.getName());
        return updated;
    }

    private void deleteTask(Task task) {
        ResponseEntity<Task[]> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + TaskController.TASK_ROUTE, // this is TASK bc one task?
                HttpMethod.DELETE,
                null,
                Task[].class,
                task.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private void deleteProject(Project p) {
        /**  After deletion
         *
         *  p5
         *   |
         *    -- p6
         */

        ResponseEntity<?> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.PROJECT_ROUTE,
                HttpMethod.DELETE,
                null,
                Void.class,
                p.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ResponseEntity<Projects> getResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.PROJECTS_ROUTE,
                HttpMethod.GET,
                null,
                Projects.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        List<Project> projects = getResponse.getBody().getOwned();
        assertEquals(1, projects.size());
        assertEquals("P5", projects.get(0).getName());
        assertEquals(1, projects.get(0).getSubProjects().size());
        assertEquals("P6", projects.get(0).getSubProjects().get(0).getName());
    }

    private String answerNotifications() {
        ResponseEntity<Notification[]> notificationsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + NotificationController.NOTIFICATIONS_ROUTE,
                HttpMethod.GET,
                null,
                Notification[].class);
        String etag = notificationsResponse.getHeaders().getETag();
        validateNotificationResponseEtagMatch(etag);
        assertEquals(HttpStatus.OK, notificationsResponse.getStatusCode());
        List<Notification> notifications = Arrays.asList(notificationsResponse.getBody());
        assertTrue(notifications.size() >= 9);
        // reject invitations to join group
        for (int i = 1; i < notifications.size() - 1; i++) {
            Notification notification = notifications.get(i);
            AnswerNotificationParams answerNotificationParams =
                    new AnswerNotificationParams(Action.DECLINE.getDescription());
            ResponseEntity<?> response = this.restTemplate.exchange(
                    ROOT_URL + randomServerPort + NotificationController.ANSWER_NOTIFICATION_ROUTE,
                    HttpMethod.POST,
                    new HttpEntity<>(answerNotificationParams),
                    Void.class,
                    notification.getId());
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
        return etag;
    }

    private void validateNotificationResponseEtagMatch(String expectedEtag) {
        ResponseEntity<Notification[]> notificationsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + NotificationController.NOTIFICATIONS_ROUTE,
                HttpMethod.GET,
                null,
                Notification[].class);

        assertEquals(HttpStatus.OK, notificationsResponse.getStatusCode());

        String etag = notificationsResponse.getHeaders().getETag();
        assertEquals(expectedEtag, etag);
    }

    private void updateProjectRelations(Project p5, Project p6, Project p7) {
        /**
         *  p5
         *   |
         *    -- p6
         *        |
         *        --p7
         */
        p6.addSubProject(p7);
        // Set user's project relations
        ResponseEntity<Projects> updateProjectRelationsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.PROJECTS_ROUTE,
                HttpMethod.PUT,
                new HttpEntity<>(ImmutableList.of(p5)),
                Projects.class
        );
        assertEquals(HttpStatus.OK, updateProjectRelationsResponse.getStatusCode());
        List<Project> projects = updateProjectRelationsResponse.getBody().getOwned();
        assertEquals(1, projects.size());
        assertEquals(p5, projects.get(0));
        assertEquals(1, projects.get(0).getSubProjects().size());
        assertEquals(p6, projects.get(0).getSubProjects().get(0));
        assertEquals(1, projects.get(0).getSubProjects().get(0).getSubProjects().size());
        assertEquals(p7, projects.get(0).getSubProjects().get(0).getSubProjects().get(0));
    }

    private void updateProjectRelations(Project p1, Project p2, Project p3, Project p4, Project p5, Project p6) {
        ResponseEntity<Projects> getProjectsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.PROJECTS_ROUTE,
                HttpMethod.GET,
                null,
                Projects.class);
        String[] eTags = getProjectsResponse.getHeaders().getETag().split("\\|");
        assertEquals(2, eTags.length);
        String ownedProjectsEtag = eTags[0];
        String sharedProjectsEtag = eTags[1];
        validateProjectResponseEtagMatch(ownedProjectsEtag, sharedProjectsEtag);

        /**
         *  p1
         *   |
         *    -- p2
         *   |   |
         *   |    -- p3
         *   |
         *    -- p4
         *
         *  p5
         *   |
         *    -- p6
         */
        List<Project> projectRelations = HierarchyProcessorProcessorTest.createSampleProjectRelations(
                p1, p2, p3, p4, p5, p6);
        // Set user's project relations
        ResponseEntity<Projects> updateProjectRelationsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.PROJECTS_ROUTE,
                HttpMethod.PUT,
                new HttpEntity<>(projectRelations),
                Projects.class
        );
        assertEquals(HttpStatus.OK, updateProjectRelationsResponse.getStatusCode());
        List<Project> projects = updateProjectRelationsResponse.getBody().getOwned();
        assertEquals(2, projects.size());
        assertEquals(p1, projects.get(0));
        assertEquals(p5, projects.get(1));
        assertEquals(2, projects.get(0).getSubProjects().size());
        assertEquals(p2, projects.get(0).getSubProjects().get(0));
        assertEquals(p4, projects.get(0).getSubProjects().get(1));
        assertEquals(1, projects.get(1).getSubProjects().size());
        assertEquals(p6, projects.get(1).getSubProjects().get(0));
        assertEquals(1, projects.get(0).getSubProjects().get(0).getSubProjects().size());
        assertEquals(p3, projects.get(0).getSubProjects().get(0).getSubProjects().get(0));

        List<ProjectsWithOwner> l = updateProjectRelationsResponse.getBody().getShared();
        assertEquals(2, l.size());
        projects = l.get(0).getProjects();
        assertEquals("Scarlet", l.get(0).getOwner());
        assertEquals(2, projects.size());
        assertEquals("P1", projects.get(0).getName());
        assertEquals("P5", projects.get(1).getName());
        assertEquals(2, projects.get(0).getSubProjects().size());
        assertEquals("P2", projects.get(0).getSubProjects().get(0).getName());
        assertEquals("P4", projects.get(0).getSubProjects().get(1).getName());
        assertEquals(1, projects.get(1).getSubProjects().size());
        assertEquals("P6", projects.get(1).getSubProjects().get(0).getName());
        assertEquals(1, projects.get(0).getSubProjects().get(0).getSubProjects().size());
        assertEquals("P3", projects.get(0).getSubProjects().get(0).getSubProjects().get(0).getName());

        projects = l.get(1).getProjects();
        assertEquals("lsx9981", l.get(1).getOwner());
        assertEquals(1, projects.size());
        assertEquals("P1", projects.get(0).getName());

        // change order of shared projects
        UpdateSharedProjectsOrderParams updateSharedProjectsOrderParams =
                new UpdateSharedProjectsOrderParams(new String[]{"lsx9981", "Scarlet"});
        ResponseEntity<?> updateSharedProjectsOrderResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.UPDATE_SHARED_PROJECTS_ORDER_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(updateSharedProjectsOrderParams),
                Void.class);
        assertEquals(HttpStatus.OK, updateSharedProjectsOrderResponse.getStatusCode());
        ResponseEntity<Projects> projectsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.PROJECTS_ROUTE,
                HttpMethod.GET,
                null,
                Projects.class);
        assertEquals(HttpStatus.OK, projectsResponse.getStatusCode());
        l = projectsResponse.getBody().getShared();
        assertEquals(2, l.size());
        projects = l.get(0).getProjects();
        assertEquals("lsx9981", l.get(0).getOwner());
        assertEquals(1, projects.size());

        projects = l.get(1).getProjects();
        assertEquals("Scarlet", l.get(1).getOwner());
        assertEquals(2, projects.size());


        validateProjectResponseEtagNotMatch(ownedProjectsEtag, sharedProjectsEtag);
    }

    private void validateProjectResponseEtagMatch(String ownedProjectsEtag, String sharedProjectsEtag) {
        ResponseEntity<Projects> projectsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.PROJECTS_ROUTE,
                HttpMethod.GET,
                null,
                Projects.class);

        assertEquals(HttpStatus.OK, projectsResponse.getStatusCode());

        String[] eTags = projectsResponse.getHeaders().getETag().split("\\|");
        assertEquals(2, eTags.length);
        assertEquals(ownedProjectsEtag, eTags[0]);
        assertEquals(sharedProjectsEtag, eTags[1]);
    }

    private void validateProjectResponseEtagNotMatch(String ownedProjectsEtag, String sharedProjectsEtag) {
        ResponseEntity<Projects> projectsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.PROJECTS_ROUTE,
                HttpMethod.GET,
                null,
                Projects.class);
        assertEquals(HttpStatus.OK, projectsResponse.getStatusCode());
        String[] eTags = projectsResponse.getHeaders().getETag().split("\\|");
        assertEquals(2, eTags.length);
        assertNotEquals(ownedProjectsEtag, eTags[0]);
        assertNotEquals(sharedProjectsEtag, eTags[1]);
    }

    private Project updateProject(Project p1) {
        // update project name from "P0" to "P1"
        String projectNewName = "P1";
        UpdateProjectParams updateProjectParams = new UpdateProjectParams();
        updateProjectParams.setName(projectNewName);
        updateProjectParams.setDescription("d2");

        ResponseEntity<Project> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.PROJECT_ROUTE,
                HttpMethod.PATCH,
                new HttpEntity<>(updateProjectParams),
                Project.class,
                p1.getId());
        p1 = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(projectNewName, p1.getName());
        assertEquals(expectedOwner, p1.getOwner());
        assertEquals(ProjectType.TODO, p1.getProjectType());
        assertEquals(expectedOwner, p1.getGroup().getOwner());
        return p1;
    }

    private void getNotifications(String notificationsEtag) {
        ResponseEntity<Notification[]> notificationsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + NotificationController.NOTIFICATIONS_ROUTE,
                HttpMethod.GET,
                null,
                Notification[].class);
        assertEquals(HttpStatus.OK, notificationsResponse.getStatusCode());
        assertNotEquals(notificationsEtag, notificationsResponse.getHeaders().getETag());

        List<Notification> notifications = Arrays.asList(notificationsResponse.getBody());
        assertEquals(2, notifications.size());
    }

    private List<GroupsWithOwner> getGroups() {
        ResponseEntity<GroupsWithOwner[]> groupsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + GroupController.GROUPS_ROUTE,
                HttpMethod.GET,
                null,
                GroupsWithOwner[].class);
        String etag = groupsResponse.getHeaders().getETag();
        List<GroupsWithOwner> groupsBody = Arrays.asList(groupsResponse.getBody());
        validateGroupsResponseEtagMatch(etag);
        return groupsBody;
    }

    private void validateGroupsResponseEtagMatch(String etag) {
        ResponseEntity<GroupsWithOwner[]> groupsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + GroupController.GROUPS_ROUTE,
                HttpMethod.GET,
                null,
                GroupsWithOwner[].class);
        assertEquals(HttpStatus.OK, groupsResponse.getStatusCode());
        assertEquals(etag, groupsResponse.getHeaders().getETag());
    }

    private List<GroupsWithOwner> createGroups(String owner) {
        List<GroupsWithOwner> groups = getGroups();
        assertEquals(4, groups.size());
        Group g = groups.get(0).getGroups().get(0);
        assertEquals(expectedOwner, g.getOwner());
        assertEquals(6, g.getUsers().size());
        Group secondOwnedGroup = groups.get(0).getGroups().get(1);
        assertEquals(expectedOwner, secondOwnedGroup.getOwner());
        assertEquals(1, secondOwnedGroup.getUsers().size());
        Group invitedToJoin = groups.get(2).getGroups().get(0);
        assertTrue(invitedToJoin.getUsers().size() >= 2);
        Group joinedGroup = groups.get(1).getGroups().get(0);
        assertEquals(2, joinedGroup.getUsers().size());
        assertEquals("Scarlet", joinedGroup.getOwner());
        assertEquals("Scarlet", joinedGroup.getUsers().get(0).getName());
        assertEquals(true, joinedGroup.getUsers().get(0).isAccepted());
        assertEquals(expectedOwner, joinedGroup.getUsers().get(1).getName());
        assertEquals(true, joinedGroup.getUsers().get(1).isAccepted());
        Group joinedGroup2 = groups.get(3).getGroups().get(0);
        Group g1 = createGroup("G0", owner);
        Group g2 = createGroup("G2", owner);
        Group g3 = createGroup("G3", owner);

        String groupNewName = "G1";
        UpdateGroupParams updateGroupParams = new UpdateGroupParams();
        updateGroupParams.setName(groupNewName);

        // Update group name from "G0" to "G1"
        ResponseEntity<Group> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + GroupController.GROUP_ROUTE,
                HttpMethod.PATCH,
                new HttpEntity<>(updateGroupParams),
                Group.class,
                g1.getId());
        g1 = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(groupNewName, g1.getName());

        // Delete Group "G3"
        ResponseEntity<?> deleteResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + GroupController.GROUP_ROUTE,
                HttpMethod.DELETE,
                null,
                Void.class,
                g3.getId());
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());

        // Delete Group "Default"
        deleteResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + GroupController.GROUP_ROUTE,
                HttpMethod.DELETE,
                null,
                Void.class,
                g.getId());
        assertEquals(HttpStatus.UNAUTHORIZED, deleteResponse.getStatusCode());
        return groups;
    }

    private List<GroupsWithOwner> addUsersToGroup(final Group group, List<String> usernames) {
        AddUserGroupsParams addUserGroupsParams = new AddUserGroupsParams();
        for (String username : usernames) {
            addUserGroupsParams.getUserGroups().add(new AddUserGroupParams(group.getId(), username));
        }
        ResponseEntity<GroupsWithOwner[]> groupsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + GroupController.ADD_USER_GROUPS_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(addUserGroupsParams),
                GroupsWithOwner[].class);
        List<GroupsWithOwner> groups = Arrays.asList(groupsResponse.getBody());
        Group updated = groups.stream().filter(g -> group.getOwner().equals(g.getOwner()))
                .findFirst().get().getGroups()
                .stream().filter(g -> group.getName().equals(g.getName())).findFirst().get();
        assertEquals(usernames.size() + 1, updated.getUsers().size());
        return groups;
    }

    private Group addUserToGroup(Group group, String username, int expectedSize) {
        AddUserGroupParams addUserGroupParams = new AddUserGroupParams(group.getId(), username);
        ResponseEntity<Group> groupsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + GroupController.ADD_USER_GROUP_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(addUserGroupParams),
                Group.class);
        Group updated = groupsResponse.getBody();
        assertEquals(expectedSize, updated.getUsers().size());
        return updated;
    }

    private void removeUsersFromGroup(final Group group, List<String> usernames, int count) {
        RemoveUserGroupsParams removeUserGroupsParams = new RemoveUserGroupsParams();
        for (String username : usernames) {
            removeUserGroupsParams.getUserGroups().add(new RemoveUserGroupParams(group.getId(), username));
        }

        this.restTemplate.exchange(
                ROOT_URL + randomServerPort + GroupController.REMOVE_USER_GROUPS_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(removeUserGroupsParams),
                Void.class);

        ResponseEntity<GroupsWithOwner[]> groupsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + GroupController.GROUPS_ROUTE,
                HttpMethod.GET,
                null,
                GroupsWithOwner[].class);
        List<GroupsWithOwner> groupsWithOwners = Arrays.asList(groupsResponse.getBody());
        Group resultGroup = groupsWithOwners.stream().filter(g -> group.getOwner().equals(g.getOwner()))
                .findFirst().get().getGroups()
                .stream().filter(g -> group.getId().equals(g.getId())).findFirst().get();
        assertEquals(count, resultGroup.getUsers().size());
    }

    private void removeUserFromGroup(Group group, String username, int count) {
        RemoveUserGroupParams removeUserGroupParams = new RemoveUserGroupParams(group.getId(), username);
        this.restTemplate.exchange(
                ROOT_URL + randomServerPort + GroupController.REMOVE_USER_GROUP_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(removeUserGroupParams),
                Void.class);

        ResponseEntity<GroupsWithOwner[]> groupsResponse = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + GroupController.GROUPS_ROUTE,
                HttpMethod.GET,
                null,
                GroupsWithOwner[].class);

        List<GroupsWithOwner> groupsWithOwners = Arrays.asList(groupsResponse.getBody());
        Group resultGroup = groupsWithOwners.stream().filter(g -> group.getOwner().equals(g.getOwner()))
                .findFirst().get().getGroups()
                .stream().filter(g -> group.getId().equals(g.getId())).findFirst().get();
        assertEquals(count, resultGroup.getUsers().size());
    }

    private Group createGroup(String groupName, String expectedOwner) {
        CreateGroupParams group = new CreateGroupParams(groupName);
        ResponseEntity<Group> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + GroupController.GROUPS_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(group),
                Group.class);
        Group created = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(groupName, created.getName());
        assertEquals(expectedOwner, created.getOwner());

        return created;
    }

    private Project createProject(String projectName, String expectedOwner, Group g, ProjectType projectType) {
        CreateProjectParams project = new CreateProjectParams(
                projectName, projectType, "d1", g.getId());
        ResponseEntity<Project> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + ProjectController.PROJECTS_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(project),
                Project.class);
        Project created = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(projectName, created.getName());
        assertEquals(expectedOwner, created.getOwner());
        assertEquals(projectType, created.getProjectType());
        assertEquals(expectedOwner, created.getGroup().getOwner());
        return created;
    }
}

