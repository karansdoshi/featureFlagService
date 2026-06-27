package com.digitalocean.featureflags.storage;

import com.digitalocean.featureflags.domain.Variation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "rule")
public class RuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "flag_name", nullable = false)
    private FeatureFlagEntity flag;

    @Column(nullable = false)
    private String attribute;

    // EQUALS | IN | NOT_EQUALS stored as readable text.
    @Column(nullable = false)
    private String operator;

    // Single operand for EQUALS / NOT_EQUALS.
    @Column(name = "single_value")
    private String value;

    // Comma-separated set for IN.
    @Column(name = "value_list")
    private String values;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Variation result;

    // "order" is a reserved SQL keyword; physical column is rule_order (DESIGN.md / D-008).
    @Column(name = "rule_order", nullable = false)
    private int ruleOrder;

    protected RuleEntity() {
    }

    public RuleEntity(String attribute, String operator, String value, String values,
                      Variation result, int ruleOrder) {
        this.attribute = attribute;
        this.operator = operator;
        this.value = value;
        this.values = values;
        this.result = result;
        this.ruleOrder = ruleOrder;
    }

    public Long getId() {
        return id;
    }

    public FeatureFlagEntity getFlag() {
        return flag;
    }

    public void setFlag(FeatureFlagEntity flag) {
        this.flag = flag;
    }

    public String getAttribute() {
        return attribute;
    }

    public String getOperator() {
        return operator;
    }

    public String getValue() {
        return value;
    }

    public String getValues() {
        return values;
    }

    public Variation getResult() {
        return result;
    }

    public int getRuleOrder() {
        return ruleOrder;
    }
}
