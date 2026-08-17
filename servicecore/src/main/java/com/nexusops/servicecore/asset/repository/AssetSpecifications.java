package com.nexusops.servicecore.asset.repository;

import com.nexusops.servicecore.asset.domain.Asset;
import com.nexusops.servicecore.asset.domain.AssetStatus;
import com.nexusops.servicecore.asset.domain.AssetType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class AssetSpecifications {

    private AssetSpecifications() {
    }

    public static Specification<Asset> hasType(
            AssetType type
    ) {
        if (type == null) {
            return Specification.unrestricted();
        }

        return (root, query, builder) ->
                builder.equal(
                        root.get("type"),
                        type
                );
    }

    public static Specification<Asset> hasStatus(
            AssetStatus status
    ) {
        if (status == null) {
            return Specification.unrestricted();
        }

        return (root, query, builder) ->
                builder.equal(
                        root.get("status"),
                        status
                );
    }

    public static Specification<Asset> hasAssigneeId(
            String assigneeId
    ) {
        if (assigneeId == null || assigneeId.isBlank()) {
            return Specification.unrestricted();
        }

        String normalized = assigneeId.trim();

        return (root, query, builder) ->
                builder.equal(
                        root.get("assigneeId"),
                        normalized
                );
    }

    public static Specification<Asset> hasManufacturer(
            String manufacturer
    ) {
        if (manufacturer == null || manufacturer.isBlank()) {
            return Specification.unrestricted();
        }

        String normalized =
                manufacturer
                        .trim()
                        .toLowerCase(Locale.ROOT);

        return (root, query, builder) ->
                builder.equal(
                        builder.lower(
                                root.get("manufacturer")
                        ),
                        normalized
                );
    }

    public static Specification<Asset> containsText(
            String text
    ) {
        if (text == null || text.isBlank()) {
            return Specification.unrestricted();
        }

        String normalized = escapeLike(
                text.trim().toLowerCase(Locale.ROOT)
        );

        String pattern = "%" + normalized + "%";

        return (root, query, builder) ->
                builder.or(
                        builder.like(
                                builder.lower(
                                        root.get("assetTag")
                                ),
                                pattern,
                                '\\'
                        ),
                        builder.like(
                                builder.lower(
                                        root.get("manufacturer")
                                ),
                                pattern,
                                '\\'
                        ),
                        builder.like(
                                builder.lower(
                                        root.get("model")
                                ),
                                pattern,
                                '\\'
                        ),
                        builder.like(
                                builder.lower(
                                        root.get("serialNumber")
                                ),
                                pattern,
                                '\\'
                        )
                );
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
