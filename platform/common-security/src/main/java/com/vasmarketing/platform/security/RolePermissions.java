package com.vasmarketing.platform.security;

import static com.vasmarketing.platform.security.Permissions.AI_ADMIN;
import static com.vasmarketing.platform.security.Permissions.AI_CHAT;
import static com.vasmarketing.platform.security.Permissions.AI_GENERATE;
import static com.vasmarketing.platform.security.Permissions.AI_TELEMETRY_READ;
import static com.vasmarketing.platform.security.Permissions.ANALYTICS_READ;
import static com.vasmarketing.platform.security.Permissions.AUDIT_READ;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_APPROVE;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_DRAFT;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_PUBLISH;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_READ;
import static com.vasmarketing.platform.security.Permissions.CAMPAIGN_SUBMIT;
import static com.vasmarketing.platform.security.Permissions.CUSTOMER_PROFILE_READ;
import static com.vasmarketing.platform.security.Permissions.CUSTOMER_WRITE;
import static com.vasmarketing.platform.security.Permissions.KNOWLEDGE_READ;
import static com.vasmarketing.platform.security.Permissions.KNOWLEDGE_WRITE;
import static com.vasmarketing.platform.security.Permissions.MERCHANT_WRITE;
import static com.vasmarketing.platform.security.Permissions.OFFER_READ;
import static com.vasmarketing.platform.security.Permissions.OFFER_WRITE;
import static com.vasmarketing.platform.security.Permissions.SEGMENT_READ;
import static com.vasmarketing.platform.security.Permissions.SEGMENT_WRITE;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The RBAC matrix of docs/security.md §2, in one place. Unknown role names grant nothing (least
 * privilege).
 */
public final class RolePermissions {

  private static final Map<PlatformRole, Set<String>> MATRIX = new EnumMap<>(PlatformRole.class);

  static {
    Set<String> readOnly =
        Set.of(CAMPAIGN_READ, OFFER_READ, SEGMENT_READ, ANALYTICS_READ, KNOWLEDGE_READ);
    MATRIX.put(PlatformRole.READ_ONLY, readOnly);
    MATRIX.put(PlatformRole.MARKETING_ANALYST, union(readOnly, Set.of(AI_CHAT, AI_GENERATE)));
    MATRIX.put(
        PlatformRole.DATA_ANALYST,
        union(readOnly, Set.of(SEGMENT_WRITE, CUSTOMER_PROFILE_READ, AI_CHAT, AI_GENERATE)));
    MATRIX.put(
        PlatformRole.CAMPAIGN_MANAGER,
        union(
            readOnly,
            Set.of(
                CAMPAIGN_DRAFT,
                CAMPAIGN_SUBMIT,
                CAMPAIGN_APPROVE,
                CAMPAIGN_PUBLISH,
                SEGMENT_WRITE,
                AI_CHAT,
                AI_GENERATE)));
    MATRIX.put(
        PlatformRole.AI_OPERATOR,
        Set.of(
            KNOWLEDGE_READ,
            KNOWLEDGE_WRITE,
            AI_CHAT,
            AI_GENERATE,
            AI_ADMIN,
            AI_TELEMETRY_READ,
            AUDIT_READ));
    MATRIX.put(
        PlatformRole.MARKETING_ADMIN,
        union(
            MATRIX.get(PlatformRole.CAMPAIGN_MANAGER),
            Set.of(
                OFFER_WRITE,
                CUSTOMER_PROFILE_READ,
                CUSTOMER_WRITE,
                MERCHANT_WRITE,
                KNOWLEDGE_WRITE,
                AI_TELEMETRY_READ,
                AUDIT_READ)));
  }

  private RolePermissions() {}

  public static Set<String> of(PlatformRole role) {
    return MATRIX.get(role);
  }

  /** Union of the permissions of all recognized roles. */
  public static Set<String> forRoleNames(Collection<String> roleNames) {
    Set<String> permissions = new TreeSet<>();
    roleNames.stream()
        .map(PlatformRole::fromName)
        .flatMap(java.util.Optional::stream)
        .forEach(role -> permissions.addAll(MATRIX.get(role)));
    return Set.copyOf(permissions);
  }

  private static Set<String> union(Set<String> a, Set<String> b) {
    Set<String> result = new TreeSet<>(a);
    result.addAll(b);
    return Set.copyOf(result);
  }
}
