package com.vasmarketing.offer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
class RuleEmbeddable {

  @Column(name = "rule_type")
  String ruleType;

  @JdbcTypeCode(SqlTypes.JSON)
  String params;

  protected RuleEmbeddable() {}

  RuleEmbeddable(String ruleType, String params) {
    this.ruleType = ruleType;
    this.params = params;
  }
}
