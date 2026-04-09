package com.example.demo.controller;

import com.example.demo.repository.WorkspaceMemberRepository;

import com.example.demo.entity.Workspace;
import com.example.demo.entity.WorkspaceMember;
import com.example.demo.entity.WorkspaceRole;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceRepository;
import com.example.demo.service.WorkspacePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/workspaces")
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspacePermissionService workspacePermissionService;

    @GetMapping
    public String listWorkspaces(Authentication authentication, Model model) {

        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();

        List<Workspace> workspaces = workspaceRepository.findDistinctByMembersUserId(user.getId())
                .stream()
                .sorted(Comparator.comparing(Workspace::getId).reversed())
                .toList();

        model.addAttribute("username", authentication.getName());
        model.addAttribute("workspaces", workspaces);

        return "workspace/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("workspace", new Workspace());
        return "workspace/create";
    }

    @PostMapping("/create")
    public String createWorkspace(@ModelAttribute Workspace workspace,
            Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        workspace.setCreatedBy(user);
        workspace.setOwner(user);

        workspaceRepository.save(workspace); // lưu trước để có id

        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspace(workspace);
        member.setUser(user);
        member.setRole(WorkspaceRole.OWNER.name());

        workspaceMemberRepository.save(member);

        workspaceRepository.save(workspace);

        return "redirect:/workspaces";
    }

    @PostMapping("/{workspaceId}/add-member")
    public String addMember(@PathVariable Long workspaceId,
            @RequestParam String email,
            Authentication authentication) {

        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (!workspacePermissionService.canManageMembers(workspaceId, currentUser)) {
            return "redirect:/workspaces/" + workspaceId + "/projects?memberError=forbidden";
        }

        Workspace workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow();

        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            return "redirect:/workspaces/" + workspaceId + "/projects?memberError=empty";
        }

        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElse(null);

        if (user == null) {
            return "redirect:/workspaces/" + workspaceId + "/projects?memberError=notfound";
        }

        boolean exists = workspaceMemberRepository
                .existsByWorkspaceIdAndUserId(workspaceId, user.getId());

        if (exists) {
            return "redirect:/workspaces/" + workspaceId + "/projects?memberError=exists";
        }

        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspace(workspace);
        member.setUser(user);
        member.setRole(WorkspaceRole.MEMBER.name());

        workspaceMemberRepository.save(member);

        return "redirect:/workspaces/" + workspaceId + "/projects?memberAdded=1";
    }

    @PostMapping("/{workspaceId}/members/{memberId}/role")
    public String updateMemberRole(@PathVariable Long workspaceId,
            @PathVariable Long memberId,
            @RequestParam String role,
            Authentication authentication) {

        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (!workspacePermissionService.canManageMembers(workspaceId, currentUser)) {
            return "redirect:/workspaces/" + workspaceId + "/projects?memberError=forbidden";
        }

        WorkspaceMember member = workspaceMemberRepository.findById(memberId).orElseThrow();
        if (member.getWorkspace() == null || !member.getWorkspace().getId().equals(workspaceId)) {
            return "redirect:/workspaces/" + workspaceId + "/projects?memberError=forbidden";
        }

        WorkspaceRole nextRole = WorkspaceRole.from(role);
        if (nextRole == WorkspaceRole.OWNER) {
            nextRole = WorkspaceRole.ADMIN;
        }

        member.setRole(nextRole.name());
        workspaceMemberRepository.save(member);

        return "redirect:/workspaces/" + workspaceId + "/projects?memberRoleUpdated=1";
    }
}
