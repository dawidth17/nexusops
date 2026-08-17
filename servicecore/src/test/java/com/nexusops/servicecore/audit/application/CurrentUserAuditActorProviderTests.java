package com.nexusops.servicecore.audit.application;

import com.nexusops.servicecore.identity.application.CurrentUser;
import com.nexusops.servicecore.identity.application.CurrentUserProvider;
import com.nexusops.servicecore.identity.domain.SecurityRole;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserAuditActorProviderTests {

    @Test
    void usesCurrentUserSubjectAsAuditActor() {
        CurrentUserProvider currentUserProvider =
                mock(CurrentUserProvider.class);

        when(currentUserProvider.currentUser())
                .thenReturn(
                        new CurrentUser(
                                "keycloak-user-123",
                                "alice",
                                Set.of(
                                        SecurityRole.TECHNICIAN
                                )
                        )
                );

        CurrentUserAuditActorProvider provider =
                new CurrentUserAuditActorProvider(
                        currentUserProvider
                );

        assertEquals(
                "keycloak-user-123",
                provider.currentActorId()
        );
    }

    @Test
    void usesSystemActorWithoutAuthenticatedUser() {
        CurrentUserProvider currentUserProvider =
                mock(CurrentUserProvider.class);

        when(currentUserProvider.currentUser())
                .thenThrow(
                        new IllegalStateException()
                );

        CurrentUserAuditActorProvider provider =
                new CurrentUserAuditActorProvider(
                        currentUserProvider
                );

        assertEquals(
                "system",
                provider.currentActorId()
        );
    }
}