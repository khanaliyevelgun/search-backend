package az.pricecompare.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A single installment/credit plan offered for a product, e.g.
 * "12 months x 175.50 AZN". Stores in Azerbaijan typically advertise several
 * of these (3, 6, 12, 18, 24 months).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditOption {

    /** Number of months in the installment plan. */
    private Integer months;

    /** Amount paid each month, in AZN. */
    private BigDecimal monthlyPayment;

    /**
     * Total paid over the full term (monthlyPayment * months). May exceed the
     * cash price when there's interest; may equal it for interest-free plans.
     */
    private BigDecimal totalPayable;

    /** True when the plan is advertised as interest-free ("faizsiz"). */
    private boolean interestFree;
}
