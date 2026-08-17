package com.nexusops.servicecore.audit.application;

import com.nexusops.servicecore.identity.application.CurrentUserProvider;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserAuditActorProvider
        implements AuditActorProvider {

    private final CurrentUserProvider currentUserProvider;

    public CurrentUserAuditActorProvider(
            CurrentUserProvider currentUserProvider
    ) {
        this.currentUserProvider =
                currentUserProvider;
    }

    @Override
    public String currentActorId() {
        try {
            return currentUserProvider
                    .currentUser()
                    .subjectId();
        } catch (IllegalStateException exception) {
            return "system";
        }
    }
}
