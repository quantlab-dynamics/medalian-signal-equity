package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "strategy_addition")
public class StrategyAdditions extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "strategy_addition_seq")
    @SequenceGenerator(name = "strategy_addition_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "strategy_id", nullable = false)
    private Strategy strategy;

    // deploy form it is used for only some strategy
    @Column(name = "delta_slippage")
    private Long deltaSlippage;

}
