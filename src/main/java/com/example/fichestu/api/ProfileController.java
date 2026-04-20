package com.example.fichestu.api;

import com.example.fichestu.api.ProfileDtos.ChangePasswordRequest;
import com.example.fichestu.api.ProfileDtos.GenericResponse;
import com.example.fichestu.api.ProfileDtos.ProfileResponse;
import com.example.fichestu.api.ProfileDtos.UpdateProfileRequest;
import com.example.fichestu.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse getProfile(@RequestHeader("Authorization") String authorization) {
        return profileService.getProfile(authorization);
    }

    @PutMapping
    public ProfileResponse updateProfile(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody UpdateProfileRequest request
    ) {
        return profileService.updateProfile(authorization, request);
    }

    @PostMapping("/change-password")
    public GenericResponse changePassword(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody ChangePasswordRequest request
    ) {
        return profileService.changePassword(authorization, request);
    }
}
