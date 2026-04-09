package com.example.demo.controller;

import com.example.demo.entity.Project;
import com.example.demo.entity.Task;
import com.example.demo.entity.User;
import com.example.demo.entity.Workspace;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class SearchController {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    @GetMapping("/search")
    public String search(@RequestParam(value = "q", required = false) String query,
            Authentication authentication,
            Model model) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        String keyword = query == null ? "" : query.trim();
        SearchCriteria criteria = parseCriteria(keyword);

        List<Workspace> workspaces;
        List<Project> projects;
        List<Task> tasks;

        if (keyword.isEmpty()) {
            workspaces = Collections.emptyList();
            projects = Collections.emptyList();
            tasks = Collections.emptyList();
        } else {
            String plainKeyword = criteria.keyword().isBlank() ? null : criteria.keyword();
            workspaces = plainKeyword == null
                    ? Collections.emptyList()
                    : workspaceRepository.findByCreatedByAndNameContainingIgnoreCase(user, plainKeyword);
            projects = plainKeyword == null
                    ? Collections.emptyList()
                    : projectRepository.findByWorkspace_CreatedByAndNameContainingIgnoreCase(user, plainKeyword);
            tasks = taskRepository.searchByKeywordAndOwner(
                    user,
                    plainKeyword,
                    criteria.statusName(),
                    criteria.assigneeEmail(),
                    criteria.dueBefore(),
                    criteria.dueAfter());
        }

        model.addAttribute("username", username);
        model.addAttribute("query", keyword);
        model.addAttribute("workspaces", workspaces);
        model.addAttribute("projects", projects);
        model.addAttribute("tasks", tasks);
        model.addAttribute("totalCount", workspaces.size() + projects.size() + tasks.size());
        model.addAttribute("searchHint",
                "Có thể dùng: status:done assignee:mail@x.com due<:2026-05-01 due>:2026-04-01");

        return "search/results";
    }

    private SearchCriteria parseCriteria(String raw) {
        if (raw == null || raw.isBlank()) {
            return new SearchCriteria("", null, null, null, null);
        }

        StringBuilder keywordBuilder = new StringBuilder();
        String status = null;
        String assignee = null;
        LocalDate dueBefore = null;
        LocalDate dueAfter = null;

        String[] tokens = raw.split("\\s+");
        for (String token : tokens) {
            String lower = token.toLowerCase(Locale.ROOT);

            if (lower.startsWith("status:")) {
                status = token.substring("status:".length()).trim();
                continue;
            }
            if (lower.startsWith("assignee:")) {
                assignee = token.substring("assignee:".length()).trim();
                continue;
            }
            if (lower.startsWith("due<:")) {
                dueBefore = parseDate(token.substring("due<:".length()).trim());
                continue;
            }
            if (lower.startsWith("due>:")) {
                dueAfter = parseDate(token.substring("due>:".length()).trim());
                continue;
            }

            keywordBuilder.append(token).append(' ');
        }

        return new SearchCriteria(
                keywordBuilder.toString().trim(),
                blankToNull(status),
                blankToNull(assignee),
                dueBefore,
                dueAfter);
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private record SearchCriteria(
            String keyword,
            String statusName,
            String assigneeEmail,
            LocalDate dueBefore,
            LocalDate dueAfter) {
    }
}