package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "signal_additions")
public class SignalAdditions extends AuditingEntity{

    // all signal metadata is stored these details are unique for the signal
    // has to save the fields when the signal is created
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @SequenceGenerator(name = "signal_additions_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "entry_underling_price")
    private Integer entryUnderlingPrice;

    @Column(name = "entry_delta")
    private Integer entryDelta;

    @Column(name = "entry_iv")
    private Integer entryIv;

    @Column(name = "current_atm")
    private Integer currentAtm;

    @Column(name = "entry_underling_spot_price")
    private Integer entryUnderlingSpotPrice;

}
