package com.example.demo.config;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Locale;

@ControllerAdvice
@RequiredArgsConstructor
public class CurrentUserModelAdvice {

    private final UserRepository userRepository;

    @ModelAttribute("currentUser")
    public User currentUser() {
        return lookupCurrentUser();
    }

    @ModelAttribute("currentUserHasAvatar")
    public boolean currentUserHasAvatar() {
        User currentUser = lookupCurrentUser();
        return currentUser != null
                && currentUser.getAvatarFilename() != null
                && !currentUser.getAvatarFilename().isBlank();
    }

    @ModelAttribute("currentUserAvatarUrl")
    public String currentUserAvatarUrl() {
        User currentUser = lookupCurrentUser();
        if (currentUser == null || currentUser.getAvatarFilename() == null
                || currentUser.getAvatarFilename().isBlank()) {
            return "";
        }
        return "/me/avatar?v=" + currentUser.getAvatarFilename();
    }

    @ModelAttribute("currentUserInitials")
    public String currentUserInitials() {
        User currentUser = lookupCurrentUser();
        if (currentUser == null) {
            return "";
        }

        String source = currentUser.getUsername();
        if (source == null || source.isBlank()) {
            source = currentUser.getFullName();
        }
        if (source == null || source.isBlank()) {
            return "U";
        }

        String trimmed = source.trim();
        return trimmed.substring(0, Math.min(2, trimmed.length())).toUpperCase(Locale.ROOT);
    }

    private User lookupCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username).orElse(null);
    }
}