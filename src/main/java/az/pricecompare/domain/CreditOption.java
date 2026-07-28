package az.pricecompare.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A single installment plan offered for a product, e.g. "12 months x 175.50 AZN".
 * All three stores advertise several of these (3–24 months) and they are a real
 * decision factor here — a cheaper cash price can still be the worse deal.
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
     * Total paid over the full term (monthlyPayment * months). Exceeds the cash
     * price when there's interest; equals it for interest-free plans.
     */
    private BigDecimal totalPayable;

    /**
     * How much more than the cash price the plan costs in total. Zero or negative
     * means the store is genuinely not charging for the credit.
     */
    private BigDecimal overpayment;

    /** True when the total is within rounding distance of the cash price. */
    private boolean interestFree;
}
