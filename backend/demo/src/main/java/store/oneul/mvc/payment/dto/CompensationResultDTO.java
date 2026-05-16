package store.oneul.mvc.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CompensationResultDTO {

    private final boolean refunded;
    private final boolean pending;
    private final int refundAmount;
    private final String refundedAt;

    public static CompensationResultDTO refunded(int refundAmount, String refundedAt) {
        return CompensationResultDTO.builder()
                .refunded(true)
                .pending(false)
                .refundAmount(refundAmount)
                .refundedAt(refundedAt)
                .build();
    }

    public static CompensationResultDTO pending() {
        return CompensationResultDTO.builder()
                .refunded(false)
                .pending(true)
                .refundAmount(0)
                .refundedAt(null)
                .build();
    }
}