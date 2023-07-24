package alphabet.exceptions;

public class UnknownReportTransactionStatusException extends RuntimeException {

    public UnknownReportTransactionStatusException(String errorMessage) {
        super(errorMessage);
    }
}
