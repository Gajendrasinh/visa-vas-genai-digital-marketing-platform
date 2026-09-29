package com.vasmarketing.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class RolePermissionsTest {

  @Test
  void readOnlyCanOnlyRead() {
    assertThat(RolePermissions.of(PlatformRole.READ_ONLY)).allMatch(p -> p.endsWith(":read"));
  }

  @Test
  void onlyManagersAndAdminsCanApproveOrPublishCampaigns() {
    for (PlatformRole role : PlatformRole.values()) {
      boolean expected =
          EnumSet.of(PlatformRole.CAMPAIGN_MANAGER, PlatformRole.MARKETING_ADMIN).contains(role);
      assertThat(RolePermissions.of(role).contains(Permissions.CAMPAIGN_APPROVE))
          .as(role.name())
          .isEqualTo(expected);
      assertThat(RolePermissions.of(role).contains(Permissions.CAMPAIGN_PUBLISH))
          .as(role.name())
          .isEqualTo(expected);
    }
  }

  @Test
  void customerProfilesOnlyForDataAnalystsAndAdmins() {
    assertThat(PlatformRole.values())
        .filteredOn(r -> RolePermissions.of(r).contains(Permissions.CUSTOMER_PROFILE_READ))
        .containsExactlyInAnyOrder(PlatformRole.DATA_ANALYST, PlatformRole.MARKETING_ADMIN);
  }

  @Test
  void aiAdministrationIsSeparatedFromMarketingAdministration() {
    assertThat(RolePermissions.of(PlatformRole.AI_OPERATOR))
        .contains(Permissions.AI_ADMIN)
        .doesNotContain(Permissions.CAMPAIGN_DRAFT);
    assertThat(RolePermissions.of(PlatformRole.MARKETING_ADMIN))
        .doesNotContain(Permissions.AI_ADMIN);
  }

  @Test
  void unknownRolesGrantNothingAndRolesCombine() {
    assertThat(RolePermissions.forRoleNames(List.of("SUPER_USER", "offline_access"))).isEmpty();
    assertThat(RolePermissions.forRoleNames(List.of("READ_ONLY", "AI_OPERATOR")))
        .contains(Permissions.CAMPAIGN_READ, Permissions.AI_ADMIN);
  }
}
