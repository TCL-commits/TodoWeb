package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping
public class ProfileController {

    private static final Path AVATAR_DIR = Paths.get("uploads", "avatars");

    private final UserRepository userRepository;

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElseThrow();
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        model.addAttribute("user", currentUser());
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String fullName,
            @RequestParam String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) MultipartFile avatarFile,
            RedirectAttributes redirectAttributes) throws IOException {

        User user = currentUser();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank()) {
            redirectAttributes.addFlashAttribute("profileError", "Email is required");
            return "redirect:/profile";
        }

        if (!normalizedEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(normalizedEmail)) {
            redirectAttributes.addFlashAttribute("profileError", "Email đã tồn tại");
            return "redirect:/profile";
        }

        user.setFullName(fullName == null ? null : fullName.trim());
        user.setEmail(normalizedEmail);
        user.setPhone(phone == null || phone.isBlank() ? null : phone.trim());

        if (avatarFile != null && !avatarFile.isEmpty()) {
            String contentType = avatarFile.getContentType() == null ? "" : avatarFile.getContentType();
            if (!contentType.startsWith("image/")) {
                redirectAttributes.addFlashAttribute("profileError", "Avatar phải là file hình ảnh");
                return "redirect:/profile";
            }
            if (avatarFile.getSize() > 5 * 1024 * 1024) {
                redirectAttributes.addFlashAttribute("profileError", "Avatar tối đa 5MB");
                return "redirect:/profile";
            }

            Files.createDirectories(AVATAR_DIR);
            String extension = StringUtils.getFilenameExtension(
                    avatarFile.getOriginalFilename() == null ? "avatar.png" : avatarFile.getOriginalFilename());
            String safeExtension = extension == null || extension.isBlank() ? "png"
                    : extension.toLowerCase(Locale.ROOT);
            String storedFilename = UUID.randomUUID() + "." + safeExtension;
            Path targetFile = AVATAR_DIR.resolve(storedFilename);
            avatarFile.transferTo(targetFile);

            user.setAvatarFilename(storedFilename);
            user.setAvatarContentType(contentType);
        }

        userRepository.save(user);
        redirectAttributes.addFlashAttribute("profileSuccess", "Đã cập nhật thông tin cá nhân");
        return "redirect:/profile";
    }

    @GetMapping("/me/avatar")
    public ResponseEntity<Resource> avatar() throws IOException {
        return avatarForUser(currentUser());
    }

    @GetMapping("/users/{userId}/avatar")
    public ResponseEntity<Resource> avatarByUserId(@PathVariable Long userId) throws IOException {
        User user = userRepository.findById(userId).orElseThrow();
        return avatarForUser(user);
    }

    private ResponseEntity<Resource> avatarForUser(User user) throws IOException {
        if (user.getAvatarFilename() != null && !user.getAvatarFilename().isBlank()) {
            Path avatarPath = AVATAR_DIR.resolve(user.getAvatarFilename());
            if (Files.exists(avatarPath)) {
                Resource resource = new org.springframework.core.io.InputStreamResource(
                        Files.newInputStream(avatarPath));
                MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
                if (user.getAvatarContentType() != null && !user.getAvatarContentType().isBlank()) {
                    try {
                        contentType = MediaType.parseMediaType(user.getAvatarContentType());
                    } catch (Exception ignored) {
                        contentType = MediaType.APPLICATION_OCTET_STREAM;
                    }
                }
                return ResponseEntity.ok()
                        .cacheControl(CacheControl.noCache())
                        .contentType(contentType)
                        .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                        .body(resource);
            }
        }

        String label = avatarLabel(user);
        String svg = """
                <svg xmlns='http://www.w3.org/2000/svg' width='128' height='128' viewBox='0 0 128 128'>
                  <defs>
                    <linearGradient id='g' x1='0%' y1='0%' x2='100%' y2='100%'>
                      <stop offset='0%' stop-color='#2f6fed'/>
                      <stop offset='100%' stop-color='#38bdf8'/>
                    </linearGradient>
                  </defs>
                  <rect width='128' height='128' rx='40' fill='url(#g)'/>
                  <circle cx='64' cy='54' r='24' fill='rgba(255,255,255,0.18)'/>
                  <text x='64' y='72' text-anchor='middle' font-family='Segoe UI, Arial, sans-serif' font-size='34' font-weight='700' fill='#ffffff'>%s</text>
                </svg>
                """
                .formatted(label);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.valueOf("image/svg+xml"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .body(new ByteArrayResource(svg.getBytes()));
    }

    private String avatarLabel(User user) {
        String source = user.getFullName();
        if (source == null || source.isBlank()) {
            source = user.getUsername();
        }
        String trimmed = source.trim();
        if (trimmed.isEmpty()) {
            return "U";
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        String first = parts[0].substring(0, 1);
        String last = parts[parts.length - 1].substring(0, 1);
        return (first + last).toUpperCase(Locale.ROOT);
    }
}
