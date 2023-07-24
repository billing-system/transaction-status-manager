package alphabet.logic;

import alphabet.enums.ReportTransactionStatus;
import alphabet.exceptions.UnknownReportTransactionStatusException;
import enums.DbTransactionStatus;
import org.springframework.stereotype.Service;

@Service
public class ReportToDbTransactionStatusConvertor {

    public DbTransactionStatus convert(ReportTransactionStatus transactionStatus) {
        switch (transactionStatus) {
            case FAIL:
                return DbTransactionStatus.FAILURE;
            case SUCCESS:
                return DbTransactionStatus.SUCCESS;
            default:
                throw new UnknownReportTransactionStatusException("Got an unknown transaction status from " +
                        "report: " + transactionStatus.name());
        }
    }
}
