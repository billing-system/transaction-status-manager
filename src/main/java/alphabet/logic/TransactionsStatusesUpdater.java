package alphabet.logic;

import alphabet.enums.ReportTransactionStatus;
import com.google.gson.JsonSyntaxException;
import dal.TransactionRepository;
import enums.DbTransactionStatus;
import exceptions.ProcessorException;
import external.api.TransactionDirection;
import logger.BillingSystemLogger;
import models.db.BillingTransaction;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wrappers.ProcessorWrapper;

import javax.persistence.LockModeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

@Service
@EnableScheduling
public class TransactionsStatusesUpdater {

    private final ProcessorWrapper processorWrapper;
    private final ReportToMapConvertor reportToMapConvertor;
    private final ReportToDbTransactionStatusConvertor reportToDbTransactionStatusConvertor;
    private final TransactionRepository transactionRepository;
    private final BillingSystemLogger logger;

    public TransactionsStatusesUpdater(ProcessorWrapper processorWrapper,
                                       ReportToMapConvertor reportToMapConvertor,
                                       ReportToDbTransactionStatusConvertor reportToDbTransactionStatusConvertor,
                                       TransactionRepository transactionRepository,
                                       BillingSystemLogger logger) {
        this.processorWrapper = processorWrapper;
        this.reportToMapConvertor = reportToMapConvertor;
        this.reportToDbTransactionStatusConvertor = reportToDbTransactionStatusConvertor;
        this.transactionRepository = transactionRepository;
        this.logger = logger;
    }

    @Scheduled(fixedRateString = "${update.status.schedule.in.ms}")
    public void updateTransactionsStatuses() {
        logger.log(Level.FINE, "Downloading daily report of transaction results");

        try {
            tryUpdatingTransactionStatusesInDb();
        } catch (ProcessorException e) {
            logger.log(Level.SEVERE, e.getMessage());
        } catch (JsonSyntaxException e) {
            logger.log(Level.SEVERE, "The following error occurred while trying to parse the report of " +
                    "transaction results: " + e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unknown exception has occurred while updating transactions " +
                    "statuses " + e.getMessage());
        }
    }

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    private void tryUpdatingTransactionStatusesInDb() throws ProcessorException, JsonSyntaxException {
        String report = processorWrapper.downloadReport();
        Map<String, ReportTransactionStatus> transactionIdToReportStatusMap = reportToMapConvertor.convert(report);
        List<String> reportTransactionsIds = new ArrayList<>(transactionIdToReportStatusMap.keySet());
        List<BillingTransaction> dbTransactions = fetchSentTransactionsThatExistInReport(reportTransactionsIds);

        dbTransactions.forEach(dbTransaction ->
                updateDbTransactionStatus(dbTransaction, transactionIdToReportStatusMap));

        saveUpdatedTransactionsInDb(dbTransactions);
    }

    private List<BillingTransaction> fetchSentTransactionsThatExistInReport(List<String> reportTransactionsIds) {
        return transactionRepository.findByTransactionStatusAndIdIn(DbTransactionStatus.SENT_TRANSACTION,
                reportTransactionsIds);
    }

    private void updateDbTransactionStatus(BillingTransaction dbTransaction,
                                           Map<String, ReportTransactionStatus> transactionIdToReportStatusMap) {
        ReportTransactionStatus reportTransactionStatus = extractTransactionNewStatus(dbTransaction,
                transactionIdToReportStatusMap);

        logger.log(Level.INFO, "The transaction result of " + dbTransaction.getTransactionId() +
                " is: " + reportTransactionStatus);

        DbTransactionStatus newDbTransactionStatus =
                computeNewDbTransactionStatus(dbTransaction, reportTransactionStatus);
        dbTransaction.setTransactionStatus(newDbTransactionStatus);
    }

    private ReportTransactionStatus extractTransactionNewStatus(BillingTransaction dbTransaction,
                                                                Map<String, ReportTransactionStatus> transactionIdToReportStatusMap) {
        String dbTransactionId = dbTransaction.getTransactionId();

        return transactionIdToReportStatusMap.get(dbTransactionId);
    }

    private DbTransactionStatus computeNewDbTransactionStatus(BillingTransaction dbTransaction,
                                                              ReportTransactionStatus reportTransactionStatus) {
        if (isCreditTransactionWithFailedStatus(dbTransaction, reportTransactionStatus)) {
            return DbTransactionStatus.WAITING_TO_BE_SENT;
        } else {
            return reportToDbTransactionStatusConvertor.convert(reportTransactionStatus);
        }
    }

    private boolean isCreditTransactionWithFailedStatus(BillingTransaction dbTransaction,
                                                        ReportTransactionStatus reportTransactionStatus) {
        TransactionDirection dbTransactionDirection = dbTransaction.getTransactionDirection();

        return dbTransactionDirection.equals(TransactionDirection.CREDIT) &&
                reportTransactionStatus.equals(ReportTransactionStatus.FAIL);
    }

    private void saveUpdatedTransactionsInDb(List<BillingTransaction> dbTransactions) {
        transactionRepository.saveAll(dbTransactions);

        if (!dbTransactions.isEmpty()) {
            logger.log(Level.FINEST, "Updated the following transactions saved in the db: " + dbTransactions);
            logger.log(Level.INFO, "Updated " + dbTransactions.size() + " transactions saved in the db " +
                    "according to the report of transaction results");
        } else {
            logger.log(Level.FINE, "Didn't update any transaction saved in the db");
        }
    }
}
