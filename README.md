(1) Periodically runs to fetch the report from the processor using the download_report API.
(2) Parses the report to extract transaction details and their status (success/failure).
(3) Fetches transactions with a status of "SENT_TRANSACTION" from the database that exist in the report.
(4) For each fetched transaction:
	(4.1) If the report indicates that the transaction succeeded, it changes the transaction status to "SUCCESS."
	(4.2) If the report indicates that the transaction failed:
		(4.2.1) If it's a debit transaction, it changes the status to "FAILURE."
		(4.2.2) If it's a credit transaction, it changes the status back to "WAITING_TO_BE_SENT," so the "transaction-performer" service can resend it.