package com.example.demo.service;

import com.example.demo.entity.Notification;
import com.example.demo.entity.Project;
import com.example.demo.entity.Task;
import com.example.demo.entity.User;
import com.example.demo.entity.Workspace;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private MentionService mentionService;

    @Test
    void extractMentionsShouldReturnUniqueUsernames() {
        Set<String> usernames = mentionService.extractMentions("Hi @alice please sync with @bob and @alice");

        assertEquals(2, usernames.size());
        assertTrue(usernames.contains("alice"));
        assertTrue(usernames.contains("bob"));
    }

    @Test
    void notifyMentionsShouldCreateNotificationWithTaskScope() {
        User actor = new User();
        actor.setId(1L);
        actor.setUsername("actor");

        User alice = new User();
        alice.setId(2L);
        alice.setUsername("alice");

        Workspace workspace = new Workspace();
        workspace.setId(11L);

        Project project = new Project();
        project.setId(22L);
        project.setWorkspace(workspace);

        Task task = new Task();
        task.setId(33L);
        task.setTitle("Demo task");
        task.setProject(project);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        int created = mentionService.notifyMentions(task, actor, "ping @alice now");

        assertEquals(1, created);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification notification = captor.getValue();
        assertEquals("mention", notification.getType());
        assertEquals(22L, notification.getProjectId());
        assertEquals(33L, notification.getTaskId());
        assertEquals(alice, notification.getUser());
    }
}
