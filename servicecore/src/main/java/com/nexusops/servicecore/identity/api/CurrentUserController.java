package com.nexusops.servicecore.identity.api;

import com.nexusops.servicecore.identity.application.CurrentUserProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {

    private final CurrentUserProvider currentUserProvider;

    public CurrentUserController(
            CurrentUserProvider currentUserProvider
    ) {
        this.currentUserProvider =
                currentUserProvider;
    }

    @GetMapping
    public CurrentUserResponse currentUser() {
        return CurrentUserResponse.from(
                currentUserProvider.currentUser()
        );
    }
}