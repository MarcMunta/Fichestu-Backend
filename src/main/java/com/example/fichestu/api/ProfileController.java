package com.example.fichestu.api;

import com.example.fichestu.api.ProfileDtos.ChangePasswordRequest;
import com.example.fichestu.api.ProfileDtos.BadgeListResponse;
import com.example.fichestu.api.ProfileDtos.GenericResponse;
import com.example.fichestu.api.ProfileDtos.ProfileResponse;
import com.example.fichestu.api.ProfileDtos.StatsResponse;
import com.example.fichestu.api.ProfileDtos.UpdateProfileRequest;
import com.example.fichestu.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse getProfile() {
        return profileService.getProfile();
    }

    @GetMapping("/badges")
    public BadgeListResponse getBadges() {
        return profileService.getBadges();
    }

    @GetMapping("/stats")
    public StatsResponse getStats() {
        return profileService.getStats();
    }

    @PutMapping
    public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(request);
    }

    @PostMapping("/avatar")
    public ProfileResponse uploadAvatar(@RequestPart("avatar") MultipartFile avatar) {
        return profileService.uploadAvatar(avatar);
    }

    @GetMapping("/avatar/{fileName}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String fileName) {
        return profileService.getAvatar(fileName);
    }

    @PostMapping("/change-password")
    public GenericResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return profileService.changePassword(request);
    }
}
