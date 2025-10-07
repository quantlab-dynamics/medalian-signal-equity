package com.quantlab.signal.dto.redisDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StrategyCheckEvent implements Serializable {
    private Long strategyId;
    private Instant timestamp;

    public boolean isValid() {
        return strategyId != null && timestamp != null;
    }
}
